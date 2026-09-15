package com.flowpilot.app.ui.util

import android.content.Context
import com.flowpilot.app.R
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.TriggerEvent

fun automaticAutomationName(
    context: Context,
    trigger: TriggerEvent,
    actions: List<ActionType>,
    appName: String,
    appPackage: String,
    scheduledMinute: Int,
    batteryLevel: Int,
    wifiSsid: String,
    bluetoothDeviceName: String,
    bluetoothDeviceAddress: String,
    nfcTagId: String,
    notificationAppName: String,
    notificationAppPackage: String,
    lightLux: Int,
    geofenceName: String,
    geofenceRadiusMeters: Int,
): String {
    val summary = actions.joinToString(" + ") { context.getString(it.labelRes) }
    val triggerLabel = context.getString(trigger.labelRes)
    return when (trigger) {
        TriggerEvent.TIME_SCHEDULE -> context.getString(
            R.string.automatic_name_schedule,
            scheduledMinute / 60,
            scheduledMinute % 60,
            summary,
        )
        TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> "$triggerLabel $batteryLevel% · $summary"
        TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED ->
            "$triggerLabel ${wifiSsid.ifBlank { context.getString(R.string.automatic_name_any_wifi) }} · $summary"
        TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED ->
            "$triggerLabel ${bluetoothDeviceName.ifBlank { bluetoothDeviceAddress }} · $summary"
        TriggerEvent.NFC_TAG_SCANNED -> context.getString(R.string.automatic_name_nfc_tag, nfcTagId, summary)
        TriggerEvent.NOTIFICATION_RECEIVED -> context.getString(
            R.string.automatic_name_notification,
            notificationAppName.ifBlank { notificationAppPackage }, summary,
        )
        TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> "$triggerLabel ${lightLux}lx · $summary"
        TriggerEvent.SMS_RECEIVED -> "$triggerLabel · $summary"
        TriggerEvent.GEOFENCE_ENTER, TriggerEvent.GEOFENCE_EXIT ->
            "$triggerLabel (${geofenceName.ifBlank { "${geofenceRadiusMeters}m" }}) · $summary"
        TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> "${appName.ifBlank { appPackage }} · $summary"
        else -> "$triggerLabel · $summary"
    }
}
