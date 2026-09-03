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
class MobileDataExecutorTest {

    private fun executor(
        shell: ShizukuShellCompatible,
        enabled: Boolean,
    ): MobileDataExecutor = MobileDataExecutor(
        context = RuntimeEnvironment.getApplication(),
        shell = shell,
        enabledStateReader = { enabled },
        waitMillis = {},
        elapsedRealtimeMillis = { 0L },
    )

    @Test
    fun on_runs_svc_data_enable_and_verifies_state() {
        val shell = FakeShell(results = mapOf("svc data enable" to (0 to "")))
        val result = executor(shell, enabled = true).execute(ActionType.MOBILE_DATA_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Mobile Data turned on")
        assertThat(shell.commands).containsExactly("svc data enable")
    }

    @Test
    fun off_runs_svc_data_disable_and_verifies_state() {
        val shell = FakeShell(results = mapOf("svc data disable" to (0 to "")))
        val result = executor(shell, enabled = false).execute(ActionType.MOBILE_DATA_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Mobile Data turned off")
        assertThat(shell.commands).containsExactly("svc data disable")
    }

    @Test
    fun command_failure_reports_error() {
        val shell = FakeShell(results = mapOf("svc data enable" to (1 to "error")))
        val result = executor(shell, enabled = true).execute(ActionType.MOBILE_DATA_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Mobile Data toggle failed")
    }

    @Test
    fun shizuku_not_running_fails_honestly() {
        val shell = FakeShell(running = false)
        val result = executor(shell, enabled = true).execute(ActionType.MOBILE_DATA_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku not running")
    }

    @Test
    fun state_mismatch_reports_failure() {
        val shell = FakeShell(results = mapOf("svc data enable" to (0 to "")))
        var elapsed = 0L
        val result = MobileDataExecutor(
            context = RuntimeEnvironment.getApplication(),
            shell = shell,
            enabledStateReader = { false },
            waitMillis = { elapsed += it },
            elapsedRealtimeMillis = { elapsed },
        ).execute(ActionType.MOBILE_DATA_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Mobile Data state mismatch")
    }
}
