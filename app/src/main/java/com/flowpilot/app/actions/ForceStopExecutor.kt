package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType

/**
 * Force stops an application via Shizuku shell (`am force-stop <package>`).
 */
class ForceStopExecutor(
    private val shizukuShell: ShizukuShellCompatible = ShizukuShell.instance,
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.FORCE_STOP_APP)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.FORCE_STOP_APP) {
            return ActionResult(false, "Unsupported action for ForceStopExecutor")
        }

        val pkg = parameters.forceStopPackage.trim()
        if (pkg.isBlank()) {
            return ActionResult(false, "Target application package is not configured")
        }

        if (!pkg.matches(Regex("""^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$"""))) {
            return ActionResult(false, "Invalid application package name: $pkg")
        }

        if (!shizukuShell.isShizukuRunning()) {
            return ActionResult(false, "Force stop requires Shizuku (service not running)")
        }
        if (!shizukuShell.hasPermission()) {
            return ActionResult(false, "Force stop requires Shizuku permission")
        }

        val (exitCode, output) = shizukuShell.run("am force-stop $pkg")
        return if (exitCode == 0) {
            ActionResult(true, "Force stopped $pkg")
        } else {
            ActionResult(false, "Failed to force stop $pkg (exit code $exitCode): $output")
        }
    }
}
