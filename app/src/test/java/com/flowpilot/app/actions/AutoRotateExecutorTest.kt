package com.flowpilot.app.actions

import android.content.Context
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoRotateExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = AutoRotateExecutor(context = context)

        val result = executor.execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for auto-rotate")
    }

    @Test
    fun execute_whenPermissionDenied_returnsFailureWithoutWriting() {
        val context = RuntimeEnvironment.getApplication()
        var writeAttempted = false

        val executor = AutoRotateExecutor(
            context = context,
            permissionChecker = { false },
            settingsWriter = { _, _, _ ->
                writeAttempted = true
                true
            },
        )

        val result = executor.execute(ActionType.AUTO_ROTATE_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("WRITE_SETTINGS")
        assertThat(writeAttempted).isFalse()
    }

    @Test
    fun execute_autoRotateOn_writesOneAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenKey: String? = null
        var writtenVal: Int? = null
        var currentSetting = 0

        val executor = AutoRotateExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, key, value ->
                writtenKey = key
                writtenVal = value
                currentSetting = value
                true
            },
            settingsReader = { _, _, _ -> currentSetting },
        )

        val result = executor.execute(ActionType.AUTO_ROTATE_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Auto-rotate on")
        assertThat(writtenKey).isEqualTo("accelerometer_rotation")
        assertThat(writtenVal).isEqualTo(1)
    }

    @Test
    fun execute_autoRotateOff_writesZeroAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenKey: String? = null
        var writtenVal: Int? = null
        var currentSetting = 1

        val executor = AutoRotateExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, key, value ->
                writtenKey = key
                writtenVal = value
                currentSetting = value
                true
            },
            settingsReader = { _, _, _ -> currentSetting },
        )

        val result = executor.execute(ActionType.AUTO_ROTATE_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Auto-rotate off (portrait lock)")
        assertThat(writtenKey).isEqualTo("accelerometer_rotation")
        assertThat(writtenVal).isEqualTo(0)
    }

    @Test
    fun execute_whenWriterFails_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()

        val executor = AutoRotateExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, _, _ -> false },
            settingsReader = { _, _, defaultVal -> defaultVal },
        )

        val result = executor.execute(ActionType.AUTO_ROTATE_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Failed to write auto-rotate setting")
    }

    @Test
    fun execute_whenReadbackMismatches_returnsFailureHonestMismatch() {
        val context = RuntimeEnvironment.getApplication()

        val executor = AutoRotateExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, _, _ -> true },
            settingsReader = { _, _, _ -> 0 }, // always returns 0 even after writing 1
        )

        val result = executor.execute(ActionType.AUTO_ROTATE_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
    }

    @Test
    fun execute_whenExceptionThrown_returnsFailureWithMessage() {
        val context = RuntimeEnvironment.getApplication()

        val executor = AutoRotateExecutor(
            context = context,
            permissionChecker = { true },
            settingsWriter = { _, _, _ -> throw SecurityException("Mock system block") },
        )

        val result = executor.execute(ActionType.AUTO_ROTATE_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Mock system block")
    }
}
