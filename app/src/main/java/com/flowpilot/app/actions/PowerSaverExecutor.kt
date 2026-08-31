package com.flowpilot.app.actions

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Battery Saver. Android does not expose a public API for this; the value
 * lives in Settings.Global "low_power". Two supported, root-free paths:
 *   1. The app itself holds WRITE_SECURE_SETTINGS (granted via `adb shell pm grant`),
 *      in which case we write the global directly.
 *   2. Shizuku runs `settings put global low_power <v>` as shell.
 * Both are checked for real capability before running; failures are reported, not faked.
 */
class PowerSaverExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible,
    private val permissionChecker: (Context) -> Boolean = { ctx ->
        ctx.packageManager.checkPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            ctx.packageName,
        ) == PackageManager.PERMISSION_GRANTED
    },
    private val settingsWriter: (ContentResolver, String, Int) -> Boolean = { cr, name, value ->
        Settings.Global.putInt(cr, name, value)
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> =
        setOf(ActionType.BATTERY_SAVER_ON, ActionType.BATTERY_SAVER_OFF)

    private fun hasWriteSecureSettings(): Boolean = permissionChecker(context)

    override fun execute(action: ActionType): ActionResult {
        val target = when (action) {
            ActionType.BATTERY_SAVER_ON -> 1
            ActionType.BATTERY_SAVER_OFF -> 0
            else -> return ActionResult(false, "Unsupported action for battery saver")
        }

        // Primary method: Direct write via WRITE_SECURE_SETTINGS (ADB-granted).
        // Preferred because it is instantaneous and requires no Shizuku or shell processes.
        if (hasWriteSecureSettings()) {
            return try {
                val ok = settingsWriter(context.contentResolver, "low_power", target)
                if (ok) ActionResult(true, "Battery saver ${if (target == 1) "on" else "off"}")
                else ActionResult(false, "Failed to write battery saver setting")
            } catch (t: Throwable) {
                ActionResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

        // Fallback method: Shizuku executes `cmd power set-mode <0|1>`
        // and updates system & global settings for AOSP and Xiaomi HyperOS.
        if (shell.isShizukuRunning() && shell.hasPermission()) {
            return try {
                val (code, out) = shell.run("cmd power set-mode $target")
                try { shell.run("settings put system POWER_SAVE_MODE_OPEN $target") } catch (_: Throwable) {}
                try { shell.run("settings put global low_power $target") } catch (_: Throwable) {}
                if (code == 0) {
                    ActionResult(true, "Battery saver ${if (target == 1) "on" else "off"}")
                } else {
                    val (code2, out2) = shell.run("settings put global low_power $target")
                    if (code2 == 0) ActionResult(true, "Battery saver ${if (target == 1) "on" else "off"}")
                    else ActionResult(false, "Failed to toggle battery saver: $out / $out2")
                }
            } catch (t: Throwable) {
                ActionResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

        return ActionResult(false, "Battery saver needs WRITE_SECURE_SETTINGS or Shizuku permission")
    }
}
