package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.geofenceRegistrationViews
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by Google Play Services when a registered geofence boundary is crossed.
 * Extracts transition events and persists them to AutomationRepository's persistent event queue.
 *
 * Execution & Lifecycle:
 * Because this receiver is invoked via an explicit PendingIntent from Google Play Services Geofencing,
 * Android (API 31+, including Android 14+ / target 36) grants an exemption from background foreground
 * service launch restrictions. If the engine is enabled, this receiver triggers AutomationService
 * reconciliation to ensure pending events are promptly evaluated without waiting for user app launch.
 * If service startup fails or is constrained, the transition events remain safely in the persistent queue.
 *
 * Security: Registered with android:exported="false" in AndroidManifest.xml and invoked
 * strictly via the app's explicit PendingIntent.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val geofencingEvent = try {
            GeofencingEvent.fromIntent(intent)
        } catch (_: Exception) {
            val errorCode = GeofenceDiagnosticErrorCode.EVENT_PARSE_FAILED
            Log.w(TAG, "Failed to parse GeofencingEvent from intent (${errorCode.name})")
            recordReceiverError(context, errorCode)
            return
        } ?: return

        if (geofencingEvent.hasError()) {
            val errorCode = GeofenceDiagnosticErrorCode.fromStatusCode(geofencingEvent.errorCode)
                ?: GeofenceDiagnosticErrorCode.RECEIVER_ERROR
            Log.w(TAG, "Geofencing error (${geofencingEvent.errorCode}): ${errorCode.name}")
            recordReceiverError(context, errorCode)
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val event = when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceEvent.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceEvent.EXIT
            else -> {
                Log.d(TAG, "Ignoring non-enter/exit geofence transition: $geofenceTransition")
                recordReceiverError(context, GeofenceDiagnosticErrorCode.UNSUPPORTED_TRANSITION)
                return
            }
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences.isNullOrEmpty()) {
            Log.w(TAG, "No triggering geofences reported")
            recordReceiverError(context, GeofenceDiagnosticErrorCode.EMPTY_TRIGGERING_GEOFENCES)
            return
        }

        val location = geofencingEvent.triggeringLocation
        val currentLat = location?.latitude ?: 0.0
        val currentLng = location?.longitude ?: 0.0
        val now = System.currentTimeMillis()

        val transitions = triggeringGeofences.map { geofence ->
            GeofenceTransition(
                automationId = geofence.requestId,
                event = event,
                currentLat = currentLat,
                currentLng = currentLng,
                distanceMeters = 0f,
                targetLat = 0.0,
                targetLng = 0.0,
                radiusMeters = 0,
                timestamp = now,
                coordinatesTimestamp = location?.time,
            )
        }

        Log.i(TAG, "Geofence transition $event detected for ${transitions.size} rule(s)")

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val repository = AutomationRepository(appContext)
            try {
                val rules = repository.automations.first()
                val registrations = rules.geofenceRegistrationViews().associateBy { it.id }
                val resolved = transitions.mapNotNull { transition ->
                    val registration = registrations[transition.automationId] ?: return@mapNotNull null
                    if (!registration.enabled) return@mapNotNull null
                    val parent = rules.firstOrNull { rule ->
                        listOf(rule).geofenceRegistrationViews().any { it.id == registration.id }
                    } ?: return@mapNotNull null
                    transition.copy(automationId = parent.id, triggerIndex = registration.matchedTriggerIndex,
                        executionRevision = parent.executionRevision)
                }
                repository.enqueueGeofenceEvents(resolved)
                repository.recordGeofenceTransitions(resolved)
                Log.d(TAG, "Persisted ${transitions.size} geofence transition(s) to queue")

                val isEngineEnabled = repository.isEngineEnabled.first()
                if (isEngineEnabled) {
                    val serviceStarted = try {
                        AutomationService.reconcileEnabled(appContext)
                    } catch (_: Exception) {
                        Log.e(TAG, "Exception while reconciling AutomationService for geofence")
                        false
                    }
                    if (!serviceStarted) {
                        Log.w(TAG, "AutomationService could not be started or reconciled; ${transitions.size} geofence transition(s) remain safely persisted in queue")
                    } else {
                        Log.i(TAG, "Triggered AutomationService reconciliation for geofence transition(s)")
                    }
                } else {
                    Log.i(TAG, "FlowPilot engine is disabled; geofence events enqueued but service reconciliation skipped")
                }
            } catch (_: Throwable) {
                val errorCode = GeofenceDiagnosticErrorCode.TRANSITION_HANDLING_FAILED
                repository.recordGeofenceReceiverError(errorCode)
                Log.e(TAG, "Failed to handle geofence transitions (${errorCode.name})")
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val ACTION_GEOFENCE_TRANSITION = "com.flowpilot.app.ACTION_GEOFENCE_TRANSITION"
    }

    private fun recordReceiverError(context: Context, errorCode: GeofenceDiagnosticErrorCode) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AutomationRepository(context.applicationContext).recordGeofenceReceiverError(errorCode)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
