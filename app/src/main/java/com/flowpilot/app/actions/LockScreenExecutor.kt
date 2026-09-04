package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType

/**
 * Locks screen / turns off display via Shizuku shell keyevent 26 (KEYCODE_POWER).
 */
class LockScreenExecutor(
    private val shizukuShell: ShizukuShellCompatible = ShizukuShell.instance,
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.LOCK_SCREEN)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.LOCK_SCREEN) {
            return ActionResult(false, "Unsupported action for LockScreenExecutor")
        }

        if (!shizukuShell.isShizukuRunning()) {
            return ActionResult(false, "Lock screen requires Shizuku (service not running)")
        }
        if (!shizukuShell.hasPermission()) {
            return ActionResult(false, "Lock screen requires Shizuku permission")
        }

        val (exitCode, output) = shizukuShell.run("input keyevent 26")
        return if (exitCode == 0) {
            ActionResult(true, "Screen locked")
        } else {
            ActionResult(false, "Failed to lock screen (exit code $exitCode): $output")
        }
    }
}
