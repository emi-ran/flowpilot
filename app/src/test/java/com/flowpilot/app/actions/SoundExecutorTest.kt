package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.SoundPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundExecutorTest {
    @Test fun execute_playsSelectedSystemSound() {
        var played = false
        val result = SoundExecutor(
            RuntimeEnvironment.getApplication(),
            playUri = { uri, _ -> played = uri.scheme == "content"; true },
        ).execute(ActionType.PLAY_SOUND, ActionParameters(soundPreset = SoundPreset.NOTIFICATION))

        assertThat(result.success).isTrue()
        assertThat(played).isTrue()
    }

    @Test fun execute_returnsFailure_whenCustomFileMissing() {
        val result = SoundExecutor(
            RuntimeEnvironment.getApplication(),
            playUri = { _, _ -> error("must not play") },
        ).execute(ActionType.PLAY_SOUND, ActionParameters(soundPreset = SoundPreset.CUSTOM))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("unavailable")
    }

    @Test fun stopPreview_isSafe_whenNothingIsPlaying() {
        SoundExecutor(RuntimeEnvironment.getApplication(), playUri = { _, _ -> true }).stopPreview()
    }
}
