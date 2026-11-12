package com.flowpilot.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Versioned OR list. Null on Automation preserves the legacy single-trigger wire format. */
@Serializable
data class TriggerDefinitions(val version: Int = 1, val items: List<TriggerDefinition>) {
    companion object { const val MAX_TRIGGERS = 5 }
}

/** A payload contains only the configuration relevant to this event. */
@Serializable
sealed interface TriggerDefinition {
    val event: TriggerEvent
    @Serializable
    @SerialName("APP_OPENED")
    data class AppOpened(val appPackage: String = "", val appName: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.APP_OPENED
    }
    @Serializable
    @SerialName("APP_CLOSED")
    data class AppClosed(val appPackage: String = "", val appName: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.APP_CLOSED
    }
    @Serializable
    @SerialName("CHARGER_CONNECTED")
    data object ChargerConnected : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.CHARGER_CONNECTED
    }
    @Serializable
    @SerialName("CHARGER_DISCONNECTED")
    data object ChargerDisconnected : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.CHARGER_DISCONNECTED
    }
    @Serializable
    @SerialName("BATTERY_BELOW")
    data class BatteryBelow(val batteryLevel: Int = 50) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.BATTERY_BELOW
    }
    @Serializable
    @SerialName("BATTERY_ABOVE")
    data class BatteryAbove(val batteryLevel: Int = 50) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.BATTERY_ABOVE
    }
    @Serializable
    @SerialName("SCREEN_ON")
    data object ScreenOn : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.SCREEN_ON
    }
    @Serializable
    @SerialName("SCREEN_OFF")
    data object ScreenOff : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.SCREEN_OFF
    }
    @Serializable
    @SerialName("TIME_SCHEDULE")
    data class Schedule(val scheduledMinute: Int = 0, val scheduledDays: Set<Int> = emptySet()) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.TIME_SCHEDULE
    }
    @Serializable
    @SerialName("WIFI_CONNECTED")
    data class WifiConnected(val wifiSsid: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.WIFI_CONNECTED
    }
    @Serializable
    @SerialName("WIFI_DISCONNECTED")
    data class WifiDisconnected(val wifiSsid: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.WIFI_DISCONNECTED
    }
    @Serializable
    @SerialName("BLUETOOTH_CONNECTED")
    data class BluetoothConnected(val bluetoothDeviceAddress: String = "", val bluetoothDeviceName: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.BLUETOOTH_CONNECTED
    }
    @Serializable
    @SerialName("BLUETOOTH_DISCONNECTED")
    data class BluetoothDisconnected(val bluetoothDeviceAddress: String = "", val bluetoothDeviceName: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.BLUETOOTH_DISCONNECTED
    }
    @Serializable
    @SerialName("NFC_TAG_SCANNED")
    data class NfcTag(val nfcTagId: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.NFC_TAG_SCANNED
    }
    @Serializable
    @SerialName("NOTIFICATION_RECEIVED")
    data class Notification(val notificationAppPackage: String = "", val notificationAppName: String = "", val notificationKeyword: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.NOTIFICATION_RECEIVED
    }
    @Serializable
    @SerialName("MEDIA_PLAYING")
    data class MediaPlaying(val mediaTriggerPackage: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.MEDIA_PLAYING
    }
    @Serializable
    @SerialName("MEDIA_PAUSED")
    data class MediaPaused(val mediaTriggerPackage: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.MEDIA_PAUSED
    }
    @Serializable
    @SerialName("MEDIA_TRACK_CHANGED")
    data class MediaTrackChanged(val mediaTriggerPackage: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.MEDIA_TRACK_CHANGED
    }
    @Serializable
    @SerialName("CALL_RINGING")
    data object CallRinging : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.CALL_RINGING
    }
    @Serializable
    @SerialName("CALL_ANSWERED")
    data object CallAnswered : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.CALL_ANSWERED
    }
    @Serializable
    @SerialName("CALL_OUTGOING")
    data object CallOutgoing : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.CALL_OUTGOING
    }
    @Serializable
    @SerialName("CALL_ENDED")
    data object CallEnded : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.CALL_ENDED
    }
    @Serializable
    @SerialName("DEVICE_FLIPPED_DOWN")
    data class FlipDown(val flipScreenOffDetection: Boolean = false) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.DEVICE_FLIPPED_DOWN
    }
    @Serializable
    @SerialName("DEVICE_FLIPPED_UP")
    data class FlipUp(val flipScreenOffDetection: Boolean = false) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.DEVICE_FLIPPED_UP
    }
    @Serializable
    @SerialName("DEVICE_SHAKE")
    data class Shake(val shakeThreshold: Float = Automation.DEFAULT_SHAKE_THRESHOLD) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.DEVICE_SHAKE
    }
    @Serializable
    @SerialName("DEVICE_UNLOCKED")
    data object Unlocked : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.DEVICE_UNLOCKED
    }
    @Serializable
    @SerialName("LIGHT_BELOW")
    data class LightBelow(val lightLux: Int = 10) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.LIGHT_BELOW
    }
    @Serializable
    @SerialName("LIGHT_ABOVE")
    data class LightAbove(val lightLux: Int = 10) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.LIGHT_ABOVE
    }
    @Serializable
    @SerialName("SMS_RECEIVED")
    data class Sms(val smsSenderFilter: String = "", val smsMatchMode: SmsMatchMode = SmsMatchMode.CONTAINS, val smsKeyword: String = "") : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.SMS_RECEIVED
    }
    @Serializable
    @SerialName("GEOFENCE_ENTER")
    data class GeofenceEnter(val geofenceName: String = "", val geofenceLatitude: Double = 0.0, val geofenceLongitude: Double = 0.0, val geofenceRadiusMeters: Int = 150) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.GEOFENCE_ENTER
    }
    @Serializable
    @SerialName("GEOFENCE_EXIT")
    data class GeofenceExit(val geofenceName: String = "", val geofenceLatitude: Double = 0.0, val geofenceLongitude: Double = 0.0, val geofenceRadiusMeters: Int = 150) : TriggerDefinition {
        override val event: TriggerEvent get() = TriggerEvent.GEOFENCE_EXIT
    }
}

