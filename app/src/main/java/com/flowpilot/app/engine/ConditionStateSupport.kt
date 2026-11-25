package com.flowpilot.app.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RingerModeCondition
import com.flowpilot.app.data.model.flattenConditions

/**
 * Which on-demand [LiveSystemState] fields the enabled rules' conditions need.
 * Fields no rule uses are never read (and no receiver is registered for them).
 */
data class ConditionStateDemand(
    val bluetoothDevices: Boolean = false,
    val headset: Boolean = false,
    val dnd: Boolean = false,
    val ringer: Boolean = false,
    val airplane: Boolean = false,
) {
    companion object {
        fun from(rules: List<Automation>): ConditionStateDemand {
            val types = rules.filter { it.enabled }
                .flatMap { it.conditionTree?.flattenConditions() ?: it.conditions }
                .mapTo(mutableSetOf()) { it.type }
            return ConditionStateDemand(
                bluetoothDevices = ConditionType.BLUETOOTH_DEVICE_CONNECTED in types,
                headset = ConditionType.HEADSET_CONNECTED in types,
                dnd = ConditionType.DND_ON in types,
                ringer = ConditionType.RINGER_MODE in types,
                airplane = ConditionType.AIRPLANE_MODE_ON in types,
            )
        }
    }
}

/** Pure decoders for platform values; kept free of Android calls so JVM tests can cover them. */
object ConditionStateDecoders {
    private const val TYPE_WIRED_HEADSET = 3
    private const val TYPE_WIRED_HEADPHONES = 4
    private const val TYPE_BLUETOOTH_SCO = 7
    private const val TYPE_BLUETOOTH_A2DP = 8
    private const val TYPE_USB_DEVICE = 11
    private const val TYPE_USB_HEADSET = 22
    private const val TYPE_BLE_HEADSET = 26
    private const val TYPE_HEARING_AID = 23

    private const val FILTER_PRIORITY = 2
    private const val FILTER_NONE = 3
    private const val FILTER_ALARMS = 4

    private val headsetTypes = setOf(
        TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES, TYPE_BLUETOOTH_SCO, TYPE_BLUETOOTH_A2DP,
        TYPE_USB_DEVICE, TYPE_USB_HEADSET, TYPE_BLE_HEADSET, TYPE_HEARING_AID,
    )
    private val bluetoothTypes = setOf(TYPE_BLUETOOTH_SCO, TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_HEARING_AID)

    /** True when any audio output device type is a wired, USB or Bluetooth headset-like output. */
    fun isHeadsetOutput(outputTypes: Collection<Int>): Boolean = outputTypes.any { it in headsetTypes }

    /** Bluetooth MAC addresses (upper-case) of Bluetooth audio outputs; invalid or blank addresses are ignored. */
    fun bluetoothAudioAddresses(outputs: Collection<Pair<Int, String>>): Set<String> =
        outputs.filter { it.first in bluetoothTypes && it.second.isNotBlank() }
            .mapTo(mutableSetOf()) { it.second.trim().uppercase() }

    /** Any interruption filter other than "all" (or unknown) means Do Not Disturb is active. */
    fun isDndActive(interruptionFilter: Int): Boolean =
        interruptionFilter == FILTER_PRIORITY || interruptionFilter == FILTER_NONE || interruptionFilter == FILTER_ALARMS

    fun ringerModeOf(audioManagerMode: Int): RingerModeCondition? = when (audioManagerMode) {
        AudioManager.RINGER_MODE_NORMAL -> RingerModeCondition.NORMAL
        AudioManager.RINGER_MODE_VIBRATE -> RingerModeCondition.VIBRATE
        AudioManager.RINGER_MODE_SILENT -> RingerModeCondition.SILENT
        else -> null
    }
}

/**
 * Synchronous one-shot readers for the on-demand condition fields. Each field is read only when
 * [ConditionStateDemand] asks for it and any failure degrades to null (unknown => condition false).
 */
class ConditionStateReader(context: Context) {
    private val appContext = context.applicationContext

    fun read(
        demand: ConditionStateDemand,
        base: LiveSystemState,
        trackedBluetoothAddresses: Set<String> = emptySet(),
    ): LiveSystemState {
        if (demand == ConditionStateDemand()) return base
        val audio = if (demand.headset || demand.bluetoothDevices) {
            appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } else null
        val outputs = if (audio != null) safe { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() } else null
        return base.copy(
            connectedBluetoothAddresses = if (demand.bluetoothDevices) {
                trackedBluetoothAddresses.mapTo(mutableSetOf()) { it.trim().uppercase() } +
                    ConditionStateDecoders.bluetoothAudioAddresses(
                        outputs.orEmpty().map { it.type to deviceAddress(it) },
                    )
            } else null,
            isHeadsetConnected = if (demand.headset && outputs != null) {
                ConditionStateDecoders.isHeadsetOutput(outputs.map(AudioDeviceInfo::getType))
            } else null,
            isDndOn = if (demand.dnd) safe {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.let { ConditionStateDecoders.isDndActive(it.currentInterruptionFilter) }
            } else null,
            ringerMode = if (demand.ringer) safe {
                (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.let { ConditionStateDecoders.ringerModeOf(it.ringerMode) }
            } else null,
            isAirplaneModeOn = if (demand.airplane) safe {
                Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            } else null,
        )
    }

    private fun deviceAddress(device: AudioDeviceInfo): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) safe { device.address }.orEmpty() else ""

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}
