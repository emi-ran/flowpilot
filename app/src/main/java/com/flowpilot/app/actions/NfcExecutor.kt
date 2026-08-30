package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType

/**
 * Toggles NFC via Shizuku (`cmd nfc on/off`). Normal apps cannot toggle NFC on
 * Android 10+; this is the honest root-free path. If Shizuku is unavailable the
 * call reports failure rather than pretending it switched NFC.
 */
class NfcExecutor(private val shell: ShizukuShellCompatible) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.NFC_ON, ActionType.NFC_OFF)

    override fun execute(action: ActionType): ActionResult {
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — NFC can't be toggled")
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot")
        }
        val (svcCmd, cmdNfc) = when (action) {
            ActionType.NFC_ON -> ("svc nfc enable" to "cmd nfc on")
            ActionType.NFC_OFF -> ("svc nfc disable" to "cmd nfc off")
            else -> return ActionResult(false, "Unsupported action for NFC")
        }
        return try {
            val (code1, out1) = shell.run(svcCmd)
            if (code1 == 0) {
                return ActionResult(true, "NFC turned ${if (action == ActionType.NFC_ON) "on" else "off"}")
            }
            val (code2, out2) = shell.run(cmdNfc)
            if (code2 == 0) {
                return ActionResult(true, "NFC turned ${if (action == ActionType.NFC_ON) "on" else "off"}")
            }
            ActionResult(false, "NFC toggle failed ($out1; $out2)")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
