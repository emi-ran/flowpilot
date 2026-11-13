package com.flowpilot.app.actions

import android.Manifest
import com.flowpilot.app.data.model.ActionExecutionRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.robolectric.Shadows.shadowOf
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
        assertThat(result.message).doesNotContain("permission denied")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED)
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
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.STATE_MISMATCH)
        assertThat(ActionExecutionRecord.create(ActionType.BLUETOOTH_ON, result).diagnostic?.reason)
            .isEqualTo(ActionDiagnosticReason.STATE_MISMATCH)
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
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING)
        assertThat(shell.commands).isEmpty()
    }

    @Test fun failures_are_preserved_in_history_without_shell_output() {
        val secret = "private-shell-output"
        val stopped = FakeShell(running = false)
        val denied = FakeShell(granted = false)
        val failed = FakeShell(results = mapOf("svc bluetooth enable" to (1 to secret)))
        val missing = FakeShell()
        val cases = listOf(
            executor(stopped, true).execute(ActionType.BLUETOOTH_ON) to ActionDiagnosticReason.SHIZUKU_NOT_RUNNING,
            executor(denied, true).execute(ActionType.BLUETOOTH_ON) to ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED,
            executor(failed, true).execute(ActionType.BLUETOOTH_ON) to ActionDiagnosticReason.SYSTEM_COMMAND_FAILED,
            BluetoothExecutor(RuntimeEnvironment.getApplication(), missing, enabledStateReader = { null })
                .execute(ActionType.BLUETOOTH_ON) to ActionDiagnosticReason.STATE_UNAVAILABLE,
        )
        cases.forEach { (result, reason) ->
            val record = ActionExecutionRecord.create(ActionType.BLUETOOTH_ON, result)
            assertThat(record.success).isFalse()
            assertThat(record.diagnostic?.reason).isEqualTo(reason)
            assertThat(Json.encodeToString(record)).doesNotContain(secret)
        }
        assertThat(stopped.commands).isEmpty()
        assertThat(denied.commands).isEmpty()
        assertThat(missing.commands).isEmpty()
        assertThat(failed.commands).containsExactly("svc bluetooth enable")
    }

    @Test @Config(sdk = [34]) fun bluetooth_permission_is_distinct_from_shizuku_service_failure() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val shell = FakeShell(running = false)
        val result = BluetoothExecutor(context, shell, enabledStateReader = { true }).execute(ActionType.BLUETOOTH_ON)
        val record = ActionExecutionRecord.create(ActionType.BLUETOOTH_ON, result)
        assertThat(record.success).isFalse()
        assertThat(record.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.BLUETOOTH_PERMISSION_REQUIRED)
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
