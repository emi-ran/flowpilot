package com.flowpilot.app.engine

import kotlin.math.sqrt

enum class FlipOrientation {
    UNKNOWN,
    FACE_UP,
    FACE_DOWN,
    OTHER,
}

enum class FlipEvent {
    FLIPPED_DOWN,
    FLIPPED_UP,
}

data class FlipTrackerState(
    val currentOrientation: FlipOrientation = FlipOrientation.UNKNOWN,
    val candidateOrientation: FlipOrientation = FlipOrientation.UNKNOWN,
    val candidateSinceMs: Long = 0L,
    val isSeeded: Boolean = false,
)

/**
 * Pure orientation detection and state-transition reducer.
 * Ensures edge-triggered execution, stability debounce, and zero startup replay.
 */
object DeviceFlipReducer {

    const val DEFAULT_DEBOUNCE_MS: Long = 500L
    private const val GRAVITY_THRESHOLD = 6.5f
    private const val TILT_THRESHOLD = 6.0f

    /**
     * Determines the raw physical orientation from proximity and gravity/accelerometer vectors.
     *
     * @param isNear true if the proximity sensor reports a close obstacle (e.g. table surface)
     * @param x gravity X axis component (m/s^2)
     * @param y gravity Y axis component (m/s^2)
     * @param z gravity Z axis component (m/s^2, -9.8 when screen points down, +9.8 when screen points up)
     */
    fun determineRawOrientation(
        isNear: Boolean,
        x: Float,
        y: Float,
        z: Float,
    ): FlipOrientation {
        val lateralForce = sqrt((x * x) + (y * y))

        return when {
            // Screen pointing towards ground AND proximity sensor covered by a surface
            isNear && z <= -GRAVITY_THRESHOLD && lateralForce <= TILT_THRESHOLD -> {
                FlipOrientation.FACE_DOWN
            }
            // Screen pointing towards ceiling AND proximity sensor clear
            !isNear && z >= GRAVITY_THRESHOLD && lateralForce <= TILT_THRESHOLD -> {
                FlipOrientation.FACE_UP
            }
            else -> FlipOrientation.OTHER
        }
    }

    /**
     * Reduces the current tracker state with a newly sampled raw orientation.
     */
    fun reduce(
        state: FlipTrackerState,
        rawOrientation: FlipOrientation,
        timestampMs: Long,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    ): Pair<FlipTrackerState, FlipEvent?> {
        // 1. Initial startup seeding: seed state without firing a trigger
        if (!state.isSeeded) {
            if (rawOrientation == FlipOrientation.UNKNOWN) {
                return state to null
            }
            return state.copy(
                currentOrientation = rawOrientation,
                candidateOrientation = rawOrientation,
                candidateSinceMs = timestampMs,
                isSeeded = true,
            ) to null
        }

        // 2. Already settled in this orientation
        if (rawOrientation == state.currentOrientation) {
            return if (state.candidateOrientation != rawOrientation) {
                state.copy(
                    candidateOrientation = rawOrientation,
                    candidateSinceMs = timestampMs,
                ) to null
            } else {
                state to null
            }
        }

        // 3. New candidate orientation detected
        if (rawOrientation != state.candidateOrientation) {
            return state.copy(
                candidateOrientation = rawOrientation,
                candidateSinceMs = timestampMs,
            ) to null
        }

        // 4. Candidate has been steady — check debounce elapsed time
        val elapsed = timestampMs - state.candidateSinceMs
        if (elapsed >= debounceMs) {
            val event = when {
                rawOrientation == FlipOrientation.FACE_DOWN && state.currentOrientation != FlipOrientation.FACE_DOWN -> {
                    FlipEvent.FLIPPED_DOWN
                }
                rawOrientation == FlipOrientation.FACE_UP && state.currentOrientation == FlipOrientation.FACE_DOWN -> {
                    FlipEvent.FLIPPED_UP
                }
                else -> null
            }

            val nextState = state.copy(
                currentOrientation = rawOrientation,
                candidateOrientation = rawOrientation,
                candidateSinceMs = timestampMs,
            )
            return nextState to event
        }

        // Still waiting for debounce duration
        return state to null
    }
}