/** Migrates legacy fields in memory without changing single-trigger exports. */
fun Automation.normalizedTriggers(): List<TriggerDefinition> = triggerDefinitions?.items ?: listOf(legacyTriggerDefinition())

fun Automation.legacyTriggerDefinition(): TriggerDefinition = when (triggerEvent) {
    TriggerEvent.APP_OPENED -> TriggerDefinition.AppOpened(appPackage, appName)
    TriggerEvent.APP_CLOSED -> TriggerDefinition.AppClosed(appPackage, appName)
    TriggerEvent.CHARGER_CONNECTED -> TriggerDefinition.ChargerConnected
    TriggerEvent.CHARGER_DISCONNECTED -> TriggerDefinition.ChargerDisconnected
    TriggerEvent.BATTERY_BELOW -> TriggerDefinition.BatteryBelow(batteryLevel)
    TriggerEvent.BATTERY_ABOVE -> TriggerDefinition.BatteryAbove(batteryLevel)
    TriggerEvent.SCREEN_ON -> TriggerDefinition.ScreenOn
    TriggerEvent.SCREEN_OFF -> TriggerDefinition.ScreenOff
    TriggerEvent.TIME_SCHEDULE -> TriggerDefinition.Schedule(scheduledMinute, scheduledDays)
    TriggerEvent.WIFI_CONNECTED -> TriggerDefinition.WifiConnected(wifiSsid)
    TriggerEvent.WIFI_DISCONNECTED -> TriggerDefinition.WifiDisconnected(wifiSsid)
    TriggerEvent.BLUETOOTH_CONNECTED -> TriggerDefinition.BluetoothConnected(bluetoothDeviceAddress, bluetoothDeviceName)
    TriggerEvent.BLUETOOTH_DISCONNECTED -> TriggerDefinition.BluetoothDisconnected(bluetoothDeviceAddress, bluetoothDeviceName)
    TriggerEvent.NFC_TAG_SCANNED -> TriggerDefinition.NfcTag(nfcTagId)
    TriggerEvent.NOTIFICATION_RECEIVED -> TriggerDefinition.Notification(notificationAppPackage, notificationAppName, notificationKeyword)
    TriggerEvent.MEDIA_PLAYING -> TriggerDefinition.MediaPlaying(mediaTriggerPackage)
    TriggerEvent.MEDIA_PAUSED -> TriggerDefinition.MediaPaused(mediaTriggerPackage)
    TriggerEvent.MEDIA_TRACK_CHANGED -> TriggerDefinition.MediaTrackChanged(mediaTriggerPackage)
    TriggerEvent.CALL_RINGING -> TriggerDefinition.CallRinging
    TriggerEvent.CALL_ANSWERED -> TriggerDefinition.CallAnswered
    TriggerEvent.CALL_OUTGOING -> TriggerDefinition.CallOutgoing
    TriggerEvent.CALL_ENDED -> TriggerDefinition.CallEnded
    TriggerEvent.DEVICE_FLIPPED_DOWN -> TriggerDefinition.FlipDown(flipScreenOffDetection)
    TriggerEvent.DEVICE_FLIPPED_UP -> TriggerDefinition.FlipUp(flipScreenOffDetection)
    TriggerEvent.DEVICE_SHAKE -> TriggerDefinition.Shake(shakeThreshold)
    TriggerEvent.DEVICE_UNLOCKED -> TriggerDefinition.Unlocked
    TriggerEvent.LIGHT_BELOW -> TriggerDefinition.LightBelow(lightLux)
    TriggerEvent.LIGHT_ABOVE -> TriggerDefinition.LightAbove(lightLux)
    TriggerEvent.SMS_RECEIVED -> TriggerDefinition.Sms(smsSenderFilter, smsMatchMode, smsKeyword)
    TriggerEvent.GEOFENCE_ENTER -> TriggerDefinition.GeofenceEnter(geofenceName, geofenceLatitude, geofenceLongitude, geofenceRadiusMeters)
    TriggerEvent.GEOFENCE_EXIT -> TriggerDefinition.GeofenceExit(geofenceName, geofenceLatitude, geofenceLongitude, geofenceRadiusMeters)
}

