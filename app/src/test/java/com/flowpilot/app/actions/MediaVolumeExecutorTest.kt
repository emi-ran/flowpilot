package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaVolumeExecutorTest {
    @Test fun execute_setsPercentageOfDeviceMaximum() {
        var volume = 0
        val result = MediaVolumeExecutor(
            RuntimeEnvironment.getApplication(),
            maxVolume = { 15 },
            currentVolume = { volume },
            setVolume = { volume = it },
        ).execute(ActionType.SET_MEDIA_VOLUME, ActionParameters(mediaVolumePercent = 50))

        assertThat(result.success).isTrue()
        assertThat(volume).isEqualTo(8)
        assertThat(result.message).isEqualTo("Media volume set to 50%")
    }

    @Test fun execute_returnsFailure_whenSystemBlocksChange() {
        val result = MediaVolumeExecutor(
            RuntimeEnvironment.getApplication(),
            maxVolume = { 15 },
            currentVolume = { 0 },
            setVolume = {},
        ).execute(ActionType.SET_MEDIA_VOLUME, ActionParameters(mediaVolumePercent = 50))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("blocked")
    }
}
