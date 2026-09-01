package com.flowpilot.app.engine

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

enum class BluetoothDeviceEvent { CONNECTED, DISCONNECTED }

data class BluetoothDeviceTransition(
    val event: BluetoothDeviceEvent,
    val address: String,
    val name: String? = null,
)

data class BluetoothTrackerState(
    /** Last broadcast state per address. Empty at startup: no initial connection is replayed. */
    val deviceStates: Map<String, Boolean> = emptyMap(),
) {
    val connectedAddresses: Set<String>
        get() = deviceStates.filterValues { it }.keys
}

/** Pure per-device transition reducer. Dynamic ACL broadcasts are non-sticky, so start has no replay path. */
object BluetoothDeviceReducer {
    fun reduce(
        state: BluetoothTrackerState,
        event: BluetoothDeviceEvent,
        address: String,
        name: String? = null,
    ): Pair<BluetoothTrackerState, BluetoothDeviceTransition?> {
        val normalizedAddress = address.trim().uppercase()
        if (normalizedAddress.isBlank()) return state to null
        val previousState = state.deviceStates[normalizedAddress]
        return when (event) {
            BluetoothDeviceEvent.CONNECTED -> {
                if (previousState == true) state to null
                else state.copy(deviceStates = state.deviceStates + (normalizedAddress to true)) to
                    BluetoothDeviceTransition(event, normalizedAddress, name?.takeIf { it.isNotBlank() })
            }
            BluetoothDeviceEvent.DISCONNECTED -> {
                if (previousState == false) state to null
                else state.copy(deviceStates = state.deviceStates + (normalizedAddress to false)) to
                    BluetoothDeviceTransition(event, normalizedAddress, name?.takeIf { it.isNotBlank() })
            }
        }
    }
}

/**
 * Receives public ACL device connection broadcasts while engine runs.
 * Does not scan, query paired-device history, or seed current connections at startup.
 */
class BluetoothDeviceTracker(private val context: Context) {
    private val transitions = ConcurrentLinkedQueue<BluetoothDeviceTransition>()
    private var registered = false
    private var state = BluetoothTrackerState()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> BluetoothDeviceEvent.CONNECTED
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> BluetoothDeviceEvent.DISCONNECTED
                else -> return
            }
            val device = intent.bluetoothDeviceOrNull() ?: return
            val address = try { device.address } catch (_: SecurityException) { return }
            Log.i(TAG, "Received address=$address event=$event")
            val name = try { device.name } catch (_: SecurityException) { null }
            synchronized(this@BluetoothDeviceTracker) {
                val result = BluetoothDeviceReducer.reduce(state, event, address, name)
                state = result.first
                result.second?.let {
                    transitions.add(it)
                    Log.i(TAG, "Reduced address=${it.address} event=${it.event}")
                }
            }
        }
    }

    fun start() {
        if (registered) return
        if (!hasConnectPermission()) {
            Log.w(TAG, "Registration blocked: BLUETOOTH_CONNECT not granted")
            return
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Bluetooth stack broadcasts require an exported dynamic receiver on Android 13+.
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
        Log.i(TAG, "Registered ACL receiver")
    }

    fun drainTransitions(): List<BluetoothDeviceTransition> = buildList {
        while (true) add(transitions.poll() ?: break)
    }.also { Log.i(TAG, "Drained transitions=${it.size}") }

    fun stop() {
        if (registered) {
            try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
            Log.i(TAG, "Unregistered ACL receiver")
        }
        registered = false
        synchronized(this) { state = BluetoothTrackerState() }
        transitions.clear()
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "FlowPilotBluetooth"
    }
}

private fun Intent.bluetoothDeviceOrNull(): BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
