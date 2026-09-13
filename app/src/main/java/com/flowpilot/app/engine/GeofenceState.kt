package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import kotlinx.serialization.Serializable

@Serializable
enum class GeofenceEvent {
    ENTER,
    EXIT,
}

@Serializable
data class GeofenceTransition(
    val automationId: String,
    val event: GeofenceEvent,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val distanceMeters: Float = 0f,
    val targetLat: Double = 0.0,
    val targetLng: Double = 0.0,
    val radiusMeters: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

val GeofenceTransition.coordinates: Pair<Double, Double>?
    get() = when {
        currentLat.isFinite() && currentLng.isFinite() && (currentLat != 0.0 || currentLng != 0.0) ->
            Pair(currentLat, currentLng)
        targetLat.isFinite() && targetLng.isFinite() && (targetLat != 0.0 || targetLng != 0.0) ->
            Pair(targetLat, targetLng)
        else -> null
    }

@Serializable
enum class GeofenceDiagnosticStatus {
    REGISTERED,
    UNREGISTERED,
    TRANSITION_ENTER,
    TRANSITION_EXIT,
    REGISTRATION_FAILED,
    RECEIVER_ERROR,
}

@Serializable
data class GeofenceDiagnostic(
    val automationId: String,
    val status: GeofenceDiagnosticStatus,
    val lastRegistrationAt: Long? = null,
    val lastTransitionAt: Long? = null,
    val lastTransition: GeofenceEvent? = null,
    val error: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Validates whether user-specified latitude, longitude, and radius meet geographic
 * constraints and the FlowPilot UI radius boundaries (50m to 1000m).
 */
fun isValidGeofenceConfig(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (latitude < -90.0 || latitude > 90.0) return false
    if (longitude < -180.0 || longitude > 180.0) return false
    if (latitude == 0.0 && longitude == 0.0) return false
    if (radiusMeters !in 50..1000) return false
    return true
}

data class GeofenceRegistrationDiff(
    val toAdd: List<Automation>,
    val toRemoveIds: List<String>,
)

/**
 * Calculates differences between currently registered rules and target active rules.
 * Handles disabled/deleted rules cleanup, parameter updates, and deduplication of request IDs.
 */
fun calculateGeofenceDiff(
    currentRegistered: Map<String, Automation>,
    desiredRules: List<Automation>,
): GeofenceRegistrationDiff {
    val activeDesired = desiredRules
        .filter { rule ->
            rule.enabled &&
                (rule.triggerEvent == TriggerEvent.GEOFENCE_ENTER || rule.triggerEvent == TriggerEvent.GEOFENCE_EXIT) &&
                isValidGeofenceConfig(rule.geofenceLatitude, rule.geofenceLongitude, rule.geofenceRadiusMeters)
        }
        .distinctBy { it.id }
        .associateBy { it.id }

    val toRemoveIds = mutableListOf<String>()
    val toAdd = mutableListOf<Automation>()

    // Identify removed or modified rules
    for ((id, current) in currentRegistered) {
        val desired = activeDesired[id]
        if (desired == null) {
            toRemoveIds.add(id)
        } else if (
            desired.geofenceLatitude != current.geofenceLatitude ||
            desired.geofenceLongitude != current.geofenceLongitude ||
            desired.geofenceRadiusMeters != current.geofenceRadiusMeters ||
            desired.triggerEvent != current.triggerEvent
        ) {
            toRemoveIds.add(id)
            toAdd.add(desired)
        }
    }

    // Identify newly added rules
    for ((id, desired) in activeDesired) {
        if (!currentRegistered.containsKey(id)) {
            toAdd.add(desired)
        }
    }

    return GeofenceRegistrationDiff(toAdd = toAdd, toRemoveIds = toRemoveIds)
}

sealed class GeofencePrerequisitesResult {
    data class Ready(val isFine: Boolean) : GeofencePrerequisitesResult()
    data class MissingLocationPermission(val message: String) : GeofencePrerequisitesResult()
    data class MissingBackgroundLocation(val message: String) : GeofencePrerequisitesResult()
    data class LocationServicesDisabled(val message: String) : GeofencePrerequisitesResult()
}

/**
 * Pure validation for location prerequisites before attempting system geofence registration.
 */
fun evaluateGeofencePrerequisites(
    hasFineLocation: Boolean,
    hasCoarseLocation: Boolean,
    hasBackgroundLocation: Boolean,
    isLocationEnabled: Boolean,
): GeofencePrerequisitesResult {
    if (!hasFineLocation) {
        return GeofencePrerequisitesResult.MissingLocationPermission(
            "Precise location permission is required for geofencing."
        )
    }
    if (!hasBackgroundLocation) {
        return GeofencePrerequisitesResult.MissingBackgroundLocation(
            "Background location permission ('Allow all the time') is required for background geofencing."
        )
    }
    if (!isLocationEnabled) {
        return GeofencePrerequisitesResult.LocationServicesDisabled(
            "System location services are turned off."
        )
    }
    return GeofencePrerequisitesResult.Ready(isFine = hasFineLocation)
}

data class GeofenceConfigSignature(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val triggerEvent: TriggerEvent,
)

fun Automation.toGeofenceConfigSignature() = GeofenceConfigSignature(
    id = id,
    latitude = geofenceLatitude,
    longitude = geofenceLongitude,
    radiusMeters = geofenceRadiusMeters,
    triggerEvent = triggerEvent,
)

/**
 * Tracks geofence registration failure retry policy and backoff.
 * Prevents repeated DataStore writes, log spam, and Google Play Services calls on periodic engine polling ticks.
 * Immediately allows retry when:
 * - Desired geofence configuration set changes (rule added, removed, or coordinates/radius/trigger changed)
 * - Prerequisites state changes (permission granted, location services turned on)
 * Applies bounded exponential backoff (base 10s up to max 60s) for identical config and error state.
 */
class GeofenceRetryPolicy(
    val baseBackoffMs: Long = 10_000L,
    val maxBackoffMs: Long = 60_000L,
) {
    data class FailureRecord(
        val signature: GeofenceConfigSignature,
        val error: String,
        val failureCount: Int,
        val nextRetryAt: Long,
    )

    private var lastDesiredSignatures: Set<GeofenceConfigSignature> = emptySet()
    private var lastPrereqKey: String? = null
    private val failureRecords = mutableMapOf<String, FailureRecord>()

    /**
     * Updates desired configuration signatures and prerequisites state for the active ruleset.
     * Returns true if desired geofence set changed or prerequisites changed.
     */
    @Synchronized
    fun updateDesiredState(
        desiredRules: List<Automation>,
        prereqResult: GeofencePrerequisitesResult,
    ): Boolean {
        val currentSignatures = desiredRules
            .filter { it.enabled && (it.triggerEvent == TriggerEvent.GEOFENCE_ENTER || it.triggerEvent == TriggerEvent.GEOFENCE_EXIT) && isValidGeofenceConfig(it.geofenceLatitude, it.geofenceLongitude, it.geofenceRadiusMeters) }
            .map { it.toGeofenceConfigSignature() }
            .toSet()
        val currentPrereqKey = prereqResult.toKey()

        val desiredChanged = lastDesiredSignatures.isNotEmpty() && currentSignatures != lastDesiredSignatures
        val prereqsChanged = lastPrereqKey != null && currentPrereqKey != lastPrereqKey

        lastDesiredSignatures = currentSignatures
        lastPrereqKey = currentPrereqKey

        return desiredChanged || prereqsChanged
    }

    @Synchronized
    fun shouldAttemptRegistration(
        rulesToAdd: List<Automation>,
        prereqResult: GeofencePrerequisitesResult,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (rulesToAdd.isEmpty()) return false

        val currentPrereqKey = prereqResult.toKey()

        // 1. If prerequisites state changed, immediately reconcile
        if (lastPrereqKey != null && currentPrereqKey != lastPrereqKey) {
            lastPrereqKey = currentPrereqKey
            return true
        }
        if (lastPrereqKey == null) {
            lastPrereqKey = currentPrereqKey
        }

        // 2. Check individual rules: if any rule has changed configuration, is new, or its backoff elapsed, attempt
        return rulesToAdd.any { rule ->
            val record = failureRecords[rule.id]
            record == null || record.signature != rule.toGeofenceConfigSignature() || now >= record.nextRetryAt
        }
    }

    @Synchronized
    fun recordFailure(
        automationIds: List<String>,
        rules: List<Automation>,
        error: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val rulesById = rules.associateBy { it.id }
        for (id in automationIds) {
            val rule = rulesById[id] ?: continue
            val prev = failureRecords[id]
            val count = if (prev != null && prev.signature == rule.toGeofenceConfigSignature() && prev.error == error) {
                prev.failureCount + 1
            } else {
                1
            }
            val backoff = calculateBackoff(count)
            failureRecords[id] = FailureRecord(
                signature = rule.toGeofenceConfigSignature(),
                error = error,
                failureCount = count,
                nextRetryAt = now + backoff,
            )
        }
    }

    @Synchronized
    fun recordSuccess(automationIds: List<String>) {
        for (id in automationIds) {
            failureRecords.remove(id)
        }
    }

    @Synchronized
    fun clearRules(automationIds: List<String>) {
        for (id in automationIds) {
            failureRecords.remove(id)
        }
    }

    @Synchronized
    fun getFailureRecord(ruleId: String): FailureRecord? = failureRecords[ruleId]

    @Synchronized
    fun reset() {
        lastDesiredSignatures = emptySet()
        lastPrereqKey = null
        failureRecords.clear()
    }

    fun calculateBackoff(failureCount: Int): Long {
        if (failureCount <= 1) return baseBackoffMs
        val shift = (failureCount - 1).coerceAtMost(6)
        val multiplier = 1L shl shift
        return (baseBackoffMs * multiplier).coerceAtMost(maxBackoffMs)
    }

    private fun GeofencePrerequisitesResult.toKey(): String = when (this) {
        is GeofencePrerequisitesResult.Ready -> "READY"
        is GeofencePrerequisitesResult.MissingLocationPermission -> "MISSING_PERMISSION:${this.message}"
        is GeofencePrerequisitesResult.MissingBackgroundLocation -> "MISSING_BG_PERMISSION:${this.message}"
        is GeofencePrerequisitesResult.LocationServicesDisabled -> "LOCATION_DISABLED:${this.message}"
    }
}
