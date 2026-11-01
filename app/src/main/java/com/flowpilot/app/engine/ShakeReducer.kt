package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import kotlin.math.abs
import kotlin.math.sqrt

data class ShakeState(
    val lastShakeTimestampMs: Long = 0L,
    /** False while a shake burst is still in progress; re-armed once the signal settles. */
    val armed: Boolean = true,
)

/** A detected shake with the measured force so each rule can apply its own threshold. */
data class ShakeEvent(
    val timestampMs: Long,
    /** Net acceleration above gravity in m/s^2 at the triggering sample. */
    val magnitude: Float,
)

object ShakeReducer {
    const val DEFAULT_THRESHOLD_ACCEL = Automation.DEFAULT_SHAKE_THRESHOLD // m/s^2 acceleration above gravity
    const val DEFAULT_DEBOUNCE_MS = 800L

    /** Signal must fall below this fraction of the threshold before another shake can register. */
    const val RELEASE_RATIO = 0.5f

    private const val GRAVITY = 9.81f

    /** Keeps a stored per-rule force inside the range the detector and UI can express. */
    fun clampThreshold(value: Float): Float =
        if (value.isNaN()) DEFAULT_THRESHOLD_ACCEL
        else value.coerceIn(Automation.MIN_SHAKE_THRESHOLD, Automation.MAX_SHAKE_THRESHOLD)

    /**
     * Calculates net acceleration magnitude excluding gravity: |sqrt(x^2 + y^2 + z^2) - 9.81|
     */
    fun calculateNetAcceleration(x: Float, y: Float, z: Float): Float {
        val totalMagnitude = sqrt(x * x + y * y + z * z)
        return abs(totalMagnitude - GRAVITY)
    }

    fun evaluate(
        state: ShakeState,
        x: Float,
        y: Float,
        z: Float,
        nowMs: Long,
        threshold: Float = DEFAULT_THRESHOLD_ACCEL,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    ): Pair<ShakeState, Boolean> = evaluateNet(
        state = state,
        netAccel = calculateNetAcceleration(x, y, z),
        nowMs = nowMs,
        threshold = threshold,
        debounceMs = debounceMs,
    )

    /**
     * Evaluates a net acceleration sample.
     *
     * A trigger requires the net acceleration to exceed [threshold] while the detector is armed.
     * The detector disarms after each trigger and re-arms only when the signal drops below
     * [RELEASE_RATIO] × [threshold], so one continuous shake produces exactly one event even when
     * its acceleration spikes last longer than [debounceMs]. [debounceMs] additionally enforces a
     * minimum gap between two distinct shakes.
     */
    fun evaluateNet(
        state: ShakeState,
        netAccel: Float,
        nowMs: Long,
        threshold: Float = DEFAULT_THRESHOLD_ACCEL,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    ): Pair<ShakeState, Boolean> {
        val elapsed = nowMs - state.lastShakeTimestampMs
        val armed = state.armed || netAccel <= threshold * RELEASE_RATIO

        val triggered = armed &&
            netAccel >= threshold &&
            (state.lastShakeTimestampMs == 0L || elapsed >= debounceMs)

        return if (triggered) {
            ShakeState(lastShakeTimestampMs = nowMs, armed = false) to true
        } else {
            state.copy(armed = armed) to false
        }
    }

    /** Lowest force that must be detected for the given enabled shake rules; null when none apply. */
    fun minimumThreshold(thresholds: Iterable<Float>): Float? =
        thresholds.map { clampThreshold(it) }.minOrNull()
}
