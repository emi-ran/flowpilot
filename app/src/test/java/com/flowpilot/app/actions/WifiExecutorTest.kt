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
class WifiExecutorTest {

    private fun executor(
        shell: ShizukuShellCompatible,
        enabled: Boolean,
    ): WifiExecutor = WifiExecutor(
        context = RuntimeEnvironment.getApplication(),
        shell = shell,
        enabledStateReader = { enabled },
        waitMillis = {},
        elapsedRealtimeMillis = { 0L },
    )

    @Test
    fun on_runs_svc_wifi_enable_and_verifies_state() {
        val shell = FakeShell(results = mapOf("svc wifi enable" to (0 to "")))
        val result = executor(shell, enabled = true).execute(ActionType.WIFI_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Wi-Fi turned on")
        assertThat(shell.commands).containsExactly("svc wifi enable")
    }

    @Test
    fun off_runs_svc_wifi_disable_and_verifies_state() {
        val shell = FakeShell(results = mapOf("svc wifi disable" to (0 to "")))
        val result = executor(shell, enabled = false).execute(ActionType.WIFI_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Wi-Fi turned off")
        assertThat(shell.commands).containsExactly("svc wifi disable")
    }

    @Test
    fun command_failure_reports_error() {
        val shell = FakeShell(results = mapOf("svc wifi enable" to (1 to "error")))
        val result = executor(shell, enabled = true).execute(ActionType.WIFI_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Wi-Fi toggle failed")
    }

    @Test
    fun shizuku_not_running_fails_honestly() {
        val shell = FakeShell(running = false)
        val result = executor(shell, enabled = true).execute(ActionType.WIFI_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku not running")
    }

    @Test
    fun shizuku_permission_not_granted_fails_honestly() {
        val shell = FakeShell(running = true, permitted = false)
        val result = executor(shell, enabled = true).execute(ActionType.WIFI_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku permission not granted")
    }

    @Test
    fun state_mismatch_reports_failure() {
        val shell = FakeShell(results = mapOf("svc wifi enable" to (0 to "")))
        var elapsed = 0L
        val result = WifiExecutor(
            context = RuntimeEnvironment.getApplication(),
            shell = shell,
            enabledStateReader = { false }, // Remains false despite enable command
            waitMillis = { elapsed += it },
            elapsedRealtimeMillis = { elapsed },
        ).execute(ActionType.WIFI_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Wi-Fi state mismatch")
    }
}
