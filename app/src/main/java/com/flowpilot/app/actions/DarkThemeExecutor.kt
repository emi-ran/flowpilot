package com.flowpilot.app.actions

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles system dark theme / night mode via Shizuku (`cmd uimode night yes|no`).
 *
 * System behavior:
 * - `cmd uimode night yes` -> enables dark theme, changes Settings.Secure `ui_night_mode` to 2.
 * - `cmd uimode night no`  -> disables dark theme, changes Settings.Secure `ui_night_mode` to 1.
 *
 * Verifies actual resulting state via injectable settingsReader after command execution
 * so it never falsely reports success.
 */
class DarkThemeExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible,
    private val settingsReader: (ContentResolver, String, Int) -> Int = { cr, name, defaultVal ->
        Settings.Secure.getInt(cr, name, defaultVal)
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> =
        setOf(ActionType.DARK_THEME_ON, ActionType.DARK_THEME_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (cmdArg, expectedMode, actionLabel) = when (action) {
            ActionType.DARK_THEME_ON -> Triple("yes", 2, "on")
            ActionType.DARK_THEME_OFF -> Triple("no", 1, "off")
            else -> return ActionResult(false, "Unsupported action for dark theme")
        }

        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Dark theme can't be toggled", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        }

        return try {
            val (code, _) = shell.run("cmd uimode night $cmdArg")
            if (code != 0) {
                return ActionResult(false, "Dark theme toggle failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
            }

            val currentMode = settingsReader(context.contentResolver, "ui_night_mode", -1)
            if (currentMode != expectedMode) {
                return ActionResult(
                    false,
                    "Dark theme state mismatch: expected $expectedMode but read $currentMode",
                    diagnostic = ActionDiagnostic(
                        if (currentMode == -1) ActionDiagnosticReason.STATE_UNAVAILABLE
                        else ActionDiagnosticReason.STATE_MISMATCH,
                    ),
                )
            }

            ActionResult(true, "Dark theme $actionLabel")
        } catch (_: Throwable) {
            ActionResult(false, "Dark theme toggle error")
        }
    }
}
