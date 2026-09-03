package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceFlipReducerTest {

    @Test
    fun raw_orientation_correctly_classifies_face_down_and_face_up() {
        // Face down: near obstacle and screen pointing downward (-9.8 Z)
        val faceDown = DeviceFlipReducer.determineRawOrientation(
            isNear = true,
            x = 0.5f,
            y = 0.2f,
            z = -9.8f,
        )
        assertThat(faceDown).isEqualTo(FlipOrientation.FACE_DOWN)

        // Face up: far obstacle and screen pointing upward (+9.8 Z)
        val faceUp = DeviceFlipReducer.determineRawOrientation(
            isNear = false,
            x = 0.2f,
            y = 0.3f,
            z = 9.8f,
        )
        assertThat(faceUp).isEqualTo(FlipOrientation.FACE_UP)

        // Screen pointing down in mid-air (proximity FAR) -> MUST be OTHER (not face down on table)
        val midAirScreenDown = DeviceFlipReducer.determineRawOrientation(
            isNear = false,
            x = 0.0f,
            y = 0.0f,
            z = -9.8f,
        )
        assertThat(midAirScreenDown).isEqualTo(FlipOrientation.OTHER)

        // Proximity NEAR but phone held upright in pocket or in hand (Y = 9.8) -> OTHER
        val uprightNear = DeviceFlipReducer.determineRawOrientation(
            isNear = true,
            x = 0.0f,
            y = 9.8f,
            z = 0.0f,
        )
        assertThat(uprightNear).isEqualTo(FlipOrientation.OTHER)
    }

    @Test
    fun startup_seeding_without_previous_state_does_not_emit_transition() {
        val initial = FlipTrackerState()
        val (seededState, event) = DeviceFlipReducer.reduce(
            state = initial,
            rawOrientation = FlipOrientation.FACE_DOWN,
            timestampMs = 1000L,
            debounceMs = 500L,
        )

        assertThat(event).isNull()
        assertThat(seededState.isSeeded).isTrue()
        assertThat(seededState.currentOrientation).isEqualTo(FlipOrientation.FACE_DOWN)
    }

    @Test
    fun full_flip_down_and_flip_up_flow_with_debounce() {
        var state = FlipTrackerState()

        // 1. Initial startup seed as FACE_UP
        val seed = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_UP, timestampMs = 1000L, debounceMs = 500L)
        state = seed.first
        assertThat(seed.second).isNull()
        assertThat(state.currentOrientation).isEqualTo(FlipOrientation.FACE_UP)

        // 2. Phone flipped to FACE_DOWN at t = 2000ms
        val flip1 = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_DOWN, timestampMs = 2000L, debounceMs = 500L)
        state = flip1.first
        assertThat(flip1.second).isNull() // Waiting for debounce
        assertThat(state.candidateOrientation).isEqualTo(FlipOrientation.FACE_DOWN)

        // 3. Still FACE_DOWN at t = 2300ms (elapsed 300ms < 500ms)
        val flip2 = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_DOWN, timestampMs = 2300L, debounceMs = 500L)
        state = flip2.first
        assertThat(flip2.second).isNull()

        // 4. Stable FACE_DOWN at t = 2500ms (elapsed 500ms == debounceMs) -> FLIPPED_DOWN emitted!
        val flip3 = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_DOWN, timestampMs = 2500L, debounceMs = 500L)
        state = flip3.first
        assertThat(flip3.second).isEqualTo(FlipEvent.FLIPPED_DOWN)
        assertThat(state.currentOrientation).isEqualTo(FlipOrientation.FACE_DOWN)

        // 5. Subsequent samples remaining FACE_DOWN -> deduped, no more events
        val steady = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_DOWN, timestampMs = 3000L, debounceMs = 500L)
        state = steady.first
        assertThat(steady.second).isNull()

        // 6. Flipped back to FACE_UP at t = 4000ms
        val flipUp1 = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_UP, timestampMs = 4000L, debounceMs = 500L)
        state = flipUp1.first
        assertThat(flipUp1.second).isNull()

        // 7. Stable FACE_UP at t = 4500ms -> FLIPPED_UP emitted!
        val flipUp2 = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_UP, timestampMs = 4500L, debounceMs = 500L)
        state = flipUp2.first
        assertThat(flipUp2.second).isEqualTo(FlipEvent.FLIPPED_UP)
        assertThat(state.currentOrientation).isEqualTo(FlipOrientation.FACE_UP)
    }

    @Test
    fun unstable_motion_resets_debounce_without_trigger() {
        var state = FlipTrackerState()
        // Seed as FACE_UP
        state = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_UP, timestampMs = 1000L).first

        // Brief candidate FACE_DOWN
        state = DeviceFlipReducer.reduce(state, FlipOrientation.FACE_DOWN, timestampMs = 2000L).first

        // Moved to OTHER after 200ms
        val (stateOther, eventOther) = DeviceFlipReducer.reduce(state, FlipOrientation.OTHER, timestampMs = 2200L)
        state = stateOther
        assertThat(eventOther).isNull()
        assertThat(state.candidateOrientation).isEqualTo(FlipOrientation.OTHER)

        // Sample at 2600ms -> still OTHER, never emitted FLIPPED_DOWN
        val (_, eventFinal) = DeviceFlipReducer.reduce(state, FlipOrientation.OTHER, timestampMs = 2600L)
        assertThat(eventFinal).isNull()
    }
}
