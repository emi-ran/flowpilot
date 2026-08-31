package com.flowpilot.app.actions

import android.content.ContentResolver
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
class PowerSaverExecutorTest {

    @Test
    fun execute_whenWriteSecureSettingsGranted_writesSettingDirectlyWithoutShell() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(isShizukuRunning = true, hasPermission = true)
        var writtenKey: String? = null
        var writtenVal: Int? = null

        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { true },
            settingsWriter = { _, key, value ->
                writtenKey = key
                writtenVal = value
                true
            },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON, "", "")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Battery saver on")
        assertThat(shell.commands).isEmpty()
        assertThat(writtenKey).isEqualTo("low_power")
        assertThat(writtenVal).isEqualTo(1)
    }

    @Test
    fun execute_whenWriteSecureSettingsMissing_fallsBackToShizuku() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(
            isShizukuRunning = true,
            hasPermission = true,
            results = mapOf(
                "cmd power set-mode 1" to (0 to ""),
                "settings put system POWER_SAVE_MODE_OPEN 1" to (0 to ""),
                "settings put global low_power 1" to (0 to ""),
            ),
        )
        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { false },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON, "", "")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Battery saver on")
        assertThat(shell.commands).contains("cmd power set-mode 1")
    }

    @Test
    fun execute_whenNeitherPermissionNorShizukuAvailable_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(isShizukuRunning = false, hasPermission = false)
        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { false },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON, "", "")

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("needs WRITE_SECURE_SETTINGS or Shizuku")
        assertThat(shell.commands).isEmpty()
    }

    private class FakeShell(
        private val isShizukuRunning: Boolean = true,
        private val hasPermission: Boolean = true,
        private val results: Map<String, Pair<Int, String>> = emptyMap(),
    ) : ShizukuShellCompatible {
        val commands = mutableListOf<String>()

        override fun isShizukuRunning() = isShizukuRunning
        override fun hasPermission() = hasPermission

        override fun run(command: String): Pair<Int, String> {
            commands += command
            return results[command] ?: (0 to "")
        }
    }
}
