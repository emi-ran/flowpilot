package com.flowpilot.app.data.backup

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.engine.NfcTagUtils
import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.data.security.SecretCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Applied again at the repository boundary, including callers that bypass JSON parsing. */
internal object ImportValidator {
    const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
    const val MAX_PLAINTEXT_BYTES = 3 * 1024 * 1024
    const val MAX_RULES = 1000
    const val MAX_ITEMS = 100
    const val MAX_STRING = 64 * 1024

    private val json = Json { encodeDefaults = true }
    private val safeCacheName = Regex("^tts_[A-Za-z0-9-]+_[0-9a-f]{16}\\.wav$")
    private val packageName = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    private val bluetoothAddress = Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")

    private fun webUri(value: String, httpsOnly: Boolean = false): Boolean = try {
        val uri = java.net.URI(value.trim())
        uri.scheme?.lowercase() in (if (httpsOnly) setOf("https") else setOf("http", "https")) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    fun validate(rules: List<Automation>) {
        require(rules.size <= MAX_RULES) { "Too many rules" }
        require(rules.all { it.id.isNotBlank() } && rules.map { it.id }.toSet().size == rules.size) {
            "Blank or duplicate rule ID"
        }
        fun checkStrings(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is JsonObject -> element.values.forEach(::checkStrings)
                is JsonArray -> element.forEach(::checkStrings)
                is JsonPrimitive -> if (element.isString) require(element.content.length <= MAX_STRING) { "Field too large" }
            }
        }
        rules.forEach { r ->
            checkStrings(json.encodeToJsonElement(Automation.serializer(), r))
            require(r.actions.size <= MAX_ITEMS && r.actionDelays.size <= MAX_ITEMS && r.conditions.size <= MAX_ITEMS) { "Too many rule items" }
            require(r.actionDelays.size <= r.effectiveActions.size && r.actionDelays.all { it in 0..300 }) { "Invalid action delays" }
            when (r.triggerEvent) {
                TriggerEvent.TIME_SCHEDULE -> require(r.scheduledMinute in 0..1439 && r.scheduledDays.all { it in 1..7 }) { "Invalid schedule" }
                TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> require(r.batteryLevel in 0..100) { "Invalid battery level" }
                TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> require(r.lightLux in 0..1000000) { "Invalid light level" }
                TriggerEvent.GEOFENCE_ENTER, TriggerEvent.GEOFENCE_EXIT -> require(r.geofenceLatitude.isFinite() && r.geofenceLatitude in -90.0..90.0 && r.geofenceLongitude.isFinite() && r.geofenceLongitude in -180.0..180.0 && r.geofenceRadiusMeters in 50..1000) { "Invalid location range" }
                else -> Unit
            }
            r.conditions.forEach { condition ->
                when (condition.type) {
                    com.flowpilot.app.data.model.ConditionType.BATTERY_BELOW, com.flowpilot.app.data.model.ConditionType.BATTERY_ABOVE -> require(condition.batteryLevel in 0..100) { "Invalid battery condition" }
                    com.flowpilot.app.data.model.ConditionType.TIME_BETWEEN -> require(condition.startMinute in 0..1439 && condition.endMinute in 0..1439) { "Invalid time condition" }
                    com.flowpilot.app.data.model.ConditionType.DAYS_OF_WEEK -> require(condition.days.all { it in 1..7 }) { "Invalid days condition" }
                    else -> Unit
                }
            }
            val actions = r.effectiveActions
            if (ActionType.VIBRATE in actions) require(r.vibrationDurationMs in 1..60000 && r.vibrationAmplitude in 1..255) { "Invalid vibration" }
            if (ActionType.SET_MEDIA_VOLUME in actions) require(r.mediaVolumePercent in 0..100) { "Invalid volume" }
            if (ActionType.PLAY_SOUND in actions) require(r.soundDurationMs in 1..60000) { "Invalid sound duration" }
            if (ActionType.SPEAK_TEXT in actions || r.ttsAudioFileName.isNotEmpty()) require(r.ttsSpeechRate.isFinite() && r.ttsSpeechRate in 0.1f..4f) { "Invalid speech rate" }
            if (ActionType.CREATE_ALARM in actions) require(r.alarmHour in 0..23 && r.alarmMinute in 0..59) { "Invalid alarm" }
            if (ActionType.START_TIMER in actions) require(r.timerDurationSeconds in 1..86400) { "Invalid timer" }
            if (ActionType.SET_SCREEN_BRIGHTNESS in actions) require(r.screenBrightnessPercent in 0..100) { "Invalid brightness" }
            require(r.cooldownMinutes in 0..1440) { "Invalid cooldown" }
            require(r.ttsAudioFileName.isEmpty() || safeCacheName.matches(r.ttsAudioFileName)) { "Unsafe cache reference" }
            require(listOf(r.webhookUrl, r.webhookHeaders, r.webhookBody).none { SecretCipher.isEncrypted(it) }) { "Device-local secret references are not portable" }
            when (r.triggerEvent) {
                TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> require(packageName.matches(r.appPackage)) { "Invalid trigger package" }
                TriggerEvent.NOTIFICATION_RECEIVED -> require(r.notificationAppPackage.isBlank() || packageName.matches(r.notificationAppPackage)) { "Invalid notification package" }
                TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> require(bluetoothAddress.matches(r.bluetoothDeviceAddress)) { "Invalid Bluetooth address" }
                TriggerEvent.NFC_TAG_SCANNED -> require(NfcTagUtils.isValidTagId(r.nfcTagId)) { "Invalid NFC tag" }
                else -> Unit
            }
            if (ActionType.LAUNCH_APP in actions) require(packageName.matches(r.launchPackage)) { "Invalid launch package" }
            if (ActionType.FORCE_STOP_APP in actions) require(packageName.matches(r.forceStopPackage)) { "Invalid force-stop package" }
            if (ActionType.OPEN_URL in actions) require(webUri(r.url)) { "Invalid URL" }
            if (ActionType.DIAL_NUMBER in actions || ActionType.CALL_NUMBER in actions) require(r.phoneNumber.isNotBlank()) { "Missing phone number" }
            if (ActionType.SEND_SMS in actions) require(r.smsRecipient.isNotBlank() && r.smsMessage.isNotBlank()) { "Missing SMS fields" }
            if (ActionType.DRAFT_SMS in actions) require(r.smsRecipient.isNotBlank() || r.smsMessage.isNotBlank()) { "Missing SMS fields" }
            if (ActionType.PLAY_SOUND in actions && r.soundPreset == SoundPreset.CUSTOM && r.soundUri.isNotEmpty()) {
                val uri = try { java.net.URI(r.soundUri) } catch (_: Exception) { null }
                require(uri != null && uri.scheme in setOf("content", "android.resource") && !uri.authority.isNullOrBlank()) { "Unsafe sound reference" }
            }
            // Sanitized exports intentionally omit webhook secrets; nonempty values must be safe.
            if (ActionType.HTTP_WEBHOOK in actions) {
                require(r.webhookMethod.trim().uppercase() in WebhookExecutor.ALLOWED_METHODS && r.webhookTimeoutSeconds in 1..300) { "Invalid webhook settings" }
                require(r.webhookUrl.isEmpty() || webUri(r.webhookUrl, httpsOnly = true)) { "Invalid webhook URL" }
                require(WebhookExecutor.validateHeaders(r.webhookHeaders) == null) { "Invalid webhook headers" }
                require(r.webhookMethod.trim().uppercase() in setOf("POST", "PUT", "PATCH") || r.webhookBody.isEmpty()) { "Invalid webhook body" }
            }
            if (r.triggerEvent == TriggerEvent.SMS_RECEIVED && r.smsMatchMode == com.flowpilot.app.data.model.SmsMatchMode.REGEX) {
                try { Regex(r.smsKeyword) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid SMS pattern") }
            }
        }
    }
}
