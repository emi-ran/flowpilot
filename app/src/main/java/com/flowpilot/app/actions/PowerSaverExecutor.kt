package com.flowpilot.app.actions

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.flowpilot.app.data.model.ActionType

private const val LOW_POWER_SETTING = "low_power"

// 6 attempts × 200 ms: at most 1 s of polling before giving up.
private const val STATE_POLL_ATTEMPTS = 6
private const val STATE_POLL_INTERVAL_MS = 200L

/**
 * Toggles Battery Saver. Android does not expose a public API for this; the value
 * lives in Settings.Global "low_power". Two supported, root-free paths:
 *   1. The app itself holds WRITE_SECURE_SETTINGS (granted via `adb shell pm grant`),
 *      in which case we write the global directly.
 *   2. Shizuku runs `settings put global low_power <v>` as shell.
 * Both are checked for real capability before running. A successful write or shell
 * exit code is not treated as proof: the resulting `low_power` value is polled and
 * a mismatch or unreadable value is reported as failure.
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
    private val settingsReader: (ContentResolver) -> Int? = { cr ->
        Settings.Global.getInt(cr, LOW_POWER_SETTING, 0)
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val statePollAttempts: Int = STATE_POLL_ATTEMPTS,
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> =
        setOf(ActionType.BATTERY_SAVER_ON, ActionType.BATTERY_SAVER_OFF)

    private fun hasWriteSecureSettings(): Boolean = permissionChecker(context)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val target = when (action) {
            ActionType.BATTERY_SAVER_ON -> 1
            ActionType.BATTERY_SAVER_OFF -> 0
            else -> return ActionResult(false, "Unsupported action for battery saver")
        }

        // Primary method: Direct write via WRITE_SECURE_SETTINGS (ADB-granted).
        // Preferred because it is instantaneous and requires no Shizuku or shell processes.
        if (hasWriteSecureSettings()) {
            return try {
                val ok = settingsWriter(context.contentResolver, LOW_POWER_SETTING, target)
                if (!ok) {
                    ActionResult(false, "Failed to write battery saver setting")
                } else {
                    verifyState(target)
                }
            } catch (t: Throwable) {
                ActionResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

        // Fallback method: Shizuku executes `cmd power set-mode <0|1>`
        // and updates system & global settings for AOSP and Xiaomi HyperOS.
        if (shell.isShizukuRunning() && shell.hasPermission()) {
            return try {
                shell.run("cmd power set-mode $target")
                try { shell.run("settings put system POWER_SAVE_MODE_OPEN $target") } catch (_: Throwable) {}
                try { shell.run("settings put global $LOW_POWER_SETTING $target") } catch (_: Throwable) {}
                // Exit codes are advisory; only the platform setting decides the result.
                verifyState(target)
            } catch (t: Throwable) {
                ActionResult(false, t.message ?: t.javaClass.simpleName)
            }
        }

        return ActionResult(false, "Battery saver needs WRITE_SECURE_SETTINGS or Shizuku permission")
    }

    private fun verifyState(target: Int): ActionResult {
        val observed = awaitState(target)
            ?: return ActionResult(
                false,
                "Battery saver state could not be verified: $LOW_POWER_SETTING setting is unavailable",
            )
        if (observed != target) {
            return ActionResult(
                false,
                "Battery saver state mismatch: expected ${describe(target)} but read ${describe(observed)}",
            )
        }
        return ActionResult(true, "Battery saver ${describe(target)}")
    }

    private fun awaitState(target: Int): Int? {
        val attempts = statePollAttempts.coerceAtLeast(1)
        var latest: Int? = null
        for (attempt in 0 until attempts) {
            latest = try {
                settingsReader(context.contentResolver)
            } catch (_: Throwable) {
                null
            }
            if (latest == target) return latest
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

    private fun describe(value: Int): String = when (value) {
        1 -> "on"
        0 -> "off"
        else -> value.toString()
    }
}
