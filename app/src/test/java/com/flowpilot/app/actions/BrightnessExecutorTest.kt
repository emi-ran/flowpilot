package com.flowpilot.app.actions

import android.content.Context
import android.provider.Settings
import com.flowpilot.app.actions.ActionParameters
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrightnessExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = BrightnessExecutor(context = context)

        val result = executor.execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for brightness executor")
    }

    @Test
    fun execute_whenPermissionDenied_returnsFailureWithoutWriting() {
        val context = RuntimeEnvironment.getApplication()
        var writeAttempted = false

        val executor = BrightnessExecutor(
            context = context,
            permissionChecker = { false },
            settingsWriter = { _, _, _ ->
                writeAttempted = true
                true
            },
        )

        val result = executor.execute(ActionType.AUTO_BRIGHTNESS_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("WRITE_SETTINGS")
        assertThat(writeAttempted).isFalse()
    }

    @Test
    fun execute_autoBrightnessOn_writesAutomaticMode() {
        val context = RuntimeEnvironment.getApplication()
        val writtenSettings = mutableMapOf<String, Int>()

        val executor = BrightnessExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, key, value ->
                writtenSettings[key] = value
                true
            },
            settingsReader = { _, key, defaultVal ->
                writtenSettings[key] ?: defaultVal
            },
        )

        val result = executor.execute(ActionType.AUTO_BRIGHTNESS_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Auto-brightness enabled")
        assertThat(writtenSettings[Settings.System.SCREEN_BRIGHTNESS_MODE])
            .isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
    }

    @Test
    fun execute_autoBrightnessOff_writesManualMode() {
        val context = RuntimeEnvironment.getApplication()
        val writtenSettings = mutableMapOf<String, Int>()

        val executor = BrightnessExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, key, value ->
                writtenSettings[key] = value
                true
            },
            settingsReader = { _, key, defaultVal ->
                writtenSettings[key] ?: defaultVal
            },
        )

        val result = executor.execute(ActionType.AUTO_BRIGHTNESS_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Auto-brightness disabled")
        assertThat(writtenSettings[Settings.System.SCREEN_BRIGHTNESS_MODE])
            .isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
    }

    @Test
    fun execute_setScreenBrightness_writesManualModeAndTargetBrightness() {
        val context = RuntimeEnvironment.getApplication()
        val writtenSettings = mutableMapOf<String, Int>()

        val executor = BrightnessExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, key, value ->
                writtenSettings[key] = value
                true
            },
            settingsReader = { _, key, defaultVal ->
                writtenSettings[key] ?: defaultVal
            },
        )

        val result = executor.execute(
            ActionType.SET_SCREEN_BRIGHTNESS,
            ActionParameters(screenBrightnessPercent = 50),
        )

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("50%")
        assertThat(writtenSettings[Settings.System.SCREEN_BRIGHTNESS_MODE])
            .isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val expectedBrightness = (50 * 255) / 100
        assertThat(writtenSettings[Settings.System.SCREEN_BRIGHTNESS])
            .isEqualTo(expectedBrightness)
    }

    @Test
    fun execute_whenExceptionThrown_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()

        val executor = BrightnessExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, _, _ ->
                throw SecurityException("Security error")
            },
        )

        val result = executor.execute(ActionType.AUTO_BRIGHTNESS_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Auto-brightness error")
    }
}
