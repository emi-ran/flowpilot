package com.flowpilot.app.actions

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles Bluetooth only through shell-owned `svc bluetooth enable|disable`.
 * Standard applications cannot reliably change Bluetooth state on modern Android.
 * State is polled after a successful shell command; no shell success is reported as device success.
 */
class BluetoothExecutor(
    private val context: Context,
    private val shell: ShizukuShellCompatible,
    private val adapterProvider: (Context) -> BluetoothAdapter? = { appContext ->
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    },
    private val enabledReader: (BluetoothAdapter) -> Boolean = { it.isEnabled },
    private val enabledStateReader: () -> Boolean? = {
        try { adapterProvider(context)?.let(enabledReader) } catch (_: Throwable) { null }
    },
    private val waitMillis: (Long) -> Unit = { Thread.sleep(it) },
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ActionExecutor {

    override val supportedTypes = setOf(ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (command, expectedEnabled, label) = when (action) {
            ActionType.BLUETOOTH_ON -> Triple("svc bluetooth enable", true, "on")
            ActionType.BLUETOOTH_OFF -> Triple("svc bluetooth disable", false, "off")
            else -> return ActionResult(false, "Unsupported action for Bluetooth")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult(false, "Bluetooth permission not granted to FlowPilot")
        }
        if (enabledStateReader() == null) return ActionResult(false, "Bluetooth is unsupported on this device")
        if (!shell.isShizukuRunning()) {
            return ActionResult(false, "Shizuku not running — Bluetooth can't be toggled")
        }
        if (!shell.hasPermission()) {
            return ActionResult(false, "Shizuku permission not granted to FlowPilot")
        }
        return try {
            val (code, output) = shell.run(command)
            if (code != 0) return ActionResult(false, "Bluetooth toggle failed: $output")
            val enabled = awaitExpectedState(expectedEnabled)
                ?: return ActionResult(false, "Bluetooth adapter became unavailable")
            if (enabled != expectedEnabled) {
                return ActionResult(false, "Bluetooth state mismatch: expected ${if (expectedEnabled) "on" else "off"} but read ${if (enabled) "on" else "off"}")
            }
            ActionResult(true, "Bluetooth turned $label")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun awaitExpectedState(expectedEnabled: Boolean): Boolean? {
        val deadline = elapsedRealtimeMillis() + STATE_POLL_TIMEOUT_MS
        while (true) {
            val enabled = enabledStateReader() ?: return null
            if (enabled == expectedEnabled) return enabled

            val remaining = deadline - elapsedRealtimeMillis()
            if (remaining <= 0) return enabled
            waitMillis(minOf(STATE_POLL_INTERVAL_MS, remaining))
        }
    }

    private companion object {
        const val STATE_POLL_INTERVAL_MS = 100L
        const val STATE_POLL_TIMEOUT_MS = 5_000L
    }
}
