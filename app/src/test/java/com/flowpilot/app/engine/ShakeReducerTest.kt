package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShakeReducerTest {

    // (30, 0, 9.81) -> magnitude ~31.56 -> net ~21.75 m/s^2, above every default threshold.
    private val strongShake = Triple(30f, 0f, 9.81f)

    // (20, 0, 9.81) -> magnitude ~22.27 -> net ~12.46 m/s^2, a light shake.
    private val lightShake = Triple(20f, 0f, 9.81f)

    // (0, 0, 9.81) -> net ~0, device at rest.
    private val atRest = Triple(0f, 0f, 9.81f)

    private fun evaluate(
        state: ShakeState,
        reading: Triple<Float, Float, Float>,
        nowMs: Long,
        threshold: Float = ShakeReducer.DEFAULT_THRESHOLD_ACCEL,
    ) = ShakeReducer.evaluate(state, reading.first, reading.second, reading.third, nowMs, threshold)

    @Test
    fun smallMovement_doesNotTriggerShake() {
        val (nextState, triggered) = evaluate(ShakeState(), atRest, nowMs = 1000L)

        assertThat(triggered).isFalse()
        assertThat(nextState.lastShakeTimestampMs).isEqualTo(0L)
        assertThat(nextState.armed).isTrue()
    }

    @Test
    fun lightShake_isIgnoredAtDefaultStrength() {
        val (_, triggered) = evaluate(ShakeState(), lightShake, nowMs = 1000L)

        assertThat(triggered).isFalse()
    }

    @Test
    fun lightShake_triggersAtSoftestThreshold() {
        val (_, triggered) = evaluate(
            ShakeState(),
            lightShake,
            nowMs = 1000L,
            threshold = com.flowpilot.app.data.model.Automation.MIN_SHAKE_THRESHOLD,
        )

        assertThat(triggered).isTrue()
    }

    @Test
    fun minimumThreshold_usesSoftestEnabledRuleAndClamps() {
        assertThat(ShakeReducer.minimumThreshold(emptyList())).isNull()
        assertThat(ShakeReducer.minimumThreshold(listOf(28f, 12f, 20f))).isEqualTo(12f)
        // Out-of-range and unset values are clamped instead of disabling detection.
        assertThat(ShakeReducer.minimumThreshold(listOf(0.5f))).isEqualTo(6f)
        assertThat(ShakeReducer.minimumThreshold(listOf(99f))).isEqualTo(32f)
        assertThat(ShakeReducer.minimumThreshold(listOf(Float.NaN))).isEqualTo(20f)
    }

    @Test
    fun strongAcceleration_triggersShake() {
        val (nextState, triggered) = evaluate(ShakeState(), strongShake, nowMs = 1000L)

        assertThat(triggered).isTrue()
        assertThat(nextState.lastShakeTimestampMs).isEqualTo(1000L)
        assertThat(nextState.armed).isFalse()
    }

    @Test
    fun continuousBurst_triggersOnlyOnce() {
        val (state1, triggered1) = evaluate(ShakeState(), strongShake, nowMs = 1000L)
        assertThat(triggered1).isTrue()

        // Same burst still spiking well past the debounce window: must stay silent.
        val (state2, triggered2) = evaluate(state1, strongShake, nowMs = 1400L)
        assertThat(triggered2).isFalse()
        val (state3, triggered3) = evaluate(state2, strongShake, nowMs = 1900L)
        assertThat(triggered3).isFalse()

        // Signal settles, then a new shake is accepted.
        val (state4, triggered4) = evaluate(state3, atRest, nowMs = 2000L)
        assertThat(triggered4).isFalse()
        assertThat(state4.armed).isTrue()

        val (_, triggered5) = evaluate(state4, strongShake, nowMs = 3000L)
        assertThat(triggered5).isTrue()
    }

    @Test
    fun subThresholdMotion_doesNotReArmDetector() {
        val (state1, triggered1) = evaluate(ShakeState(), strongShake, nowMs = 1000L)
        assertThat(triggered1).isTrue()

        // Net ~12.5 m/s^2: below the 20 threshold but above the 10 release level.
        val (state2, triggered2) = evaluate(state1, lightShake, nowMs = 1200L)
        assertThat(triggered2).isFalse()
        assertThat(state2.armed).isFalse()

        val (_, triggered3) = evaluate(state2, strongShake, nowMs = 1500L)
        assertThat(triggered3).isFalse()
    }

    @Test
    fun debounce_suppressesRapidSecondShake() {
        val (state1, triggered1) = evaluate(ShakeState(), strongShake, nowMs = 1000L)
        assertThat(triggered1).isTrue()

        val (state2, _) = evaluate(state1, atRest, nowMs = 1100L)

        // 300ms after the first shake (below the 800ms debounce), even after re-arming.
        val (state3, triggered3) = evaluate(state2, strongShake, nowMs = 1300L)
        assertThat(triggered3).isFalse()

        // Past the debounce window a new shake registers.
        val (_, triggered4) = evaluate(state3, strongShake, nowMs = 1901L)
        assertThat(triggered4).isTrue()
    }
}
