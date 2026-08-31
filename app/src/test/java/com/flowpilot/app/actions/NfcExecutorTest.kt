package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NfcExecutorTest {
    @Test fun nfc_on_uses_svc_nfc_enable() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (0 to "")))

        val result = NfcExecutor(shell).execute(ActionType.NFC_ON)

        assertThat(result.success).isTrue()
        assertThat(shell.commands).containsExactly("svc nfc enable")
    }

    @Test fun nfc_off_uses_svc_nfc_disable() {
        val shell = FakeShell(results = mapOf("svc nfc disable" to (0 to "")))

        val result = NfcExecutor(shell).execute(ActionType.NFC_OFF)

        assertThat(result.success).isTrue()
        assertThat(shell.commands).containsExactly("svc nfc disable")
    }

    @Test fun nfc_on_does_not_run_cmd_nfc_fallback_when_svc_fails() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (1 to "Unknown service: nfc")))

        val result = NfcExecutor(shell).execute(ActionType.NFC_ON)

        assertThat(result.success).isFalse()
        assertThat(shell.commands).containsExactly("svc nfc enable")
    }

    private class FakeShell(
        private val results: Map<String, Pair<Int, String>>,
    ) : ShizukuShellCompatible {
        val commands = mutableListOf<String>()

        override fun isShizukuRunning() = true
        override fun hasPermission() = true

        override fun run(command: String): Pair<Int, String> {
            commands += command
            return results[command] ?: (-1 to "Unexpected command")
        }
    }
}
