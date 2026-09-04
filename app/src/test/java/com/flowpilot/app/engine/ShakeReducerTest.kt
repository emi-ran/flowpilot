package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShakeReducerTest {

    @Test
    fun smallMovement_doesNotTriggerShake() {
        val state = ShakeState()
        // Earth gravity is ~9.81 m/s^2. If sensor reports (0, 0, 9.81), net acceleration is ~0
        val (nextState, triggered) = ShakeReducer.evaluate(state, 0f, 0f, 9.81f, nowMs = 1000L)
        assertThat(triggered).isFalse()
        assertThat(nextState.lastShakeTimestampMs).isEqualTo(0L)
    }

    @Test
    fun strongAcceleration_triggersShake() {
        val state = ShakeState()
        // Strong acceleration: x = 15, y = 10, z = 15 -> magnitude = sqrt(225+100+225) = sqrt(550) ~= 23.45 -> net = 23.45 - 9.81 = 13.64 >= 12.0
        val (nextState, triggered) = ShakeReducer.evaluate(state, 15f, 10f, 15f, nowMs = 1000L)
        assertThat(triggered).isTrue()
        assertThat(nextState.lastShakeTimestampMs).isEqualTo(1000L)
    }

    @Test
    fun debounce_suppressesShakeWithinCooldownWindow() {
        val state = ShakeState()
        val (state1, triggered1) = ShakeReducer.evaluate(state, 20f, 0f, 9.81f, nowMs = 1000L)
        assertThat(triggered1).isTrue()

        // 500ms later (less than 800ms debounce), another shake occurs
        val (state2, triggered2) = ShakeReducer.evaluate(state1, 20f, 0f, 9.81f, nowMs = 1500L)
        assertThat(triggered2).isFalse()
        assertThat(state2.lastShakeTimestampMs).isEqualTo(1000L)

        // 801ms later (> 800ms debounce), should trigger again
        val (state3, triggered3) = ShakeReducer.evaluate(state2, 20f, 0f, 9.81f, nowMs = 1801L)
        assertThat(triggered3).isTrue()
        assertThat(state3.lastShakeTimestampMs).isEqualTo(1801L)
    }
}