/** A transient view used by existing per-type matchers and pickers; never persisted as a rule. */
fun Automation.withTrigger(definition: TriggerDefinition): Automation = when (definition) {
    is TriggerDefinition.AppOpened -> copy(triggerEvent = definition.event, triggerDefinitions = null, appPackage = definition.appPackage, appName = definition.appName)
    is TriggerDefinition.AppClosed -> copy(triggerEvent = definition.event, triggerDefinitions = null, appPackage = definition.appPackage, appName = definition.appName)
    TriggerDefinition.ChargerConnected -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    TriggerDefinition.ChargerDisconnected -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    is TriggerDefinition.BatteryBelow -> copy(triggerEvent = definition.event, triggerDefinitions = null, batteryLevel = definition.batteryLevel)
    is TriggerDefinition.BatteryAbove -> copy(triggerEvent = definition.event, triggerDefinitions = null, batteryLevel = definition.batteryLevel)
    TriggerDefinition.ScreenOn -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    TriggerDefinition.ScreenOff -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    is TriggerDefinition.Schedule -> copy(triggerEvent = definition.event, triggerDefinitions = null, scheduledMinute = definition.scheduledMinute, scheduledDays = definition.scheduledDays)
    is TriggerDefinition.WifiConnected -> copy(triggerEvent = definition.event, triggerDefinitions = null, wifiSsid = definition.wifiSsid)
    is TriggerDefinition.WifiDisconnected -> copy(triggerEvent = definition.event, triggerDefinitions = null, wifiSsid = definition.wifiSsid)
    is TriggerDefinition.BluetoothConnected -> copy(triggerEvent = definition.event, triggerDefinitions = null, bluetoothDeviceAddress = definition.bluetoothDeviceAddress, bluetoothDeviceName = definition.bluetoothDeviceName)
    is TriggerDefinition.BluetoothDisconnected -> copy(triggerEvent = definition.event, triggerDefinitions = null, bluetoothDeviceAddress = definition.bluetoothDeviceAddress, bluetoothDeviceName = definition.bluetoothDeviceName)
    is TriggerDefinition.NfcTag -> copy(triggerEvent = definition.event, triggerDefinitions = null, nfcTagId = definition.nfcTagId)
    is TriggerDefinition.Notification -> copy(triggerEvent = definition.event, triggerDefinitions = null, notificationAppPackage = definition.notificationAppPackage, notificationAppName = definition.notificationAppName, notificationKeyword = definition.notificationKeyword)
    is TriggerDefinition.MediaPlaying -> copy(triggerEvent = definition.event, triggerDefinitions = null, mediaTriggerPackage = definition.mediaTriggerPackage)
    is TriggerDefinition.MediaPaused -> copy(triggerEvent = definition.event, triggerDefinitions = null, mediaTriggerPackage = definition.mediaTriggerPackage)
    is TriggerDefinition.MediaTrackChanged -> copy(triggerEvent = definition.event, triggerDefinitions = null, mediaTriggerPackage = definition.mediaTriggerPackage)
    TriggerDefinition.CallRinging -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    TriggerDefinition.CallAnswered -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    TriggerDefinition.CallOutgoing -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    TriggerDefinition.CallEnded -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    is TriggerDefinition.FlipDown -> copy(triggerEvent = definition.event, triggerDefinitions = null, flipScreenOffDetection = definition.flipScreenOffDetection)
    is TriggerDefinition.FlipUp -> copy(triggerEvent = definition.event, triggerDefinitions = null, flipScreenOffDetection = definition.flipScreenOffDetection)
    is TriggerDefinition.Shake -> copy(triggerEvent = definition.event, triggerDefinitions = null, shakeThreshold = definition.shakeThreshold)
    TriggerDefinition.Unlocked -> copy(triggerEvent = definition.event, triggerDefinitions = null)
    is TriggerDefinition.LightBelow -> copy(triggerEvent = definition.event, triggerDefinitions = null, lightLux = definition.lightLux)
    is TriggerDefinition.LightAbove -> copy(triggerEvent = definition.event, triggerDefinitions = null, lightLux = definition.lightLux)
    is TriggerDefinition.Sms -> copy(triggerEvent = definition.event, triggerDefinitions = null, smsSenderFilter = definition.smsSenderFilter, smsMatchMode = definition.smsMatchMode, smsKeyword = definition.smsKeyword)
    is TriggerDefinition.GeofenceEnter -> copy(triggerEvent = definition.event, triggerDefinitions = null, geofenceName = definition.geofenceName, geofenceLatitude = definition.geofenceLatitude, geofenceLongitude = definition.geofenceLongitude, geofenceRadiusMeters = definition.geofenceRadiusMeters)
    is TriggerDefinition.GeofenceExit -> copy(triggerEvent = definition.event, triggerDefinitions = null, geofenceName = definition.geofenceName, geofenceLatitude = definition.geofenceLatitude, geofenceLongitude = definition.geofenceLongitude, geofenceRadiusMeters = definition.geofenceRadiusMeters)
}

