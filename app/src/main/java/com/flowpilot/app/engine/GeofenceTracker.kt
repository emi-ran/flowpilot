package com.flowpilot.app.engine

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.Automation
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wraps Google Play Services GeofencingClient to register, synchronize, and tear down system geofences.
 *
 * Uses hardware-backed system geofencing with PendingIntent delivery for zero-battery idle drain.
 * Tracks registrations only for the current process. System geofences can disappear after reboot or
 * app update, so each new engine process registers the current desired set again.
 */
class GeofenceTracker(
    private val context: Context,
    private val repository: AutomationRepository = AutomationRepository(context),
    private val clientProvider: () -> GeofencingClient = { LocationServices.getGeofencingClient(context) },
    val retryPolicy: GeofenceRetryPolicy = GeofenceRetryPolicy(),
) {
    companion object {
        private const val TAG = "GeofenceTracker"
        const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 4001
        private const val MAX_EVENT_AGE_MS = 2 * 60 * 60 * 1000L // 2 hours
    }

    private val geofencingClient: GeofencingClient by lazy { clientProvider() }
    // Google Play Services registrations can disappear after reboot, app update, or data restore.
    // Process-local state forces each new engine process to reconcile desired fences with the OS.
    private val registeredRules = ConcurrentHashMap<String, Automation>()

    private val isUpdating = AtomicBoolean(false)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_TRANSITION
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, GEOFENCE_PENDING_INTENT_REQUEST_CODE, intent, flags)
    }

    /**
     * Reconciles registered system geofences against desired rules list.
     * Prevents race condition between asynchronous removal and re-addition of modified rules.
     * When a rule is modified, its ID is removed first; only after removal succeeds is the new fence registered.
     * Purely new IDs are registered independently.
     * If an update is already in progress, subsequent poll cycles skip to prevent state drift.
     */
    @Synchronized
    fun updateListeningPolicy(rules: List<Automation>) {
        if (isUpdating.get()) {
            return
        }

        val diff = calculateGeofenceDiff(registeredRules, rules)
        if (diff.toRemoveIds.isEmpty() && diff.toAdd.isEmpty()) {
            return
        }

        val prereqs = evaluatePrerequisites()
        val desiredOrPrereqsChanged = retryPolicy.updateDesiredState(rules, prereqs)

        val toRemoveIds = diff.toRemoveIds
        val (toReAdd, purelyNew) = diff.toAdd.partition { it.id in toRemoveIds }

        // Clean up retry records for purely removed rules (deleted or disabled, not in toReAdd)
        val purelyRemovedIds = toRemoveIds.filter { id -> toReAdd.none { it.id == id } }
        if (purelyRemovedIds.isNotEmpty()) {
            retryPolicy.clearRules(purelyRemovedIds)
        }

        val shouldAttemptReAdd = toReAdd.isNotEmpty() && (desiredOrPrereqsChanged || retryPolicy.shouldAttemptRegistration(toReAdd, prereqs))
        val shouldAttemptNew = purelyNew.isNotEmpty() && (desiredOrPrereqsChanged || retryPolicy.shouldAttemptRegistration(purelyNew, prereqs))

        val hasPurelyRemoved = purelyRemovedIds.isNotEmpty()
        val willRemove = hasPurelyRemoved || (toRemoveIds.isNotEmpty() && shouldAttemptReAdd)
        val willAttemptDirectNew = shouldAttemptNew

        val totalOps = (if (willRemove) 1 else 0) + (if (willAttemptDirectNew) 1 else 0)
        if (totalOps == 0) {
            return
        }

        if (!isUpdating.compareAndSet(false, true)) {
            return
        }

        val activeOps = AtomicInteger(totalOps)
        val onOpFinished = {
            if (activeOps.decrementAndGet() <= 0) {
                isUpdating.set(false)
            }
        }

        // 1. Process removals first (modified rules + deleted/disabled rules)
        if (willRemove) {
            try {
                geofencingClient.removeGeofences(toRemoveIds)
                    .addOnSuccessListener {
                        Log.i(TAG, "Unregistered ${toRemoveIds.size} geofence(s)")
                        toRemoveIds.forEach { registeredRules.remove(it) }
                        recordUnregistration(toRemoveIds)

                        // Only re-add modified rules if remove succeeded AND retry policy allows
                        if (toReAdd.isNotEmpty() && shouldAttemptReAdd) {
                            checkPrerequisitesAndRegister(toReAdd, onComplete = onOpFinished)
                        } else {
                            onOpFinished()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to unregister geofences: ${e.message}")
                        val errorMsg = "Unregister failed: ${e.message.orEmpty()}"
                        if (toReAdd.isNotEmpty()) {
                            retryPolicy.recordFailure(toReAdd.map { it.id }, toReAdd, errorMsg)
                        }
                        recordRegistrationFailure(toRemoveIds, errorMsg)
                        // Remove failed: do NOT add toReAdd rules to avoid ID collision and state drift!
                        onOpFinished()
                    }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException while removing geofences: ${e.message}")
                val errorMsg = "Unregister security error: ${e.message.orEmpty()}"
                if (toReAdd.isNotEmpty()) {
                    retryPolicy.recordFailure(toReAdd.map { it.id }, toReAdd, errorMsg)
                }
                recordRegistrationFailure(toRemoveIds, errorMsg)
                onOpFinished()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error removing geofences: ${e.message}")
                val errorMsg = "Unregister error: ${e.message.orEmpty()}"
                if (toReAdd.isNotEmpty()) {
                    retryPolicy.recordFailure(toReAdd.map { it.id }, toReAdd, errorMsg)
                }
                recordRegistrationFailure(toRemoveIds, errorMsg)
                onOpFinished()
            }
        }

        // 2. Purely new IDs can be registered immediately
        if (willAttemptDirectNew) {
            checkPrerequisitesAndRegister(purelyNew, onComplete = onOpFinished)
        }
    }

    fun evaluatePrerequisites(): GeofencePrerequisitesResult {
        val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocEnabled = if (lm == null) false else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }

        return evaluateGeofencePrerequisites(
            hasFineLocation = hasFine,
            hasCoarseLocation = hasCoarse,
            hasBackgroundLocation = hasBackground,
            isLocationEnabled = isLocEnabled,
        )
    }

    private fun checkPrerequisitesAndRegister(
        rulesToAdd: List<Automation>,
        onComplete: () -> Unit,
    ) {
        if (rulesToAdd.isEmpty()) {
            onComplete()
            return
        }

        val prereqResult = evaluatePrerequisites()

        when (prereqResult) {
            is GeofencePrerequisitesResult.Ready -> {
                Log.i(TAG, "Geofence prerequisites verified (fine=${prereqResult.isFine}). Registering ${rulesToAdd.size} rule(s)...")
                registerGeofences(rulesToAdd, onComplete)
            }
            is GeofencePrerequisitesResult.MissingLocationPermission -> {
                Log.w(TAG, "Cannot register geofences: ${prereqResult.message}")
                retryPolicy.recordFailure(rulesToAdd.map { it.id }, rulesToAdd, prereqResult.message)
                recordRegistrationFailure(rulesToAdd.map { it.id }, prereqResult.message)
                onComplete()
            }
            is GeofencePrerequisitesResult.MissingBackgroundLocation -> {
                Log.w(TAG, "Cannot register geofences: ${prereqResult.message}")
                retryPolicy.recordFailure(rulesToAdd.map { it.id }, rulesToAdd, prereqResult.message)
                recordRegistrationFailure(rulesToAdd.map { it.id }, prereqResult.message)
                onComplete()
            }
            is GeofencePrerequisitesResult.LocationServicesDisabled -> {
                Log.w(TAG, "Cannot register geofences: ${prereqResult.message}")
                retryPolicy.recordFailure(rulesToAdd.map { it.id }, rulesToAdd, prereqResult.message)
                recordRegistrationFailure(rulesToAdd.map { it.id }, prereqResult.message)
                onComplete()
            }
        }
    }

    private fun registerGeofences(
        rulesToAdd: List<Automation>,
        onComplete: () -> Unit,
    ) {
        val geofenceList = rulesToAdd.map { rule ->
            Geofence.Builder()
                .setRequestId(rule.id)
                .setCircularRegion(
                    rule.geofenceLatitude,
                    rule.geofenceLongitude,
                    rule.geofenceRadiusMeters.toFloat(),
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(5_000)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofenceList)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Successfully registered ${rulesToAdd.size} geofence(s) with system")
                    rulesToAdd.forEach { registeredRules[it.id] = it }
                    retryPolicy.recordSuccess(rulesToAdd.map { it.id })
                    recordRegistration(rulesToAdd.map { it.id })
                    onComplete()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register geofences with Google Play Services: ${e.message}", e)
                    val errorMsg = e.message.orEmpty().ifBlank { "Registration failed" }
                    retryPolicy.recordFailure(rulesToAdd.map { it.id }, rulesToAdd, errorMsg)
                    recordRegistrationFailure(rulesToAdd.map { it.id }, errorMsg)
                    onComplete()
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering geofences: ${e.message}", e)
            val errorMsg = "Security error: ${e.message.orEmpty()}"
            retryPolicy.recordFailure(rulesToAdd.map { it.id }, rulesToAdd, errorMsg)
            recordRegistrationFailure(rulesToAdd.map { it.id }, errorMsg)
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error registering geofences: ${e.message}", e)
            val errorMsg = e.message.orEmpty().ifBlank { "Unexpected registration error" }
            retryPolicy.recordFailure(rulesToAdd.map { it.id }, rulesToAdd, errorMsg)
            recordRegistrationFailure(rulesToAdd.map { it.id }, errorMsg)
            onComplete()
        }
    }

    /**
     * Drains pending geofence transitions from the persistent queue in AutomationRepository.
     * Discards any transitions older than MAX_EVENT_AGE_MS.
     */
    suspend fun drainTransitions(): List<GeofenceTransition> {
        val drained = repository.drainGeofenceEvents()
        if (drained.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return drained.filter { now - it.timestamp <= MAX_EVENT_AGE_MS }
    }

    /**
     * Stops the geofence tracker.
     * If removeSystemGeofences is false (e.g. temporary service recreation or lifecycle restart while engine
     * is still enabled), hardware-backed geofences remain active in Google Play Services so incoming transitions
     * continue waking up the engine via GeofenceBroadcastReceiver.
     * If removeSystemGeofences is true (engine disabled by user), all geofences are unregistered from the OS.
     */
    @Synchronized
    fun stop(removeSystemGeofences: Boolean = false) {
        if (!removeSystemGeofences) {
            Log.d(TAG, "GeofenceTracker stopped while engine remains enabled; retaining system geofences")
            return
        }
        retryPolicy.reset()
        if (registeredRules.isNotEmpty()) {
            val allIds = registeredRules.keys.toList()
            try {
                geofencingClient.removeGeofences(allIds)
                    .addOnSuccessListener {
                        Log.i(TAG, "Removed all ${allIds.size} geofence(s) on stop (engine disabled)")
                        recordUnregistration(allIds)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to remove geofences on stop: ${e.message}")
                        recordRegistrationFailure(allIds, "Unregister failed: ${e.message.orEmpty()}")
                    }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException removing geofences on stop: ${e.message}")
                recordRegistrationFailure(allIds, "Unregister security error: ${e.message.orEmpty()}")
            }
            registeredRules.clear()
        }
    }

    fun getRegisteredRules(): Map<String, Automation> = registeredRules.toMap()

    private fun recordRegistration(automationIds: List<String>) {
        CoroutineScope(Dispatchers.IO).launch { repository.recordGeofenceRegistration(automationIds) }
    }

    private fun recordUnregistration(automationIds: List<String>) {
        CoroutineScope(Dispatchers.IO).launch { repository.recordGeofenceUnregistration(automationIds) }
    }

    private fun recordRegistrationFailure(automationIds: List<String>, error: String) {
        CoroutineScope(Dispatchers.IO).launch { repository.recordGeofenceRegistrationFailure(automationIds, error) }
    }
}
