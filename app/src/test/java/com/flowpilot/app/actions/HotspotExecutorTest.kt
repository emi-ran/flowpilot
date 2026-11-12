package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HotspotExecutorTest {

    private class FakeHotspotShell(
        private val running: Boolean = true,
        private val permitted: Boolean = true,
        private val bridgeResult: Pair<Int, String> = 0 to "started",
    ) : ShizukuShellCompatible {
        val hotspotRequests = mutableListOf<Boolean>()

        override fun isShizukuRunning(): Boolean = running

        override fun hasPermission(): Boolean = permitted

        override fun run(command: String): Pair<Int, String> = -1 to "Unexpected command: $command"

        override fun runHotspot(enabled: Boolean): Pair<Int, String> {
            hotspotRequests += enabled
            return bridgeResult
        }
    }

    private fun executor(shell: ShizukuShellCompatible, sdkInt: Int = 36): HotspotExecutor =
        HotspotExecutor(shell = shell, sdkInt = { sdkInt })

    @Test
    fun on_runs_bridge_with_enabled_true() {
        val shell = FakeHotspotShell(bridgeResult = 0 to "started")
        val result = executor(shell).execute(ActionType.HOTSPOT_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Hotspot turned on")
        assertThat(result.resultCode).isEqualTo(ActionResultCode.STATE_ENABLED)
        assertThat(shell.hotspotRequests).containsExactly(true)
    }

    @Test
    fun off_runs_bridge_with_enabled_false() {
        val shell = FakeHotspotShell(bridgeResult = 0 to "stopped")
        val result = executor(shell).execute(ActionType.HOTSPOT_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Hotspot turned off")
        assertThat(result.resultCode).isEqualTo(ActionResultCode.STATE_DISABLED)
        assertThat(shell.hotspotRequests).containsExactly(false)
    }

    @Test
    fun bridge_failure_is_reported_as_failure() {
        val shell = FakeHotspotShell(bridgeResult = 1 to "sensitive bridge output")
        val result = executor(shell).execute(ActionType.HOTSPOT_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Hotspot on failed")
        assertThat(result.toString()).doesNotContain("sensitive bridge output")
        assertThat(result.resultCode).isNull()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED)
        assertThat(shell.hotspotRequests).containsExactly(true)
    }

    @Test
    fun bridge_exception_is_reported_as_failure() {
        val shell = object : ShizukuShellCompatible {
            override fun isShizukuRunning(): Boolean = true
            override fun hasPermission(): Boolean = true
            override fun run(command: String): Pair<Int, String> = 0 to ""
            override fun runHotspot(enabled: Boolean): Pair<Int, String> =
                throw IllegalStateException("sensitive binder detail")
        }

        val result = executor(shell).execute(ActionType.HOTSPOT_OFF)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Hotspot toggle error")
        assertThat(result.toString()).doesNotContain("sensitive binder detail")
        assertThat(result.resultCode).isNull()
        assertThat(result.diagnostic).isNull()
    }

    @Test
    fun other_actions_are_rejected() {
        val shell = FakeHotspotShell()
        val result = executor(shell).execute(ActionType.WIFI_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for Hotspot")
        assertThat(shell.hotspotRequests).isEmpty()
    }

    @Test
    fun sdk_below_36_fails_without_running_bridge() {
        val shell = FakeHotspotShell()
        val result = executor(shell, sdkInt = 35).execute(ActionType.HOTSPOT_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Android 16")
        assertThat(shell.hotspotRequests).isEmpty()
    }

    @Test
    fun shizuku_not_running_fails_without_running_bridge() {
        val shell = FakeHotspotShell(running = false)
        val result = executor(shell).execute(ActionType.HOTSPOT_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku not running")
        assertThat(shell.hotspotRequests).isEmpty()
    }

    @Test
    fun missing_shizuku_permission_fails_without_running_bridge() {
        val shell = FakeHotspotShell(permitted = false)
        val result = executor(shell).execute(ActionType.HOTSPOT_OFF)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Shizuku permission not granted")
        assertThat(shell.hotspotRequests).isEmpty()
    }
}
