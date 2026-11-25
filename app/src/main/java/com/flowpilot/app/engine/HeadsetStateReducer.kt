package com.flowpilot.app.engine

enum class HeadsetEvent {
    CONNECTED,
    DISCONNECTED,
}

/**
 * Plain snapshot of one audio device. [type] holds the numeric `AudioDeviceInfo.TYPE_*` value, so the
 * reducer needs no Android classes. Name and address are deliberately not carried.
 */
data class HeadsetDevice(val id: Int, val type: Int, val isSink: Boolean = true)

/** Numeric `AudioDeviceInfo.TYPE_*` values (verified against the SDK constants in unit tests). */
object HeadsetDeviceTypes {
    const val BUILTIN_EARPIECE = 1
    const val BUILTIN_SPEAKER = 2
    const val WIRED_HEADSET = 3
    const val WIRED_HEADPHONES = 4
    const val BLUETOOTH_SCO = 7
    const val BLUETOOTH_A2DP = 8
    const val HDMI = 9
    const val USB_DEVICE = 11
    const val USB_HEADSET = 22
    const val BLE_HEADSET = 26

    private const val API_USB_HEADSET = 26
    private const val API_BLE_HEADSET = 31

    /** True for wired, USB and Bluetooth audio outputs this trigger treats as a headset. */
    fun isHeadsetType(type: Int, sdkInt: Int): Boolean = when (type) {
        WIRED_HEADSET, WIRED_HEADPHONES, USB_DEVICE, BLUETOOTH_SCO, BLUETOOTH_A2DP -> true
        USB_HEADSET -> sdkInt >= API_USB_HEADSET
        BLE_HEADSET -> sdkInt >= API_BLE_HEADSET
        else -> false
    }
}

/**
 * Tracks which headset-class output devices are present and reports real transitions only.
 *
 * Semantics: CONNECTED when the first headset output appears (none before), DISCONNECTED when the
 * last one leaves. A second device while one is present, or one of several leaving, is not a
 * transition; a Bluetooth headset that exposes A2DP and SCO as two devices is therefore one event.
 * Repeated add/remove callbacks, unknown removals, inputs and the built-in speaker are ignored.
 * Seeding records the current devices without emitting, so registration never replays startup state.
 */
class HeadsetStateReducer(private val sdkInt: Int) {
    private val present = LinkedHashSet<Int>()

    val isConnected: Boolean get() = present.isNotEmpty()

    @Synchronized
    fun seed(devices: Collection<HeadsetDevice>) {
        present.clear()
        devices.filter(::qualifies).forEach { present.add(it.id) }
    }

    @Synchronized
    fun onDevicesAdded(devices: Collection<HeadsetDevice>): HeadsetEvent? {
        val before = present.isNotEmpty()
        devices.filter(::qualifies).forEach { present.add(it.id) }
        return transition(before)
    }

    @Synchronized
    fun onDevicesRemoved(devices: Collection<HeadsetDevice>): HeadsetEvent? {
        val before = present.isNotEmpty()
        devices.forEach { present.remove(it.id) }
        return transition(before)
    }

    @Synchronized
    fun reset() = present.clear()

    private fun qualifies(device: HeadsetDevice) =
        device.isSink && HeadsetDeviceTypes.isHeadsetType(device.type, sdkInt)

    private fun transition(before: Boolean): HeadsetEvent? {
        val after = present.isNotEmpty()
        return when {
            !before && after -> HeadsetEvent.CONNECTED
            before && !after -> HeadsetEvent.DISCONNECTED
            else -> null
        }
    }
}
