package com.flowpilot.app.actions

import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Android location services via `cmd location set-location-enabled true|false` through Shizuku.
 * Verifies final state via LocationManager.isLocationEnabled.
 */
class LocationExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible = ShizukuShell.instance,
    private val locationManagerProvider: (Context) -> LocationManager? = { ctx ->
        ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    },
    private val enabledReader: (LocationManager) -> Boolean = { LocationManagerCompat.isLocationEnabled(it) },
    private val enabledStateReader: () -> Boolean? = {
        try { locationManagerProvider(context)?.let(enabledReader) } catch (_: Throwable) { null }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.LOCATION_ON, ActionType.LOCATION_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (command, expectedEnabled, label) = when (action) {
            ActionType.LOCATION_ON -> Triple("cmd location set-location-enabled true", true, "on")
            ActionType.LOCATION_OFF -> Triple("cmd location set-location-enabled false", false, "off")
            else -> return ActionResult(false, "Unsupported action for Location")
        }

        if (enabledStateReader() == null) {
            return ActionResult(false, "Location is unsupported on this device")
        }
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Location can't be toggled")
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot")
        }

        return try {
            val (code, output) = shell.run(command)
            if (code != 0) {
                return ActionResult(false, "Location toggle failed: $output")
            }

            val enabled = awaitExpectedState(expectedEnabled)
                ?: return ActionResult(false, "Location state became unavailable")
            if (enabled != expectedEnabled) {
                return ActionResult(false, "Location state mismatch: expected ${if (expectedEnabled) "on" else "off"} but read ${if (enabled) "on" else "off"}")
            }
            ActionResult(true, "Location turned $label")
        } catch (t: Throwable) {
            ActionResult(false, "Location toggle error: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun awaitExpectedState(expected: Boolean, timeoutMs: Long = 1_500L): Boolean? {
        val deadline = elapsedRealtimeMillis() + timeoutMs
        var latest: Boolean? = null
        while (elapsedRealtimeMillis() <= deadline) {
            latest = enabledStateReader()
            if (latest == expected) return latest
            waitMillis(100L)
        }
        return latest ?: enabledStateReader()
    }
}
