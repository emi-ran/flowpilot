package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AirplaneModeExecutorTest {

    private fun executor(
        shell: ShizukuShellCompatible,
        enabled: Boolean,
    ): AirplaneModeExecutor = AirplaneModeExecutor(
        context = RuntimeEnvironment.getApplication(),
        shell = shell,
        airplaneStateReader = { enabled },
        waitMillis = {},
        elapsedRealtimeMillis = { 0L },
    )

    @Test
    fun on_runs_cmd_connectivity_airplane_mode_enable() {
        val shell = FakeShell(results = mapOf("cmd connectivity airplane-mode enable" to (0 to "")))
        val result = executor(shell, enabled = true).execute(ActionType.AIRPLANE_MODE_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Airplane mode turned on")
        assertThat(shell.commands).containsExactly("cmd connectivity airplane-mode enable")
    }

    @Test
    fun off_runs_cmd_connectivity_airplane_mode_disable() {
        val shell = FakeShell(results = mapOf("cmd connectivity airplane-mode disable" to (0 to "")))
        val result = executor(shell, enabled = false).execute(ActionType.AIRPLANE_MODE_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Airplane mode turned off")
        assertThat(shell.commands).containsExactly("cmd connectivity airplane-mode disable")
    }

    @Test
    fun command_failure_reports_error() {
        val shell = FakeShell(results = mapOf("cmd connectivity airplane-mode enable" to (1 to "error")))
        val result = executor(shell, enabled = true).execute(ActionType.AIRPLANE_MODE_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Airplane mode toggle failed")
    }

    @Test
    fun shizuku_not_permitted_fails() {
        val shell = FakeShell(running = true, permitted = false)
        val result = executor(shell, enabled = true).execute(ActionType.AIRPLANE_MODE_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku permission not granted")
    }
}
