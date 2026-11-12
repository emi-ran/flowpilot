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
class NfcExecutorTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun nfc_on_uses_svc_nfc_enable_and_verifies_enabled_state() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (0 to "")))

        val result = executor(shell, nfcEnabledProvider = { true })
            .execute(ActionType.NFC_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("NFC turned on")
        assertThat(shell.commands).containsExactly("svc nfc enable")
    }

    @Test
    fun nfc_off_uses_svc_nfc_disable_and_verifies_disabled_state() {
        val shell = FakeShell(results = mapOf("svc nfc disable" to (0 to "")))

        val result = executor(shell, nfcEnabledProvider = { false })
            .execute(ActionType.NFC_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("NFC turned off")
        assertThat(shell.commands).containsExactly("svc nfc disable")
    }

    @Test
    fun nfc_on_whenShellSucceedsButAdapterStaysOff_returnsReadbackMismatch() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (0 to "")))

        val result = executor(shell, nfcEnabledProvider = { false })
            .execute(ActionType.NFC_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.STATE_MISMATCH)
        assertThat(result.message).contains("expected on")
        assertThat(result.message).contains("read off")
        assertThat(result.message).isNotEqualTo("NFC turned on")
    }

    @Test
    fun nfc_off_whenShellSucceedsButAdapterStaysOn_returnsReadbackMismatch() {
        val shell = FakeShell(results = mapOf("svc nfc disable" to (0 to "")))

        val result = executor(shell, nfcEnabledProvider = { true })
            .execute(ActionType.NFC_OFF)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
        assertThat(result.message).isNotEqualTo("NFC turned off")
    }

    @Test
    fun nfc_on_whenAdapterStateUnavailable_returnsUnverifiedFailure() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (0 to "")))

        val result = executor(shell, nfcEnabledProvider = { null })
            .execute(ActionType.NFC_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("could not be verified")
        assertThat(result.message).contains("unavailable")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.STATE_UNAVAILABLE)
    }

    @Test
    fun nfc_on_pollsUntilAdapterReportsExpectedState() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (0 to "")))
        var reads = 0

        val result = executor(
            shell = shell,
            nfcEnabledProvider = {
                reads += 1
                reads >= 3
            },
            attempts = 4,
        ).execute(ActionType.NFC_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("NFC turned on")
        assertThat(reads).isEqualTo(3)
    }

    @Test
    fun nfc_on_does_not_reportSuccess_whenSvcCommandFails() {
        val shell = FakeShell(results = mapOf("svc nfc enable" to (1 to "Unknown service: nfc")))

        val result = executor(shell, nfcEnabledProvider = { true })
            .execute(ActionType.NFC_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("NFC toggle failed")
        assertThat(result.message).doesNotContain("Unknown service: nfc")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED)
        assertThat(shell.commands).containsExactly("svc nfc enable")
    }

    @Test
    fun nfc_unsupportedAction_returnsFailureWithoutRunningShell() {
        val shell = FakeShell()

        val result = executor(shell, nfcEnabledProvider = { true })
            .execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for NFC")
        assertThat(shell.commands).isEmpty()
    }

    private fun executor(
        shell: ShizukuShellCompatible,
        nfcEnabledProvider: () -> Boolean?,
        attempts: Int = 2,
    ) = NfcExecutor(
        context = context,
        shell = shell,
        nfcEnabledProvider = nfcEnabledProvider,
        waitMillis = {},
        statePollAttempts = attempts,
    )
}
