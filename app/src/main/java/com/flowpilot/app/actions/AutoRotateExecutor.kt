package com.flowpilot.app.actions

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Android system auto-rotation (ACCELEROMETER_ROTATION).
 *
 * Requirements:
 * Requires user-grantable WRITE_SETTINGS special access (Settings.System.canWrite(context)).
 *
 * System behavior:
 * - 1: Auto-rotate enabled (screen rotates freely with accelerometer)
 * - 0: Auto-rotate disabled (portrait lock / automatic rotation disabled; does not claim arbitrary orientation lock)
 */
class AutoRotateExecutor(
    private val context: Context,
    private val permissionChecker: (Context) -> Boolean = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(ctx)
        } else {
            true
        }
    },
    private val settingsWriter: (ContentResolver, String, Int) -> Boolean = { cr, name, value ->
        Settings.System.putInt(cr, name, value)
    },
    private val settingsReader: (ContentResolver, String, Int) -> Int = { cr, name, defaultVal ->
        Settings.System.getInt(cr, name, defaultVal)
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> =
        setOf(ActionType.AUTO_ROTATE_ON, ActionType.AUTO_ROTATE_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val target = when (action) {
            ActionType.AUTO_ROTATE_ON -> 1
            ActionType.AUTO_ROTATE_OFF -> 0
            else -> return ActionResult(false, "Unsupported action for auto-rotate")
        }

        if (!permissionChecker(context)) {
            return ActionResult(
                false,
                "Auto-rotate requires Modify system settings (WRITE_SETTINGS) permission",
            )
        }

        return try {
            val written = settingsWriter(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, target)
            if (!written) {
                return ActionResult(false, "Failed to write auto-rotate setting")
            }

            val readBack = settingsReader(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, -1)
            if (readBack != target) {
                return ActionResult(
                    false,
                    "Auto-rotate state mismatch: wrote $target but read $readBack",
                )
            }

            val message = if (target == 1) {
                "Auto-rotate on"
            } else {
                "Auto-rotate off (portrait lock)"
            }
            ActionResult(true, message)
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
