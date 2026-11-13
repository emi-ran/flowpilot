package com.flowpilot.app.actions

import android.content.Context
import android.nfc.NfcManager
import com.flowpilot.app.data.model.ActionType

// 6 attempts × 200 ms: at most 1 s of polling before giving up.
private const val STATE_POLL_ATTEMPTS = 6
private const val STATE_POLL_INTERVAL_MS = 200L

/**
 * Toggles NFC via Shizuku (`cmd nfc on/off`). Normal apps cannot toggle NFC on
 * Android 10+; this is the honest root-free path. If Shizuku is unavailable the
 * call reports failure rather than pretending it switched NFC.
 *
 * A successful shell exit code is not treated as proof: the adapter state is
 * polled afterwards, and a mismatch or unavailable readback is a failure.
 */
class NfcExecutor(
    context: Context,
    private val shell: ShizukuShellCompatible,
    private val nfcEnabledProvider: () -> Boolean? = {
        try {
            (context.applicationContext.getSystemService(Context.NFC_SERVICE) as? NfcManager)
                ?.defaultAdapter?.isEnabled
        } catch (_: Throwable) {
            null
        }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val statePollAttempts: Int = STATE_POLL_ATTEMPTS,
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.NFC_ON, ActionType.NFC_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — NFC can't be toggled", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        }
        val svcCmd = when (action) {
            ActionType.NFC_ON -> "svc nfc enable"
            ActionType.NFC_OFF -> "svc nfc disable"
            else -> return ActionResult(false, "Unsupported action for NFC")
        }
        val expectedEnabled = action == ActionType.NFC_ON

        return try {
            val (code1) = shell.run(svcCmd)
            if (code1 != 0) {
                return ActionResult(false, "NFC toggle failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
            }

            val enabled = awaitExpectedState(expectedEnabled)
                ?: return ActionResult(
                    false,
                    "NFC toggle could not be verified: adapter state is unavailable",
                    diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE),
                )
            if (enabled != expectedEnabled) {
                return ActionResult(
                    false,
                    "NFC state mismatch: expected ${describe(expectedEnabled)} but read ${describe(enabled)}",
                    diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_MISMATCH),
                )
            }

            ActionResult(true, "NFC turned ${describe(expectedEnabled)}")
        } catch (_: Throwable) {
            ActionResult(false, "NFC toggle error")
        }
    }

    private fun awaitExpectedState(expectedEnabled: Boolean): Boolean? {
        val attempts = statePollAttempts.coerceAtLeast(1)
        var latest: Boolean? = null
        for (attempt in 0 until attempts) {
            latest = try {
                nfcEnabledProvider()
            } catch (_: Throwable) {
                null
            }
            if (latest == expectedEnabled) return latest
            if (attempt < attempts - 1) {
                try {
                    waitMillis(STATE_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return latest
                }
            }
        }
        return latest
    }

    private fun describe(enabled: Boolean): String = if (enabled) "on" else "off"
}