/** Ordered views retain the matching source, while callers deduplicate by the parent rule ID. */
fun List<Automation>.triggerViews(): List<Automation> = flatMap { rule ->
    rule.normalizedTriggers().mapIndexed { index, definition ->
        rule.withTrigger(definition).copy(matchedTriggerIndex = index)
    }
}

fun Automation.hasTrigger(vararg events: TriggerEvent): Boolean = normalizedTriggers().any { it.event in events }

/** Synchronize the legacy primary payload for old readers; null remains null. */
fun Automation.normalizeTriggerStorage(): Automation = triggerDefinitions?.items?.firstOrNull()?.let {
    withTrigger(it).copy(triggerDefinitions = triggerDefinitions)
} ?: this

/** Shared editor validity; imports apply the same ranges at the trust boundary. */
fun TriggerDefinition.isValidTriggerDefinition(): Boolean {
    val r = Automation(id = "", name = "", createdAt = 0).withTrigger(this)
    return when (event) {
        TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$").matches(r.appPackage)
        TriggerEvent.TIME_SCHEDULE -> r.scheduledMinute in 0..1439 && r.scheduledDays.all { it in 1..7 }
        TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> r.batteryLevel in 0..100
        TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> r.lightLux in 0..1_000_000
        TriggerEvent.DEVICE_SHAKE -> r.shakeThreshold.isFinite() && r.shakeThreshold in Automation.MIN_SHAKE_THRESHOLD..Automation.MAX_SHAKE_THRESHOLD
        TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$").matches(r.bluetoothDeviceAddress)
        TriggerEvent.NFC_TAG_SCANNED -> com.flowpilot.app.engine.NfcTagUtils.isValidTagId(r.nfcTagId)
        TriggerEvent.GEOFENCE_ENTER, TriggerEvent.GEOFENCE_EXIT -> com.flowpilot.app.engine.isValidGeofenceConfig(r.geofenceLatitude, r.geofenceLongitude, r.geofenceRadiusMeters)
        TriggerEvent.NOTIFICATION_RECEIVED -> r.notificationAppPackage.isBlank() || Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$").matches(r.notificationAppPackage)
        TriggerEvent.MEDIA_PLAYING, TriggerEvent.MEDIA_PAUSED, TriggerEvent.MEDIA_TRACK_CHANGED -> r.mediaTriggerPackage.isBlank() || Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$").matches(r.mediaTriggerPackage)
        TriggerEvent.SMS_RECEIVED -> r.smsMatchMode != SmsMatchMode.REGEX || runCatching { Regex(r.smsKeyword) }.isSuccess
        else -> true
    }
}

/** System registration IDs are scoped to a definition and saved revision, rejecting stale callbacks. */
fun List<Automation>.geofenceRegistrationViews(): List<Automation> = flatMap { rule ->
    val definitions = rule.normalizedTriggers()
    definitions.mapIndexedNotNull { index, definition ->
        if (definition.event != TriggerEvent.GEOFENCE_ENTER && definition.event != TriggerEvent.GEOFENCE_EXIT) null
        else rule.withTrigger(definition).copy(
            id = if (rule.triggerDefinitions == null) rule.id else "${rule.id}#t${rule.executionRevision}_$index",
            matchedTriggerIndex = index,
        )
    }
}
