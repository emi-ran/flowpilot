package com.flowpilot.app.actions

import android.os.Build
import com.flowpilot.app.data.model.ActionType

/**
 * Turns the Wi-Fi hotspot on or off through [HotspotBridge], executed by Shizuku as the shell UID.
 *
 * Neither a normal app nor a Shizuku UserService can start Wi-Fi tethering as its own package,
 * and `cmd wifi` is rejected with SecurityException, so the bridge runs under app_process and
 * identifies itself as com.android.shell. Success is reported only when the bridge exits 0.
 */
class HotspotExecutor(
    private val shell: ShizukuShellCompatible = ShizukuShell.instance,
    private val sdkInt: () -> Int = { Build.VERSION.SDK_INT },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.HOTSPOT_ON, ActionType.HOTSPOT_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val enabled = when (action) {
            ActionType.HOTSPOT_ON -> true
            ActionType.HOTSPOT_OFF -> false
            else -> return ActionResult(false, "Unsupported action for Hotspot")
        }
        val label = if (enabled) "on" else "off"

        if (sdkInt() < Build.VERSION_CODES.BAKLAVA) {
            return ActionResult(false, "Hotspot control requires Android 16 or newer")
        }
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Hotspot can't be toggled", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        }

        return try {
            val (code, _) = shell.runHotspot(enabled)
            if (code != 0) {
                return ActionResult(false, "Hotspot $label failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
            }
            ActionResult(
                true,
                "Hotspot turned $label",
                if (enabled) ActionResultCode.STATE_ENABLED else ActionResultCode.STATE_DISABLED,
            )
        } catch (_: Throwable) {
            ActionResult(false, "Hotspot toggle error")
        }
    }
}
