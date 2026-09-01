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
class DarkThemeExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(isShizukuRunning = true, hasPermission = true)
        val executor = DarkThemeExecutor(context = context, shell = shell)

        val result = executor.execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for dark theme")
        assertThat(shell.commands).isEmpty()
    }

    @Test
    fun execute_whenShizukuNotRunning_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(isShizukuRunning = false, hasPermission = true)
        val executor = DarkThemeExecutor(context = context, shell = shell)

        val result = executor.execute(ActionType.DARK_THEME_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku not running")
        assertThat(shell.commands).isEmpty()
    }

    @Test
    fun execute_whenShizukuPermissionNotGranted_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(isShizukuRunning = true, hasPermission = false)
        val executor = DarkThemeExecutor(context = context, shell = shell)

        val result = executor.execute(ActionType.DARK_THEME_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku permission not granted")
        assertThat(shell.commands).isEmpty()
    }

    @Test
    fun execute_darkThemeOn_success_runsCommandAndVerifiesNightMode2() {
        val context = RuntimeEnvironment.getApplication()
        var currentNightMode = 1
        val shell = FakeShell(
            isShizukuRunning = true,
            hasPermission = true,
            results = mapOf("cmd uimode night yes" to (0 to "")),
            onRun = { if (it == "cmd uimode night yes") currentNightMode = 2 },
        )
        val executor = DarkThemeExecutor(
            context = context,
            shell = shell,
            settingsReader = { _, _, _ -> currentNightMode },
        )

        val result = executor.execute(ActionType.DARK_THEME_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Dark theme on")
        assertThat(shell.commands).containsExactly("cmd uimode night yes")
    }

    @Test
    fun execute_darkThemeOff_success_runsCommandAndVerifiesNightMode1() {
        val context = RuntimeEnvironment.getApplication()
        var currentNightMode = 2
        val shell = FakeShell(
            isShizukuRunning = true,
            hasPermission = true,
            results = mapOf("cmd uimode night no" to (0 to "")),
            onRun = { if (it == "cmd uimode night no") currentNightMode = 1 },
        )
        val executor = DarkThemeExecutor(
            context = context,
            shell = shell,
            settingsReader = { _, _, _ -> currentNightMode },
        )

        val result = executor.execute(ActionType.DARK_THEME_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Dark theme off")
        assertThat(shell.commands).containsExactly("cmd uimode night no")
    }

    @Test
    fun execute_whenShellFails_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(
            isShizukuRunning = true,
            hasPermission = true,
            results = mapOf("cmd uimode night yes" to (1 to "Error executing command")),
        )
        val executor = DarkThemeExecutor(
            context = context,
            shell = shell,
            settingsReader = { _, _, _ -> 2 },
        )

        val result = executor.execute(ActionType.DARK_THEME_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Dark theme toggle failed: Error executing command")
        assertThat(shell.commands).containsExactly("cmd uimode night yes")
    }

    @Test
    fun execute_whenStateMismatch_returnsFailureHonestMismatch() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(
            isShizukuRunning = true,
            hasPermission = true,
            results = mapOf("cmd uimode night yes" to (0 to "")),
        )
        val executor = DarkThemeExecutor(
            context = context,
            shell = shell,
            settingsReader = { _, _, _ -> 1 }, // State remains 1 despite command running
        )

        val result = executor.execute(ActionType.DARK_THEME_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Dark theme state mismatch: expected 2 but read 1")
    }

    @Test
    fun execute_whenShellThrows_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val shell = FakeShell(
            isShizukuRunning = true,
            hasPermission = true,
            onRun = { throw RuntimeException("DeadObjectException") },
        )
        val executor = DarkThemeExecutor(
            context = context,
            shell = shell,
        )

        val result = executor.execute(ActionType.DARK_THEME_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("DeadObjectException")
    }

    private class FakeShell(
        private val isShizukuRunning: Boolean = true,
        private val hasPermission: Boolean = true,
        private val results: Map<String, Pair<Int, String>> = emptyMap(),
        private val onRun: (String) -> Unit = {},
    ) : ShizukuShellCompatible {
        val commands = mutableListOf<String>()

        override fun isShizukuRunning() = isShizukuRunning
        override fun hasPermission() = hasPermission

        override fun run(command: String): Pair<Int, String> {
            commands += command
            onRun(command)
            return results[command] ?: (0 to "")
        }
    }
}
