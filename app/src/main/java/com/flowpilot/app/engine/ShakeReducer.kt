package com.flowpilot.app.engine

import kotlin.math.abs
import kotlin.math.sqrt

data class ShakeState(
    val lastShakeTimestampMs: Long = 0L,
)

object ShakeReducer {
    const val DEFAULT_THRESHOLD_ACCEL = 12.0f // m/s^2 acceleration above gravity
    const val DEFAULT_DEBOUNCE_MS = 800L
    private const val GRAVITY = 9.81f

    /**
     * Calculates net acceleration magnitude excluding gravity: |sqrt(x^2 + y^2 + z^2) - 9.81|
     */
    fun calculateNetAcceleration(x: Float, y: Float, z: Float): Float {
        val totalMagnitude = sqrt(x * x + y * y + z * z)
        return abs(totalMagnitude - GRAVITY)
    }

    /**
     * Evaluates accelerometer readings. If acceleration spike exceeds threshold and debounce
     * has elapsed, returns updated state with didShake = true.
     */
    fun evaluate(
        state: ShakeState,
        x: Float,
        y: Float,
        z: Float,
        nowMs: Long,
        threshold: Float = DEFAULT_THRESHOLD_ACCEL,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    ): Pair<ShakeState, Boolean> {
        val netAccel = calculateNetAcceleration(x, y, z)
        val elapsed = nowMs - state.lastShakeTimestampMs

        if (netAccel >= threshold && (state.lastShakeTimestampMs == 0L || elapsed >= debounceMs)) {
            return ShakeState(lastShakeTimestampMs = nowMs) to true
        }
        return state to false
    }
}
