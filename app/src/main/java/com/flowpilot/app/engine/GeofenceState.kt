package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.geofenceRegistrationViews
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
    val coordinatesTimestamp: Long? = null,
    /** Null for legacy queued events; otherwise the matched OR definition index. */
    val triggerIndex: Int? = null,
    /** Saved rule revision that authorized this source; null only for legacy queue entries. */
    val executionRevision: Long? = null,
)

val GeofenceTransition.coordinates: Pair<Double, Double>?
    get() = when {
        currentLat.isFinite() && currentLat in -90.0..90.0 && currentLng.isFinite() && currentLng in -180.0..180.0 && (currentLat != 0.0 || currentLng != 0.0) ->
            Pair(currentLat, currentLng)
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
enum class GeofenceDiagnosticErrorCode(val message: String) {
    MISSING_LOCATION_PERMISSION("Precise location permission is required for geofencing."),
    MISSING_BACKGROUND_LOCATION("Background location permission ('Allow all the time') is required for background geofencing."),
    LOCATION_SERVICES_DISABLED("System location services are turned off."),
    GEOFENCE_NOT_AVAILABLE("Geofencing error (1000): GEOFENCE_NOT_AVAILABLE"),
    GEOFENCE_TOO_MANY_GEOFENCES("Geofencing error (1001): GEOFENCE_TOO_MANY_GEOFENCES"),
    GEOFENCE_TOO_MANY_PENDING_INTENTS("Geofencing error (1002): GEOFENCE_TOO_MANY_PENDING_INTENTS"),
    GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION("Geofencing error (1004): GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION"),
    GEOFENCE_REQUEST_TOO_FREQUENT("Geofencing error (1005): GEOFENCE_REQUEST_TOO_FREQUENT"),
    REGISTRATION_SECURITY_ERROR("Location permission denied while registering geofences."),
    REGISTRATION_FAILED("Geofence registration failed."),
    UNREGISTER_SECURITY_ERROR("Location permission denied while removing geofences."),
    UNREGISTER_FAILED("Failed to unregister geofences."),
    EVENT_PARSE_FAILED("Could not parse geofence event."),
    UNSUPPORTED_TRANSITION("Unsupported geofence transition."),
    EMPTY_TRIGGERING_GEOFENCES("No triggering geofences reported"),
    TRANSITION_HANDLING_FAILED("Failed to handle geofence transitions."),
    RECEIVER_ERROR("Geofence receiver error.");

    companion object {
        fun fromStatusCode(statusCode: Int): GeofenceDiagnosticErrorCode? = when (statusCode) {
            1000 -> GEOFENCE_NOT_AVAILABLE
            1001 -> GEOFENCE_TOO_MANY_GEOFENCES
            1002 -> GEOFENCE_TOO_MANY_PENDING_INTENTS
            1004 -> GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION
            1005 -> GEOFENCE_REQUEST_TOO_FREQUENT
            else -> null
        }

        fun fromThrowable(
            throwable: Throwable?,
            status: GeofenceDiagnosticStatus = GeofenceDiagnosticStatus.REGISTRATION_FAILED,
            unregisterOperation: Boolean = false,
        ): GeofenceDiagnosticErrorCode {
            if (throwable is SecurityException) {
                return if (unregisterOperation) UNREGISTER_SECURITY_ERROR else REGISTRATION_SECURITY_ERROR
            }
            val statusCode = extractStatusCode(throwable)
            if (statusCode != null) {
                fromStatusCode(statusCode)?.let { return it }
                if (statusCode == 13 || statusCode == 17) {
                    return if (unregisterOperation) UNREGISTER_FAILED else REGISTRATION_FAILED
                }
            }
            return when {
                status == GeofenceDiagnosticStatus.RECEIVER_ERROR -> TRANSITION_HANDLING_FAILED
                unregisterOperation -> UNREGISTER_FAILED
                else -> REGISTRATION_FAILED
            }
        }

        private fun extractStatusCode(throwable: Throwable?): Int? {
            if (throwable == null) return null
            try {
                val method = throwable.javaClass.getMethod("getStatusCode")
                val code = method.invoke(throwable) as? Int
                if (code != null) return code
            } catch (_: Throwable) {}
            return null
        }

        fun resolveOrSanitize(
            rawError: String,
            status: GeofenceDiagnosticStatus,
            errorCode: GeofenceDiagnosticErrorCode? = null,
        ): Pair<GeofenceDiagnosticErrorCode?, String> {
            when (status) {
                GeofenceDiagnosticStatus.REGISTERED,
                GeofenceDiagnosticStatus.UNREGISTERED,
                GeofenceDiagnosticStatus.TRANSITION_ENTER,
                GeofenceDiagnosticStatus.TRANSITION_EXIT -> return null to ""
                GeofenceDiagnosticStatus.REGISTRATION_FAILED,
                GeofenceDiagnosticStatus.RECEIVER_ERROR -> {}
            }
            if (errorCode != null) {
                return errorCode to errorCode.message
            }
            val trimmed = rawError.trim()
            entries.firstOrNull { it.message.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
                ?.let { return it to it.message }

            val lower = trimmed.lowercase()
            val matched = when {
                trimmed.equals("Location disabled", ignoreCase = true) ||
                    trimmed.equals("Location services disabled", ignoreCase = true) ||
                    trimmed.equals("System location services are turned off", ignoreCase = true) ->
                    LOCATION_SERVICES_DISABLED to "Location disabled"
                trimmed.equals("Missing background location", ignoreCase = true) ||
                    trimmed.equals("Background location permission required", ignoreCase = true) ->
                    MISSING_BACKGROUND_LOCATION to MISSING_BACKGROUND_LOCATION.message
                trimmed.equals("Missing location permission", ignoreCase = true) ||
                    trimmed.equals("Location permission required", ignoreCase = true) ||
                    trimmed.equals("Precise location permission required", ignoreCase = true) ->
                    MISSING_LOCATION_PERMISSION to MISSING_LOCATION_PERMISSION.message
                lower.startsWith("geofencing error (1000)") || lower == "1000: geofence_not_available" || lower == "geofence_not_available" ->
                    GEOFENCE_NOT_AVAILABLE to GEOFENCE_NOT_AVAILABLE.message
                lower.startsWith("geofencing error (1001)") || lower == "1001: geofence_too_many_geofences" || lower == "geofence_too_many_geofences" ->
                    GEOFENCE_TOO_MANY_GEOFENCES to GEOFENCE_TOO_MANY_GEOFENCES.message
                lower.startsWith("geofencing error (1002)") || lower == "1002: geofence_too_many_pending_intents" || lower == "geofence_too_many_pending_intents" ->
                    GEOFENCE_TOO_MANY_PENDING_INTENTS to GEOFENCE_TOO_MANY_PENDING_INTENTS.message
                lower.startsWith("geofencing error (1004)") || lower == "1004: geofence_insufficient_location_permission" || lower == "geofence_insufficient_location_permission" ->
                    GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION to GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION.message
                lower.startsWith("geofencing error (1005)") || lower == "1005: geofence_request_too_frequent" || lower == "geofence_request_too_frequent" ->
                    GEOFENCE_REQUEST_TOO_FREQUENT to GEOFENCE_REQUEST_TOO_FREQUENT.message
                lower.startsWith("unregister security error") ->
                    UNREGISTER_SECURITY_ERROR to UNREGISTER_SECURITY_ERROR.message
                lower.startsWith("unregister failed") || lower.startsWith("unregister error") ->
                    UNREGISTER_FAILED to UNREGISTER_FAILED.message
                lower.startsWith("security error") ->
                    REGISTRATION_SECURITY_ERROR to REGISTRATION_SECURITY_ERROR.message
                lower.startsWith("could not parse geofence event") ->
                    EVENT_PARSE_FAILED to EVENT_PARSE_FAILED.message
                lower.startsWith("unsupported geofence transition") ->
                    UNSUPPORTED_TRANSITION to UNSUPPORTED_TRANSITION.message
                lower.startsWith("no triggering geofences reported") ->
                    EMPTY_TRIGGERING_GEOFENCES to EMPTY_TRIGGERING_GEOFENCES.message
                lower.startsWith("failed to handle geofence transitions") ->
                    TRANSITION_HANDLING_FAILED to TRANSITION_HANDLING_FAILED.message
                else -> {
                    val fallback = if (status == GeofenceDiagnosticStatus.RECEIVER_ERROR) RECEIVER_ERROR else REGISTRATION_FAILED
                    fallback to fallback.message
                }
            }
            return matched
        }
    }
}

@Serializable
data class GeofenceDiagnostic(
    val automationId: String,
    val status: GeofenceDiagnosticStatus,
    val lastRegistrationAt: Long? = null,
    val lastTransitionAt: Long? = null,
    val lastTransition: GeofenceEvent? = null,
    val error: String = "",
    val errorCode: GeofenceDiagnosticErrorCode? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun sanitized(): GeofenceDiagnostic {
        val (resolvedCode, safeError) = GeofenceDiagnosticErrorCode.resolveOrSanitize(
            rawError = error,
            status = status,
            errorCode = errorCode,
        )
        return if (error == safeError && errorCode == resolvedCode) {
            this
        } else {
            copy(error = safeError, errorCode = resolvedCode)
        }
    }
}

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
    val activeDesired = desiredRules.geofenceRegistrationViews()
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
        val currentSignatures = desiredRules.geofenceRegistrationViews()
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
        val (_, safeError) = GeofenceDiagnosticErrorCode.resolveOrSanitize(
            rawError = error,
            status = GeofenceDiagnosticStatus.REGISTRATION_FAILED,
        )
        for (id in automationIds) {
            val rule = rulesById[id] ?: continue
            val prev = failureRecords[id]
            val count = if (prev != null && prev.signature == rule.toGeofenceConfigSignature() && prev.error == safeError) {
                prev.failureCount + 1
            } else {
                1
            }
            val backoff = calculateBackoff(count)
            failureRecords[id] = FailureRecord(
                signature = rule.toGeofenceConfigSignature(),
                error = safeError,
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
