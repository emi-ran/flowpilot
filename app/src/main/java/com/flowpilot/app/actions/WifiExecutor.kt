package com.flowpilot.app.actions

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Wi-Fi radio through shell-owned `svc wifi enable|disable` via Shizuku.
 * On modern Android, normal third-party apps cannot toggle Wi-Fi via WifiManager.setWifiEnabled.
 * Verification polls WifiManager.isWifiEnabled; honest error returned if state does not match.
 */
class WifiExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible,
    private val wifiManagerProvider: (Context) -> WifiManager? = { appContext ->
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    },
    private val enabledReader: (WifiManager) -> Boolean = { it.isWifiEnabled },
    private val enabledStateReader: () -> Boolean? = {
        try { wifiManagerProvider(context)?.let(enabledReader) } catch (_: Throwable) { null }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ActionExecutor {

    override val supportedTypes = setOf(ActionType.WIFI_ON, ActionType.WIFI_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (command, expectedEnabled, label) = when (action) {
            ActionType.WIFI_ON -> Triple("svc wifi enable", true, "on")
            ActionType.WIFI_OFF -> Triple("svc wifi disable", false, "off")
            else -> return ActionResult(false, "Unsupported action for Wi-Fi")
        }

        if (enabledStateReader() == null) {
            return ActionResult(false, "Wi-Fi is unsupported on this device")
        }
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Wi-Fi can't be toggled")
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot")
        }

        return try {
            val (code, output) = shell.run(command)
            if (code != 0) {
                return ActionResult(false, "Wi-Fi toggle failed: $output")
            }

            val enabled = awaitExpectedState(expectedEnabled)
                ?: return ActionResult(false, "Wi-Fi state became unavailable")
            if (enabled != expectedEnabled) {
                return ActionResult(false, "Wi-Fi state mismatch: expected ${if (expectedEnabled) "on" else "off"} but read ${if (enabled) "on" else "off"}")
            }
            ActionResult(true, "Wi-Fi turned $label")
        } catch (t: Throwable) {
            ActionResult(false, "Wi-Fi toggle error: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun awaitExpectedState(expected: Boolean, timeoutMs: Long = 4_000L): Boolean? {
        val deadline = elapsedRealtimeMillis() + timeoutMs
        var latest: Boolean? = null
        while (elapsedRealtimeMillis() <= deadline) {
            latest = enabledStateReader()
            if (latest == expected) return latest
            waitMillis(100L)
        }
        return latest
    }
}
