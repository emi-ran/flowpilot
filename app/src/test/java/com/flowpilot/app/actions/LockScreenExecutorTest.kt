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
class LockScreenExecutorTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun execute_whenScreenBecomesNonInteractive_reportsScreenLocked() {
        val shell = FakeShell(results = mapOf(KEY_EVENT to (0 to "")))

        val result = executor(shell, screenInteractiveProvider = { false })
            .execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Screen locked")
        assertThat(shell.commands).containsExactly("input keyevent 26")
    }

    @Test
    fun execute_pollsUntilScreenBecomesNonInteractive() {
        val shell = FakeShell(results = mapOf(KEY_EVENT to (0 to "")))
        var reads = 0

        val result = executor(
            shell = shell,
            screenInteractiveProvider = {
                reads += 1
                reads < 3
            },
            attempts = 4,
        ).execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Screen locked")
        assertThat(reads).isEqualTo(3)
    }

    @Test
    fun execute_whenScreenStaysInteractive_returnsFailure() {
        val shell = FakeShell(results = mapOf(KEY_EVENT to (0 to "")))

        val result = executor(shell, screenInteractiveProvider = { true })
            .execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("still interactive")
        assertThat(result.message).isNotEqualTo("Screen locked")
    }

    @Test
    fun execute_whenScreenStateUnavailable_reportsAcceptedButNotCompleted() {
        val shell = FakeShell(results = mapOf(KEY_EVENT to (0 to "")))

        val result = executor(shell, screenInteractiveProvider = { null })
            .execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Screen lock command accepted (completion not verified)")
        assertThat(shell.commands).containsExactly("input keyevent 26")
    }

    @Test
    fun execute_whenShellCommandFails_returnsFailureWithoutAdvertisingCompletion() {
        val shell = FakeShell(results = mapOf(KEY_EVENT to (1 to "denied")))
        var reads = 0

        val result = executor(
            shell = shell,
            screenInteractiveProvider = {
                reads += 1
                false
            },
        ).execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Failed to lock screen")
        assertThat(result.message).isNotEqualTo("Screen locked")
        assertThat(reads).isEqualTo(0)
    }

    @Test
    fun execute_whenShizukuNotRunning_returnsFailureWithoutRunningCommand() {
        val shell = FakeShell(running = false)

        val result = executor(shell, screenInteractiveProvider = { false })
            .execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("service not running")
        assertThat(shell.commands).isEmpty()
    }

    @Test
    fun execute_whenShizukuPermissionMissing_returnsFailureWithoutRunningCommand() {
        val shell = FakeShell(permitted = false)

        val result = executor(shell, screenInteractiveProvider = { false })
            .execute(ActionType.LOCK_SCREEN)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku permission")
        assertThat(shell.commands).isEmpty()
    }

    @Test
    fun execute_unsupportedAction_returnsFailureWithoutRunningCommand() {
        val shell = FakeShell(results = mapOf(KEY_EVENT to (0 to "")))

        val result = executor(shell, screenInteractiveProvider = { false })
            .execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for LockScreenExecutor")
        assertThat(shell.commands).isEmpty()
    }

    private fun executor(
        shell: ShizukuShellCompatible,
        screenInteractiveProvider: () -> Boolean?,
        attempts: Int = 2,
    ) = LockScreenExecutor(
        context = context,
        shizukuShell = shell,
        screenInteractiveProvider = screenInteractiveProvider,
        waitMillis = {},
        statePollAttempts = attempts,
    )

    private companion object {
        const val KEY_EVENT = "input keyevent 26"
    }
}
