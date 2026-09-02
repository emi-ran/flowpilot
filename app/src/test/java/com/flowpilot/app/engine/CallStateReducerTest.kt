package com.flowpilot.app.engine

import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import android.telephony.TelephonyManager

class CallStateReducerTest {

    @Test
    fun startup_seeding_without_previous_state_does_not_emit_transition() {
        val initial = CallTrackerState()
        val seededIdle = CallStateReducer.reduce(initial, TelephonyManager.CALL_STATE_IDLE, "")
        assertThat(seededIdle.second).isNull()
        assertThat(seededIdle.first.lastRawState).isEqualTo(TelephonyManager.CALL_STATE_IDLE)
        assertThat(seededIdle.first.isSeeded).isTrue()

        val seededOffhook = CallStateReducer.reduce(initial, TelephonyManager.CALL_STATE_OFFHOOK, "")
        assertThat(seededOffhook.second).isNull()
        assertThat(seededOffhook.first.lastRawState).isEqualTo(TelephonyManager.CALL_STATE_OFFHOOK)
        assertThat(seededOffhook.first.isSeeded).isTrue()
    }

    @Test
    fun incoming_call_flow_idle_to_ringing_to_offhook_to_idle() {
        var state = CallTrackerState()
        // Startup seeding
        val seed = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = seed.first

        // 1. Ringing
        val ringing = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_RINGING, "+90 555 123 45 67")
        state = ringing.first
        assertThat(ringing.second).isEqualTo(
            CallTransition(TriggerEvent.CALL_RINGING, "+90 555 123 45 67")
        )

        // 2. Answered (OFFHOOK from RINGING)
        val answered = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_OFFHOOK, "")
        state = answered.first
        assertThat(answered.second).isEqualTo(
            CallTransition(TriggerEvent.CALL_ANSWERED, "+90 555 123 45 67")
        )

        // 3. Ended (IDLE from OFFHOOK)
        val ended = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = ended.first
        assertThat(ended.second).isEqualTo(
            CallTransition(TriggerEvent.CALL_ENDED, "+90 555 123 45 67")
        )
    }

    @Test
    fun outgoing_call_flow_idle_to_offhook_to_idle() {
        var state = CallTrackerState()
        val seed = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = seed.first

        // Outgoing call (IDLE -> OFFHOOK directly)
        val outgoing = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_OFFHOOK, "05559876543")
        state = outgoing.first
        assertThat(outgoing.second).isEqualTo(
            CallTransition(TriggerEvent.CALL_OUTGOING, "05559876543")
        )

        // Call ended
        val ended = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = ended.first
        assertThat(ended.second).isEqualTo(
            CallTransition(TriggerEvent.CALL_ENDED, "05559876543")
        )
    }

    @Test
    fun missed_call_flow_idle_to_ringing_to_idle() {
        var state = CallTrackerState()
        val seed = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = seed.first

        // Ringing
        val ringing = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_RINGING, "05321112233")
        state = ringing.first
        assertThat(ringing.second?.triggerEvent).isEqualTo(TriggerEvent.CALL_RINGING)

        // Caller hung up before answer -> CALL_ENDED
        val ended = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = ended.first
        assertThat(ended.second).isEqualTo(
            CallTransition(TriggerEvent.CALL_ENDED, "05321112233")
        )
    }

    @Test
    fun duplicate_consecutive_state_is_suppressed() {
        var state = CallTrackerState()
        val seed = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_IDLE, "")
        state = seed.first

        val ringing1 = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_RINGING, "5551234")
        state = ringing1.first
        assertThat(ringing1.second).isNotNull()

        // Consecutive identical ringing with same number
        val ringing2 = CallStateReducer.reduce(state, TelephonyManager.CALL_STATE_RINGING, "5551234")
        assertThat(ringing2.second).isNull()
    }
}
