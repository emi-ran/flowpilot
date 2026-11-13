package com.flowpilot.app.actions

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Airplane Mode through shell-owned `cmd connectivity airplane-mode enable|disable` via Shizuku.
 * Third-party applications cannot change Airplane Mode without signature/privileged permissions.
 * State is read back via Settings.Global.AIRPLANE_MODE_ON; returns honest error on failure or mismatch.
 */
class AirplaneModeExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible,
    private val airplaneStateReader: () -> Boolean? = {
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Throwable) {
            null
        }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ActionExecutor {

    override val supportedTypes = setOf(ActionType.AIRPLANE_MODE_ON, ActionType.AIRPLANE_MODE_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (command, expectedEnabled, label) = when (action) {
            ActionType.AIRPLANE_MODE_ON -> Triple("cmd connectivity airplane-mode enable", true, "on")
            ActionType.AIRPLANE_MODE_OFF -> Triple("cmd connectivity airplane-mode disable", false, "off")
            else -> return ActionResult(false, "Unsupported action for Airplane mode")
        }

        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Airplane mode can't be toggled", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        }

        return try {
            val (code, _) = shell.run(command)
            if (code != 0) {
                return ActionResult(false, "Airplane mode toggle failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
            }

            val enabled = awaitExpectedState(expectedEnabled)
            if (enabled != null && enabled != expectedEnabled) {
                return ActionResult(false, "Airplane mode state mismatch: expected ${if (expectedEnabled) "on" else "off"} but read ${if (enabled) "on" else "off"}", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_MISMATCH))
            }
            ActionResult(true, "Airplane mode turned $label")
        } catch (_: Throwable) {
            ActionResult(false, "Airplane mode toggle error")
        }
    }

    private fun awaitExpectedState(expected: Boolean, timeoutMs: Long = 4_000L): Boolean? {
        val deadline = elapsedRealtimeMillis() + timeoutMs
        var latest: Boolean? = null
        while (elapsedRealtimeMillis() <= deadline) {
            latest = airplaneStateReader()
            if (latest == expected) return latest
            waitMillis(100L)
        }
        return latest
    }
}
