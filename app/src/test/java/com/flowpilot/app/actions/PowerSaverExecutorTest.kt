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

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun execute_whenWriteSecureSettingsGranted_writesSettingDirectlyAndVerifiesIt() {
        val shell = FakeShell()
        var writtenKey: String? = null
        var writtenVal: Int? = null
        var currentSetting = 0

        val executor = directExecutor(
            shell = shell,
            writer = { _, key, value ->
                writtenKey = key
                writtenVal = value
                currentSetting = value
                true
            },
            reader = { currentSetting },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Battery saver on")
        assertThat(shell.commands).isEmpty()
        assertThat(writtenKey).isEqualTo("low_power")
        assertThat(writtenVal).isEqualTo(1)
    }

    @Test
    fun execute_batterySaverOff_directWriteVerifiesOff() {
        var currentSetting = 1
        val executor = directExecutor(
            shell = FakeShell(),
            writer = { _, _, value ->
                currentSetting = value
                true
            },
            reader = { currentSetting },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Battery saver off")
        assertThat(currentSetting).isEqualTo(0)
    }

    @Test
    fun execute_whenDirectWriteReadbackStillOff_returnsMismatchFailure() {
        val executor = directExecutor(
            shell = FakeShell(),
            writer = { _, _, _ -> true },
            reader = { 0 },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.STATE_MISMATCH)
        assertThat(result.message).contains("expected on")
        assertThat(result.message).contains("read off")
        assertThat(result.message).isNotEqualTo("Battery saver on")
    }

    @Test
    fun execute_whenDirectWriteReadbackUnavailable_returnsUnverifiedFailure() {
        val executor = directExecutor(
            shell = FakeShell(),
            writer = { _, _, _ -> true },
            reader = { null },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("could not be verified")
        assertThat(result.message).contains("unavailable")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.STATE_UNAVAILABLE)
    }

    @Test
    fun execute_whenDirectWriterReportsFailure_returnsFailureWithoutClaimingSuccess() {
        var reads = 0
        val executor = directExecutor(
            shell = FakeShell(),
            writer = { _, _, _ -> false },
            reader = {
                reads += 1
                1
            },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Failed to write battery saver setting")
        assertThat(reads).isEqualTo(0)
    }

    @Test
    fun execute_whenDirectWriteReadbackLags_pollsUntilTarget() {
        var reads = 0
        val executor = directExecutor(
            shell = FakeShell(),
            writer = { _, _, _ -> true },
            reader = {
                reads += 1
                if (reads >= 3) 1 else 0
            },
            attempts = 4,
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Battery saver on")
        assertThat(reads).isEqualTo(3)
    }

    @Test
    fun execute_whenWriteSecureSettingsMissing_fallsBackToShizukuAndVerifiesReadback() {
        val shell = FakeShell(results = shizukuResults(target = 1))
        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { false },
            settingsReader = { 1 },
            waitMillis = {},
            statePollAttempts = 2,
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Battery saver on")
        assertThat(shell.commands).contains("cmd power set-mode 1")
    }

    @Test
    fun execute_whenShizukuCommandsSucceedButReadbackStaysOff_returnsMismatchFailure() {
        val shell = FakeShell(results = shizukuResults(target = 1))
        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { false },
            settingsReader = { 0 },
            waitMillis = {},
            statePollAttempts = 2,
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
        assertThat(result.message).isNotEqualTo("Battery saver on")
        assertThat(shell.commands).contains("cmd power set-mode 1")
    }

    @Test
    fun execute_whenShizukuReadbackUnavailable_returnsUnverifiedFailure() {
        val shell = FakeShell(results = shizukuResults(target = 0))
        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { false },
            settingsReader = { null },
            waitMillis = {},
            statePollAttempts = 2,
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_OFF)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("could not be verified")
        assertThat(result.message).isNotEqualTo("Battery saver off")
    }

    @Test
    fun execute_whenNeitherPermissionNorShizukuAvailable_returnsFailure() {
        val shell = FakeShell(running = false, permitted = false)
        val executor = PowerSaverExecutor(
            context = context,
            shell = shell,
            permissionChecker = { false },
        )

        val result = executor.execute(ActionType.BATTERY_SAVER_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("needs WRITE_SECURE_SETTINGS or Shizuku")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING)
        assertThat(shell.commands).isEmpty()
    }

    private fun directExecutor(
        shell: ShizukuShellCompatible,
        writer: (ContentResolver, String, Int) -> Boolean,
        reader: (ContentResolver) -> Int?,
        attempts: Int = 2,
    ) = PowerSaverExecutor(
        context = context,
        shell = shell,
        permissionChecker = { true },
        settingsWriter = writer,
        settingsReader = reader,
        waitMillis = {},
        statePollAttempts = attempts,
    )

    private fun shizukuResults(target: Int): Map<String, Pair<Int, String>> = mapOf(
        "cmd power set-mode $target" to (0 to ""),
        "settings put system POWER_SAVE_MODE_OPEN $target" to (0 to ""),
        "settings put global low_power $target" to (0 to ""),
    )
}
