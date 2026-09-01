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
class SoundProfileExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = SoundProfileExecutor(context = context)

        val result = executor.execute(ActionType.PLAY_SOUND)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for sound profile")
    }

    @Test
    fun execute_whenPolicyAccessDenied_returnsFailureWithoutWriting() {
        val context = RuntimeEnvironment.getApplication()
        var writeAttempted = false

        val executor = SoundProfileExecutor(
            context = context,
            policyAccessChecker = { false },
            ringerModeWriter = { _, _ ->
                writeAttempted = true
            },
        )

        val result = executor.execute(ActionType.SOUND_PROFILE_NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Notification Policy Access")
        assertThat(writeAttempted).isFalse()
    }

    @Test
    fun execute_normalProfile_writesNormalAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenMode: Int? = null
        var currentMode = AudioManager.RINGER_MODE_SILENT

        val executor = SoundProfileExecutor(
            context = context,
            policyAccessChecker = { true },
            ringerModeWriter = { _, mode ->
                writtenMode = mode
                currentMode = mode
            },
            ringerModeReader = { currentMode },
        )

        val result = executor.execute(ActionType.SOUND_PROFILE_NORMAL)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Sound profile set to Normal")
        assertThat(writtenMode).isEqualTo(AudioManager.RINGER_MODE_NORMAL)
    }

    @Test
    fun execute_vibrateProfile_writesVibrateAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenMode: Int? = null
        var currentMode = AudioManager.RINGER_MODE_NORMAL

        val executor = SoundProfileExecutor(
            context = context,
            policyAccessChecker = { true },
            ringerModeWriter = { _, mode ->
                writtenMode = mode
                currentMode = mode
            },
            ringerModeReader = { currentMode },
        )

        val result = executor.execute(ActionType.SOUND_PROFILE_VIBRATE)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Sound profile set to Vibrate")
        assertThat(writtenMode).isEqualTo(AudioManager.RINGER_MODE_VIBRATE)
    }

    @Test
    fun execute_silentProfile_writesSilentAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenMode: Int? = null
        var currentMode = AudioManager.RINGER_MODE_NORMAL

        val executor = SoundProfileExecutor(
            context = context,
            policyAccessChecker = { true },
            ringerModeWriter = { _, mode ->
                writtenMode = mode
                currentMode = mode
            },
            ringerModeReader = { currentMode },
        )

        val result = executor.execute(ActionType.SOUND_PROFILE_SILENT)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Sound profile set to Silent")
        assertThat(writtenMode).isEqualTo(AudioManager.RINGER_MODE_SILENT)
    }

    @Test
    fun execute_whenReadbackMismatches_returnsFailureHonestMismatch() {
        val context = RuntimeEnvironment.getApplication()

        val executor = SoundProfileExecutor(
            context = context,
            policyAccessChecker = { true },
            ringerModeWriter = { _, _ -> },
            ringerModeReader = { AudioManager.RINGER_MODE_NORMAL }, // returns NORMAL even when requested SILENT
        )

        val result = executor.execute(ActionType.SOUND_PROFILE_SILENT)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
    }

    @Test
    fun execute_whenExceptionThrown_returnsFailureWithMessage() {
        val context = RuntimeEnvironment.getApplication()

        val executor = SoundProfileExecutor(
            context = context,
            policyAccessChecker = { true },
            ringerModeWriter = { _, _ -> throw SecurityException("Mock audio ringer mode security block") },
        )

        val result = executor.execute(ActionType.SOUND_PROFILE_SILENT)

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Mock audio ringer mode security block")
    }
}
