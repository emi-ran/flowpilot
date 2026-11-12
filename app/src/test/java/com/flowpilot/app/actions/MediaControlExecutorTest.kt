package com.flowpilot.app.actions

import android.media.session.PlaybackState
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaControlExecutorTest {
    private class Session(
        override val packageName: String = "player",
        override var state: Int? = PlaybackState.STATE_PAUSED,
        override var actions: Long = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
    ) : MediaControlSession {
        var command: String? = null
        var failure: Exception? = null
        private fun send(value: String) { failure?.let { throw it }; command = value }
        override fun play() = send("play")
        override fun pause() = send("pause")
        override fun next() = send("next")
        override fun previous() = send("previous")
    }

    @Test fun dispatchesAllFiveActions() {
        val cases = listOf(
            Triple(ActionType.MEDIA_PLAY, PlaybackState.STATE_PAUSED, "play"),
            Triple(ActionType.MEDIA_PAUSE, PlaybackState.STATE_PLAYING, "pause"),
            Triple(ActionType.MEDIA_TOGGLE, PlaybackState.STATE_PAUSED, "play"),
            Triple(ActionType.MEDIA_TOGGLE, PlaybackState.STATE_PLAYING, "pause"),
            Triple(ActionType.MEDIA_NEXT, PlaybackState.STATE_PLAYING, "next"),
            Triple(ActionType.MEDIA_PREVIOUS, PlaybackState.STATE_PLAYING, "previous"),
        )
        for ((action, state, expected) in cases) {
            val session = Session(state = state)
            val result = MediaControlExecutor({ true }, { listOf(session) }).execute(action)
            assertThat(result.success).isTrue()
            assertThat(result.message).contains("not verified")
            assertThat(session.command).isEqualTo(expected)
        }
    }

    @Test fun selectsPriorityOrExactRequestedPackageWithoutFallback() {
        val first = Session("first")
        val second = Session("second")
        val executor = MediaControlExecutor({ true }, { listOf(first, second) })
        assertThat(executor.execute(ActionType.MEDIA_PLAY).success).isTrue()
        assertThat(first.command).isEqualTo("play")
        first.command = null
        assertThat(executor.execute(ActionType.MEDIA_PLAY, ActionParameters(mediaTargetPackage = " second ")).success).isTrue()
        assertThat(second.command).isEqualTo("play")
        second.command = null
        assertThat(executor.execute(ActionType.MEDIA_PLAY, ActionParameters(mediaTargetPackage = "missing")).success).isFalse()
        assertThat(first.command).isNull()
        assertThat(second.command).isNull()
    }

    @Test fun refusesMissingPermissionServiceAndSession() {
        var queried = false
        val denied = MediaControlExecutor({ false }, { queried = true; emptyList() }).execute(ActionType.MEDIA_PLAY)
        assertThat(denied.success).isFalse()
        assertThat(denied.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
        assertThat(queried).isFalse()
        assertThat(MediaControlExecutor({ true }, { null }).execute(ActionType.MEDIA_PLAY).message).contains("unavailable")
        assertThat(MediaControlExecutor({ true }, { emptyList() }).execute(ActionType.MEDIA_PLAY).success).isFalse()
    }

    @Test fun rejectsUnsupportedCommandsAndUnknownToggleState() {
        val session = Session(actions = 0)
        val executor = MediaControlExecutor({ true }, { listOf(session) })
        assertThat(executor.execute(ActionType.MEDIA_PLAY).message).contains("does not support")
        session.state = null
        assertThat(executor.execute(ActionType.MEDIA_TOGGLE).message).contains("state")
        assertThat(executor.execute(ActionType.SET_MEDIA_VOLUME).success).isFalse()
        assertThat(session.command).isNull()
    }

    @Test fun alreadyDesiredStateIsNotReportedAsCommandSuccess() {
        val session = Session(state = PlaybackState.STATE_PLAYING)
        val executor = MediaControlExecutor({ true }, { listOf(session) })
        assertThat(executor.execute(ActionType.MEDIA_PLAY).success).isFalse()
        session.state = PlaybackState.STATE_STOPPED
        assertThat(executor.execute(ActionType.MEDIA_PAUSE).success).isFalse()
        assertThat(session.command).isNull()
    }

    @Test fun reportsPermissionRevocationAndCommandFailureWithoutLeakingException() {
        val denied = MediaControlExecutor({ true }, { throw SecurityException("private") }).execute(ActionType.MEDIA_PLAY)
        assertThat(denied.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
        val session = Session().apply { failure = IllegalStateException("private") }
        val failed = MediaControlExecutor({ true }, { listOf(session) }).execute(ActionType.MEDIA_PLAY)
        assertThat(failed.success).isFalse()
        assertThat(failed.message).isEqualTo("Media command failed")
    }
}
