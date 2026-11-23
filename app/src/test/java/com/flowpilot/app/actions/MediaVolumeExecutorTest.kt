package com.flowpilot.app.actions

import android.media.AudioManager
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
            setVolume = { _, level -> volume = level },
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
            setVolume = { _, _ -> },
        ).execute(ActionType.SET_MEDIA_VOLUME, ActionParameters(mediaVolumePercent = 50))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("blocked")
    }

    @Test fun execute_selectsIndependentStreamAndPercent() {
        val parameters = ActionParameters(mediaVolumePercent = 20, notificationVolumePercent = 40, alarmVolumePercent = 80)
        val cases = listOf(
            Triple(ActionType.SET_MEDIA_VOLUME, AudioManager.STREAM_MUSIC, 2),
            Triple(ActionType.SET_NOTIFICATION_VOLUME, AudioManager.STREAM_NOTIFICATION, 4),
            Triple(ActionType.SET_ALARM_VOLUME, AudioManager.STREAM_ALARM, 8),
        )
        for ((action, expectedStream, expectedLevel) in cases) {
            var stream = -1
            var volume = -1
            val executor = MediaVolumeExecutor(RuntimeEnvironment.getApplication(), maxVolume = { 10 }, minVolume = { 0 },
                currentVolume = { volume }, setVolume = { s, v -> stream = s; volume = v })
            assertThat(executor.execute(action, parameters).success).isTrue()
            assertThat(stream).isEqualTo(expectedStream)
            assertThat(volume).isEqualTo(expectedLevel)
        }
    }

    @Test fun execute_rejectsBelowMinimumWithoutChangingVolume() {
        var volume = 7
        var setCalled = false
        val executor = MediaVolumeExecutor(RuntimeEnvironment.getApplication(), maxVolume = { 15 }, minVolume = { 1 },
            currentVolume = { volume }, setVolume = { _, level -> setCalled = true; volume = level })
        val result = executor.execute(ActionType.SET_ALARM_VOLUME, ActionParameters(alarmVolumePercent = 0))
        assertThat(result.success).isFalse()
        assertThat(setCalled).isFalse()
        assertThat(volume).isEqualTo(7)
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.VOLUME_BELOW_MINIMUM)
    }

    @Test fun execute_reportsUnadvertisedSystemLimit() {
        var volume = 0
        val executor = MediaVolumeExecutor(RuntimeEnvironment.getApplication(), maxVolume = { 15 }, minVolume = { 1 },
            currentVolume = { volume }, setVolume = { _, level -> volume = level.coerceAtLeast(4) })
        val result = executor.execute(ActionType.SET_ALARM_VOLUME, ActionParameters(alarmVolumePercent = 7))
        assertThat(result.success).isFalse()
        assertThat(volume).isEqualTo(4)
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.STATE_MISMATCH)
    }

    @Test fun execute_reportsSecurityException() {
        val executor = MediaVolumeExecutor(RuntimeEnvironment.getApplication(), maxVolume = { 10 }, minVolume = { 0 },
            setVolume = { _, _ -> throw SecurityException() })
        val result = executor.execute(ActionType.SET_NOTIFICATION_VOLUME, ActionParameters(notificationVolumePercent = 0))
        assertThat(result.success).isFalse()
        assertThat(result.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.VOLUME_PERMISSION_REQUIRED)
        assertThat(result.message).contains("Do Not Disturb")
    }

    @Test fun execute_sanitizesUnexpectedExceptions() {
        val executor = MediaVolumeExecutor(RuntimeEnvironment.getApplication(), maxVolume = { throw IllegalStateException("private") })
        val result = executor.execute(ActionType.SET_ALARM_VOLUME)
        assertThat(result.success).isFalse()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED)
        assertThat(result.message).doesNotContain("private")
    }
}
