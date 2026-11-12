package com.flowpilot.app.actions

import android.content.Context
import android.os.PowerManager
import com.flowpilot.app.data.model.ActionType

// 6 attempts × 200 ms: at most 1 s of polling before giving up.
private const val STATE_POLL_ATTEMPTS = 6
private const val STATE_POLL_INTERVAL_MS = 200L

/**
 * Locks screen / turns off display via Shizuku shell keyevent 26 (KEYCODE_POWER).
 *
 * The shell exit code alone is not proof: the screen's interactive state is polled
 * afterwards. Non-interactive means confirmed; still interactive is a failure; an
 * unavailable readback reports the command as accepted without claiming completion.
 */
class LockScreenExecutor(
    context: Context,
    private val shizukuShell: ShizukuShellCompatible = ShizukuShell.instance,
    private val screenInteractiveProvider: () -> Boolean? = {
        try {
            (context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isInteractive
        } catch (_: Throwable) {
            null
        }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val statePollAttempts: Int = STATE_POLL_ATTEMPTS,
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.LOCK_SCREEN)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.LOCK_SCREEN) {
            return ActionResult(false, "Unsupported action for LockScreenExecutor")
        }

        if (!shizukuShell.isShizukuRunning()) {
            return ActionResult(false, "Lock screen requires Shizuku (service not running)", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        }
        if (!shizukuShell.hasPermission()) {
            return ActionResult(false, "Lock screen requires Shizuku permission", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        }

        val (exitCode, _) = shizukuShell.run("input keyevent 26")
        if (exitCode != 0) {
            return ActionResult(false, "Failed to lock screen: system command failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
        }

        return when (awaitScreenInteractive()) {
            false -> ActionResult(true, "Screen locked")
            true -> ActionResult(false, "Failed to lock screen: screen is still interactive", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_MISMATCH))
            null -> ActionResult(true, "Screen lock command accepted (completion not verified)")
        }
    }

    /** Returns the last observed interactive state, or null when it could never be read. */
    private fun awaitScreenInteractive(): Boolean? {
        val attempts = statePollAttempts.coerceAtLeast(1)
        var latest: Boolean? = null
        for (attempt in 0 until attempts) {
            latest = try {
                screenInteractiveProvider()
            } catch (_: Throwable) {
                null
            }
            if (latest == false) return false
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
}
