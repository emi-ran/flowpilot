package com.flowpilot.app.engine

import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaSessionTrackerTest {
    private val first = MediaSnapshot(false, "1", "First", "Artist")
    private val second = MediaSnapshot(true, "2", "Second", "Other")

    @Test
    fun initialSnapshotIsSilent() {
        assertThat(MediaSessionReducer.reduce("player.one", null, second)).isEmpty()
    }

    @Test
    fun playbackTransitionsAreEmittedOnceWithCurrentMetadataAndPackage() {
        val playing = first.copy(playing = true)
        assertThat(MediaSessionReducer.reduce("player.one", first, playing)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player.one", "First", "Artist"),
        )
        assertThat(MediaSessionReducer.reduce("player.one", playing, playing)).isEmpty()
        assertThat(MediaSessionReducer.reduce("player.one", playing, first)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PAUSED, "player.one", "First", "Artist"),
        )
    }

    @Test
    fun trackChangeAndPlaybackChangeCanOccurTogether() {
        assertThat(MediaSessionReducer.reduce("player.two", first, second)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player.two", "Second", "Other"),
            MediaSessionEvent(TriggerEvent.MEDIA_TRACK_CHANGED, "player.two", "Second", "Other"),
        ).inOrder()
        assertThat(MediaSessionReducer.reduce("player.two", second, second)).isEmpty()
    }

    @Test
    fun missingMetadataDoesNotGenerateTrackEvents() {
        val empty = MediaSnapshot(false, null, "", "")
        assertThat(MediaSessionReducer.reduce("player", empty, first)).isEmpty()
        assertThat(MediaSessionReducer.reduce("player", first, empty)).isEmpty()
        assertThat(MediaSessionReducer.reduce("player", empty, empty.copy(playing = true))).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player", "", ""),
        )
    }

    @Test
    fun unknownPlaybackIsNotMistakenForPause() {
        assertThat(MediaSessionReducer.reduce("player", second, second.copy(playing = null))).isEmpty()
        assertThat(MediaSessionReducer.reduce("player", second.copy(playing = null), first)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_TRACK_CHANGED, "player", "First", "Artist"),
        )
    }

    @Test
    fun bufferingPreservesLastKnownPlaybackInBothDirections() {
        for (initial in listOf(false, true)) {
            var state: MediaSnapshot? = first.copy(playing = initial)
            val buffering = first.copy(playing = null)
            assertThat(MediaSessionReducer.reduce("player", state, buffering)).isEmpty()
            state = MediaSessionReducer.nextSnapshot(state, buffering)
            assertThat(MediaSessionReducer.reduce("player", state, buffering)).isEmpty()
            state = MediaSessionReducer.nextSnapshot(state, buffering)
            val changed = first.copy(playing = !initial)
            assertThat(MediaSessionReducer.reduce("player", state, changed)).containsExactly(
                MediaSessionEvent(if (initial) TriggerEvent.MEDIA_PAUSED else TriggerEvent.MEDIA_PLAYING, "player", "First", "Artist"),
            )
            state = MediaSessionReducer.nextSnapshot(state, changed)
            assertThat(MediaSessionReducer.reduce("player", state, changed)).isEmpty()
        }
    }

    @Test
    fun bufferingBeforeFirstKnownPlaybackDoesNotReplayInitialState() {
        val unknown = first.copy(playing = null)
        val state = MediaSessionReducer.nextSnapshot(null, unknown)
        assertThat(MediaSessionReducer.reduce("player", state, first)).isEmpty()
    }

    @Test
    fun sameTitleDifferentIdAndSameIdDifferentMetadataAreTrackChanges() {
        assertThat(MediaSessionReducer.reduce("player", first, first.copy(mediaId = "2")).single().event)
            .isEqualTo(TriggerEvent.MEDIA_TRACK_CHANGED)
        assertThat(MediaSessionReducer.reduce("player", first, first.copy(artist = "Other")).single().event)
            .isEqualTo(TriggerEvent.MEDIA_TRACK_CHANGED)
    }

    @Test
    fun emptyMetadataPreservesLastTrackUntilAnotherKnownTrackArrives() {
        var state = MediaSessionReducer.nextSnapshot(null, first)
        val empty = MediaSessionReducer.metadataUpdate(state, null, "", "")
        assertThat(MediaSessionReducer.reduce("player", state, empty)).isEmpty()
        state = MediaSessionReducer.nextSnapshot(state, empty)
        val same = MediaSessionReducer.metadataUpdate(state, first.mediaId, first.title, first.artist)
        assertThat(MediaSessionReducer.reduce("player", state, same)).isEmpty()
        val different = MediaSessionReducer.metadataUpdate(state, second.mediaId, second.title, second.artist)
        assertThat(MediaSessionReducer.reduce("player", state, different)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_TRACK_CHANGED, "player", "Second", "Other"),
        )
        state = MediaSessionReducer.nextSnapshot(state, different)
        assertThat(MediaSessionReducer.reduce("player", state, different)).isEmpty()
    }

    @Test
    fun queuedPlaybackUpdatesRetainEachTransitionAndLastObservedMetadata() {
        var state = MediaSessionReducer.nextSnapshot(null, second)
        val paused = MediaSessionReducer.playbackUpdate(state, false)
        assertThat(MediaSessionReducer.reduce("player", state, paused)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PAUSED, "player", "Second", "Other"),
        )
        state = MediaSessionReducer.nextSnapshot(state, paused)
        val playing = MediaSessionReducer.playbackUpdate(state, true)
        assertThat(MediaSessionReducer.reduce("player", state, playing)).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player", "Second", "Other"),
        )
    }

    @Test
    fun startupSessionsAreSilentAndNewPlayingSessionsEmitOnce() {
        val observations = MediaSessionObservations<String>()
        assertThat(observations.initialEvent("startup", "player", second, seeding = true)).isNull()
        assertThat(observations.initialEvent("new", "player", second, seeding = false)).isEqualTo(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player", "Second", "Other"),
        )
        assertThat(observations.initialEvent("new", "player", second, seeding = false)).isNull()
        assertThat(observations.initialEvent("paused", "player", first, seeding = false)).isNull()
        assertThat(observations.initialEvent("unknown", "player", first.copy(playing = null), seeding = false)).isNull()
    }

    @Test
    fun reappearingTokenDoesNotReplayPlayingButNewTokenDoes() {
        val observations = MediaSessionObservations<String>()
        assertThat(observations.initialEvent("original", "player", second, seeding = true)).isNull()
        // A session removed from the active list can reappear with the same token.
        assertThat(observations.initialEvent("original", "player", second, seeding = false)).isNull()
        assertThat(observations.initialEvent("replacement", "player", second, seeding = false)).isEqualTo(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player", "Second", "Other"),
        )
        observations.clear()
        assertThat(observations.initialEvent("original", "player", second, seeding = true)).isNull()
    }

    @Test
    fun newlyObservedPausedSessionStillEmitsObservedPlaybackTransition() {
        val observations = MediaSessionObservations<String>()
        assertThat(observations.initialEvent("new", "player", first, seeding = false)).isNull()
        val state = MediaSessionReducer.nextSnapshot(null, first)
        assertThat(MediaSessionReducer.reduce("player", state, MediaSessionReducer.playbackUpdate(state, true))).containsExactly(
            MediaSessionEvent(TriggerEvent.MEDIA_PLAYING, "player", "First", "Artist"),
        )
    }
}
