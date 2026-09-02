package com.flowpilot.app.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.flowpilot.app.data.model.TriggerEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

data class CallTransition(
    val triggerEvent: TriggerEvent,
    /** Transient in-memory phone number if provided by system callback. Never persisted or logged in plain text. */
    val phoneNumber: String? = null,
)

data class CallTrackerState(
    val lastRawState: Int = TelephonyManager.CALL_STATE_IDLE,
    val activeNumber: String? = null,
    val isSeeded: Boolean = false,
)

/**
 * Pure telephony state transition reducer with deduplication and no startup replay.
 */
object CallStateReducer {
    fun reduce(
        state: CallTrackerState,
        rawState: Int,
        incomingNumber: String? = null,
    ): Pair<CallTrackerState, CallTransition?> {
        val cleanNumber = incomingNumber?.takeIf { it.isNotBlank() }

        // Initial startup seed: record current call state without producing trigger events.
        if (!state.isSeeded) {
            return state.copy(
                lastRawState = rawState,
                activeNumber = cleanNumber,
                isSeeded = true,
            ) to null
        }

        // Consecutive identical state deduplication
        if (state.lastRawState == rawState) {
            val updatedNumber = cleanNumber ?: state.activeNumber
            return state.copy(activeNumber = updatedNumber) to null
        }

        return when (rawState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val number = cleanNumber ?: state.activeNumber
                state.copy(
                    lastRawState = TelephonyManager.CALL_STATE_RINGING,
                    activeNumber = number,
                ) to CallTransition(TriggerEvent.CALL_RINGING, number)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val number = cleanNumber ?: state.activeNumber
                val event = if (state.lastRawState == TelephonyManager.CALL_STATE_RINGING) {
                    TriggerEvent.CALL_ANSWERED
                } else {
                    TriggerEvent.CALL_OUTGOING
                }
                state.copy(
                    lastRawState = TelephonyManager.CALL_STATE_OFFHOOK,
                    activeNumber = number,
                ) to CallTransition(event, number)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val number = cleanNumber ?: state.activeNumber
                val transition = if (state.lastRawState == TelephonyManager.CALL_STATE_OFFHOOK ||
                    state.lastRawState == TelephonyManager.CALL_STATE_RINGING
                ) {
                    CallTransition(TriggerEvent.CALL_ENDED, number)
                } else {
                    null
                }
                state.copy(
                    lastRawState = TelephonyManager.CALL_STATE_IDLE,
                    activeNumber = null,
                ) to transition
            }
            else -> state to null
        }
    }
}

/**
 * Tracks telephony call state transitions using TelephonyManager while the automation engine runs.
 * Gated by READ_PHONE_STATE permission.
 */
class CallStateTracker(private val context: Context) {

    private val transitions = ConcurrentLinkedQueue<CallTransition>()
    private var registered = false
    private var state = CallTrackerState()
    private val executor = Executors.newSingleThreadExecutor()

    private var telephonyCallback: Any? = null
    @Suppress("DEPRECATION")
    private var legacyListener: PhoneStateListener? = null

    fun start() {
        if (registered) return
        if (!hasPermission()) {
            Log.w(TAG, "CallStateTracker registration skipped: READ_PHONE_STATE not granted")
            return
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        // Seed initial state without emitting trigger
        val initialCallState = try {
            tm.callState
        } catch (_: SecurityException) {
            TelephonyManager.CALL_STATE_IDLE
        }

        synchronized(this) {
            state = CallTrackerState(lastRawState = initialCallState, isSeeded = true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(callState: Int) {
                        handleRawCallState(callState, null)
                    }
                }
                tm.registerTelephonyCallback(executor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(callState: Int, phoneNumber: String?) {
                        handleRawCallState(callState, phoneNumber)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                legacyListener = listener
            }
            registered = true
            Log.i(TAG, "CallStateTracker registered (initialState=$initialCallState)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register call state listener: ${e.message}")
        }
    }

    private fun handleRawCallState(rawState: Int, phoneNumber: String?) {
        synchronized(this) {
            val result = CallStateReducer.reduce(state, rawState, phoneNumber)
            state = result.first
            result.second?.let {
                transitions.add(it)
                Log.i(TAG, "Call event transition: ${it.triggerEvent.name}")
            }
        }
    }

    fun drainTransitions(): List<CallTransition> = buildList {
        while (true) add(transitions.poll() ?: break)
    }

    fun stop() {
        if (registered) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                        (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
                    } else if (legacyListener != null) {
                        @Suppress("DEPRECATION")
                        tm.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed unregistering call listener: ${t.message}")
                }
            }
            Log.i(TAG, "CallStateTracker unregistered")
        }
        registered = false
        telephonyCallback = null
        legacyListener = null
        synchronized(this) { state = CallTrackerState() }
        transitions.clear()
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "FlowPilotCall"
    }
}
