package com.flowpilot.app.engine

import android.location.Location
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation

private val LOCATION_TOKENS = setOf(
    "locationLat", "location.lat", "locationLng", "location.lng",
    "location.maps_url", "maps_url", "location.coords", "coords",
)

fun Automation.requiresLocation(): Boolean = effectiveActions.any { requiresLocation(it) }

fun Automation.requiresLocation(action: ActionType): Boolean = when (action) {
    ActionType.HTTP_WEBHOOK -> webhookHeaders.containsLocationToken() || webhookBody.containsLocationToken()
    ActionType.SEND_SMS, ActionType.DRAFT_SMS -> smsRecipient.containsLocationToken() || smsMessage.containsLocationToken()
    else -> false
}

private fun String.containsLocationToken(): Boolean =
    Regex("""\$\{([a-zA-Z0-9_.]+)\}""").findAll(this).any { it.groupValues[1] in LOCATION_TOKENS }

fun isValidCachedLocation(location: Location, nowMs: Long): Boolean {
    val ageMs = nowMs - location.time
    return ageMs in 0 until 60_000L && location.hasAccuracy() && location.accuracy <= 50f
}

internal const val MAX_COORDINATE_AGE_MS = 60_000L

suspend fun resolveExecutionCoordinates(
    requiresLocation: Boolean,
    event: GeofenceTransition?,
    freshLocationProvider: suspend () -> Pair<Double, Double>?,
    eventLocationAllowed: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
): Pair<Double, Double>? {
    if (!requiresLocation) return null
    val timestamp = event?.coordinatesTimestamp
    if (eventLocationAllowed && timestamp != null && timestamp > 0 && timestamp <= nowMs && nowMs - timestamp < MAX_COORDINATE_AGE_MS) {
        event.coordinates?.let { return it }
    }
    return freshLocationProvider()
}
