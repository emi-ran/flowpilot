package com.flowpilot.app.actions

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Mobile Data through shell-owned `svc data enable|disable` via Shizuku.
 * Third-party Android apps cannot toggle cellular data directly without system permissions.
 * State is verified via TelephonyManager / Settings.Global; honest error returned if state mismatches.
 */
class MobileDataExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible,
    private val telephonyManagerProvider: (Context) -> TelephonyManager? = { appContext ->
        appContext.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    },
    private val enabledStateReader: () -> Boolean? = {
        try {
            // First check Settings.Global "mobile_data"
            val globalVal = Settings.Global.getInt(context.contentResolver, "mobile_data", -1)
            if (globalVal != -1) {
                globalVal == 1
            } else {
                // Fallback to TelephonyManager if accessible
                val tm = telephonyManagerProvider(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tm?.isDataEnabled
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ActionExecutor {

    override val supportedTypes = setOf(ActionType.MOBILE_DATA_ON, ActionType.MOBILE_DATA_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (command, expectedEnabled, label) = when (action) {
            ActionType.MOBILE_DATA_ON -> Triple("svc data enable", true, "on")
            ActionType.MOBILE_DATA_OFF -> Triple("svc data disable", false, "off")
            else -> return ActionResult(false, "Unsupported action for Mobile Data")
        }

        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Mobile Data can't be toggled")
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot")
        }

        return try {
            val (code, output) = shell.run(command)
            if (code != 0) {
                return ActionResult(false, "Mobile Data toggle failed: $output")
            }

            val enabled = awaitExpectedState(expectedEnabled)
            if (enabled != null && enabled != expectedEnabled) {
                return ActionResult(false, "Mobile Data state mismatch: expected ${if (expectedEnabled) "on" else "off"} but read ${if (enabled) "on" else "off"}")
            }
            ActionResult(true, "Mobile Data turned $label")
        } catch (t: Throwable) {
            ActionResult(false, "Mobile Data toggle error: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun awaitExpectedState(expected: Boolean, timeoutMs: Long = 4_000L): Boolean? {
        val deadline = elapsedRealtimeMillis() + timeoutMs
        var latest: Boolean? = null
        while (elapsedRealtimeMillis() <= deadline) {
            latest = enabledStateReader()
            if (latest == expected) return latest
            waitMillis(100L)
        }
        return latest
    }
}
