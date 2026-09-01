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
class BluetoothExecutorTest {
    @Test fun on_runs_only_allowed_bluetooth_command_and_verifies_enabled_state() {
        val shell = FakeShell(results = mapOf("svc bluetooth enable" to (0 to "")))
        val result = executor(shell, enabled = true).execute(ActionType.BLUETOOTH_ON)

        assertThat(result.success).isTrue()
        assertThat(shell.commands).containsExactly("svc bluetooth enable")
    }

    @Test fun command_failure_does_not_claim_success_or_read_state() {
        val shell = FakeShell(results = mapOf("svc bluetooth disable" to (1 to "permission denied")))
        val result = executor(shell, enabled = false).execute(ActionType.BLUETOOTH_OFF)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Bluetooth toggle failed")
        assertThat(shell.commands).containsExactly("svc bluetooth disable")
    }

    @Test fun successful_command_with_state_mismatch_reports_failure() {
        val shell = FakeShell(results = mapOf("svc bluetooth enable" to (0 to "")))
        var elapsed = 0L
        val result = BluetoothExecutor(
            context = RuntimeEnvironment.getApplication(),
            shell = shell,
            enabledStateReader = { false },
            waitMillis = { elapsed += it },
            elapsedRealtimeMillis = { elapsed },
        ).execute(ActionType.BLUETOOTH_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Bluetooth state mismatch")
    }

    @Test fun delayed_state_change_succeeds_before_poll_timeout() {
        val shell = FakeShell(results = mapOf("svc bluetooth enable" to (0 to "")))
        var reads = 0
        var elapsed = 0L
        val result = BluetoothExecutor(
            context = RuntimeEnvironment.getApplication(),
            shell = shell,
            enabledStateReader = { ++reads >= 4 },
            waitMillis = { elapsed += it },
            elapsedRealtimeMillis = { elapsed },
        ).execute(ActionType.BLUETOOTH_ON)

        assertThat(result.success).isTrue()
        assertThat(reads).isEqualTo(4)
        assertThat(elapsed).isEqualTo(200L)
    }

    @Test fun state_mismatch_times_out_after_five_seconds() {
        val shell = FakeShell(results = mapOf("svc bluetooth enable" to (0 to "")))
        var elapsed = 0L
        val result = BluetoothExecutor(
            context = RuntimeEnvironment.getApplication(),
            shell = shell,
            enabledStateReader = { false },
            waitMillis = { elapsed += it },
            elapsedRealtimeMillis = { elapsed },
        ).execute(ActionType.BLUETOOTH_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Bluetooth state mismatch")
        assertThat(elapsed).isEqualTo(5_000L)
    }

    @Test fun unavailable_shizuku_does_not_run_command() {
        val shell = FakeShell(running = false)
        val result = executor(shell, enabled = true).execute(ActionType.BLUETOOTH_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku not running")
        assertThat(shell.commands).isEmpty()
    }

    private fun executor(shell: FakeShell, enabled: Boolean): BluetoothExecutor = BluetoothExecutor(
        context = RuntimeEnvironment.getApplication(),
        shell = shell,
        enabledStateReader = { enabled },
    )

    private class FakeShell(
        private val running: Boolean = true,
        private val granted: Boolean = true,
        private val results: Map<String, Pair<Int, String>> = emptyMap(),
    ) : ShizukuShellCompatible {
        val commands = mutableListOf<String>()
        override fun isShizukuRunning() = running
        override fun hasPermission() = granted
        override fun run(command: String): Pair<Int, String> {
            commands += command
            return results[command] ?: (-1 to "Unexpected command")
        }
    }
}
