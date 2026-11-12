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
            return ActionResult(false, "Wi-Fi is unsupported on this device", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
        }
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Wi-Fi can't be toggled", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        }

        return try {
            val (code) = shell.run(command)
            if (code != 0) {
                return ActionResult(false, "Wi-Fi toggle failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
            }

            val enabled = awaitExpectedState(expectedEnabled)
                ?: return ActionResult(false, "Wi-Fi state became unavailable", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
            if (enabled != expectedEnabled) {
                return ActionResult(false, "Wi-Fi state mismatch: expected ${if (expectedEnabled) "on" else "off"} but read ${if (enabled) "on" else "off"}", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_MISMATCH))
            }
            ActionResult(true, "Wi-Fi turned $label")
        } catch (_: Throwable) {
            ActionResult(false, "Wi-Fi toggle error")
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
