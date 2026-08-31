package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForegroundReducerTest {

    private val akbank = "com.akbank.android.apps.akbank_direkt"
    private val launcher = "com.android.launcher"
    private val spotify = "com.spotify.music"

    @Test
    fun batch_akbank_then_launcher_suppresses_akbank_opened() {
        val initial = ForegroundState(currentForeground = null)
        val transitions = listOf(
            ForegroundReducer.Transition(packageName = akbank, isForeground = true, timestamp = 100L),
            ForegroundReducer.Transition(packageName = launcher, isForeground = true, timestamp = 200L),
        )

        val output = ForegroundReducer.reduceBatch(initial, transitions)

        assertThat(output.openedPackage).isEqualTo(launcher)
        assertThat(output.closedPackage).isNull()
        assertThat(output.state.currentForeground).isEqualTo(launcher)
        assertThat(output.state.openLocks).containsExactly(launcher)
    }

    @Test
    fun unsorted_batch_uses_timestamp_order_and_suppresses_stale_akbank_opened() {
        val transitions = listOf(
            ForegroundReducer.Transition(packageName = launcher, isForeground = true, timestamp = 200L),
            ForegroundReducer.Transition(packageName = akbank, isForeground = true, timestamp = 100L),
        )

        val output = ForegroundReducer.reduceBatch(ForegroundState(), transitions)

        assertThat(output.openedPackage).isEqualTo(launcher)
        assertThat(output.state.currentForeground).isEqualTo(launcher)
    }

    @Test
    fun final_akbank_opens_once() {
        val initial = ForegroundState(currentForeground = launcher, openLocks = setOf(launcher))
        val transitions = listOf(
            ForegroundReducer.Transition(packageName = akbank, isForeground = true, timestamp = 100L),
        )

        val output = ForegroundReducer.reduceBatch(initial, transitions)

        assertThat(output.openedPackage).isEqualTo(akbank)
        assertThat(output.closedPackage).isEqualTo(launcher)
        assertThat(output.state.currentForeground).isEqualTo(akbank)
        assertThat(output.state.openLocks).containsExactly(akbank)
    }

    @Test
    fun repeated_resumed_events_deduped() {
        val initial = ForegroundState(currentForeground = akbank, openLocks = setOf(akbank))
        val transitions = listOf(
            ForegroundReducer.Transition(packageName = akbank, isForeground = true, timestamp = 100L),
            ForegroundReducer.Transition(packageName = akbank, isForeground = true, timestamp = 200L),
        )

        val output = ForegroundReducer.reduceBatch(initial, transitions)

        assertThat(output.openedPackage).isNull()
        assertThat(output.closedPackage).isNull()
        assertThat(output.state.currentForeground).isEqualTo(akbank)
        assertThat(output.state.openLocks).containsExactly(akbank)
    }

    @Test
    fun close_event_fires_once_when_backgrounded() {
        val initial = ForegroundState(currentForeground = akbank, openLocks = setOf(akbank))
        val transitions = listOf(
            ForegroundReducer.Transition(packageName = akbank, isForeground = false, timestamp = 100L),
        )

        val output = ForegroundReducer.reduceBatch(initial, transitions)

        assertThat(output.openedPackage).isNull()
        assertThat(output.closedPackage).isEqualTo(akbank)
        assertThat(output.state.currentForeground).isNull()
        assertThat(output.state.openLocks).isEmpty()
    }

    @Test
    fun history_no_replay_when_empty_transitions() {
        val initial = ForegroundState(currentForeground = null, openLocks = emptySet())
        val output = ForegroundReducer.reduceBatch(initial, emptyList())

        assertThat(output.openedPackage).isNull()
        assertThat(output.closedPackage).isNull()
        assertThat(output.state).isEqualTo(initial)
    }

    @Test
    fun multiple_transitions_close_and_open_correctly() {
        val initial = ForegroundState(currentForeground = spotify, openLocks = setOf(spotify))
        val transitions = listOf(
            ForegroundReducer.Transition(packageName = spotify, isForeground = false, timestamp = 100L),
            ForegroundReducer.Transition(packageName = akbank, isForeground = true, timestamp = 150L),
        )

        val output = ForegroundReducer.reduceBatch(initial, transitions)

        assertThat(output.closedPackage).isEqualTo(spotify)
        assertThat(output.openedPackage).isEqualTo(akbank)
        assertThat(output.state.currentForeground).isEqualTo(akbank)
        assertThat(output.state.openLocks).containsExactly(akbank)
    }
}
