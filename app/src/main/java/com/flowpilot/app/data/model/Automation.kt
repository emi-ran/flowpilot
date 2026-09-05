package com.flowpilot.app.data.model

import kotlinx.serialization.Serializable
import com.flowpilot.app.data.security.SecretCipher

/**
 * Where / how an action can be executed, tied to the permission/access it needs.
 */
@Serializable
enum class CapabilityRequirement {
    /** Plain app with Usage Access only — no extra system powers needed. */
    NONE,

    /** Needs user-grantable special access to modify system settings (android.permission.WRITE_SETTINGS). */
    WRITE_SETTINGS,

    /** Needs the app to hold android.permission.WRITE_SECURE_SETTINGS (via ADB `pm grant`). */
    WRITE_SECURE_SETTINGS,

    /** Needs Shizuku running (and granted to this app). */
    SHIZUKU,

    /** Needs Android's notification runtime permission on Android 13+. */
    NOTIFICATIONS,

    /** Needs user-grantable Notification Policy Access (Do Not Disturb access). */
    NOTIFICATION_POLICY,

    /** Needs a device vibrator. */
    VIBRATION,

    /** Needs android.permission.CALL_PHONE runtime permission to initiate phone calls directly. */
    CALL_PHONE,

    /** Needs android.permission.SEND_SMS runtime permission to send SMS directly. */
    SEND_SMS,

    /** Not possible on this device at all (e.g. no NFC hardware). */
    UNSUPPORTED,
}

/** Groups actions shown in the picker (future actions slide into existing/new categories). */
@Serializable
enum class ActionCategory(val label: String) {
    CONNECTIVITY("Connectivity"),
    NFC("NFC"),
    BATTERY("Battery"),
    DISPLAY("Display"),
    ALERTS("Alerts"),
    AUDIO("Audio"),
    APPS_LINKS("Apps & Links"),
    CLOCK("Clock"),
    PHONE("Phone"),
    SMS("SMS"),
}

/** A concrete system action a rule can perform. */
@Serializable
enum class ActionType(val label: String, val category: ActionCategory, val requirement: CapabilityRequirement) {
    BLUETOOTH_ON("Turn Bluetooth on", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    BLUETOOTH_OFF("Turn Bluetooth off", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    NFC_ON("Turn NFC on", ActionCategory.NFC, CapabilityRequirement.SHIZUKU),
    NFC_OFF("Turn NFC off", ActionCategory.NFC, CapabilityRequirement.SHIZUKU),
    BATTERY_SAVER_ON("Turn Battery Saver on", ActionCategory.BATTERY, CapabilityRequirement.WRITE_SECURE_SETTINGS),
    BATTERY_SAVER_OFF("Turn Battery Saver off", ActionCategory.BATTERY, CapabilityRequirement.WRITE_SECURE_SETTINGS),
    DARK_THEME_ON("Turn Dark theme on", ActionCategory.DISPLAY, CapabilityRequirement.SHIZUKU),
    DARK_THEME_OFF("Turn Dark theme off", ActionCategory.DISPLAY, CapabilityRequirement.SHIZUKU),
    AUTO_ROTATE_ON("Turn Auto-rotate on", ActionCategory.DISPLAY, CapabilityRequirement.WRITE_SETTINGS),
    AUTO_ROTATE_OFF("Turn Auto-rotate off", ActionCategory.DISPLAY, CapabilityRequirement.WRITE_SETTINGS),
    SHOW_NOTIFICATION("Show notification", ActionCategory.ALERTS, CapabilityRequirement.NOTIFICATIONS),
    DND_ON("Turn Do Not Disturb on", ActionCategory.ALERTS, CapabilityRequirement.NOTIFICATION_POLICY),
    DND_OFF("Turn Do Not Disturb off", ActionCategory.ALERTS, CapabilityRequirement.NOTIFICATION_POLICY),
    VIBRATE("Vibrate", ActionCategory.ALERTS, CapabilityRequirement.VIBRATION),
    SOUND_PROFILE_NORMAL("Sound profile: Normal", ActionCategory.AUDIO, CapabilityRequirement.NOTIFICATION_POLICY),
    SOUND_PROFILE_VIBRATE("Sound profile: Vibrate", ActionCategory.AUDIO, CapabilityRequirement.NOTIFICATION_POLICY),
    SOUND_PROFILE_SILENT("Sound profile: Silent", ActionCategory.AUDIO, CapabilityRequirement.NOTIFICATION_POLICY),
    PLAY_SOUND("Play sound", ActionCategory.AUDIO, CapabilityRequirement.NONE),
    SET_MEDIA_VOLUME("Set media volume", ActionCategory.AUDIO, CapabilityRequirement.NONE),
    SPEAK_TEXT("Speak text (TTS)", ActionCategory.AUDIO, CapabilityRequirement.NONE),
    CREATE_ALARM("Create alarm", ActionCategory.CLOCK, CapabilityRequirement.NONE),
    START_TIMER("Start timer", ActionCategory.CLOCK, CapabilityRequirement.NONE),
    LAUNCH_APP("Launch app", ActionCategory.APPS_LINKS, CapabilityRequirement.NONE),
    OPEN_URL("Open URL", ActionCategory.APPS_LINKS, CapabilityRequirement.NONE),
    HTTP_WEBHOOK("Send HTTP webhook", ActionCategory.APPS_LINKS, CapabilityRequirement.NONE),
    OPEN_DIALER("Open dialer", ActionCategory.PHONE, CapabilityRequirement.NONE),
    DIAL_NUMBER("Dial phone number", ActionCategory.PHONE, CapabilityRequirement.NONE),
    CALL_NUMBER("Call phone number directly", ActionCategory.PHONE, CapabilityRequirement.CALL_PHONE),
    SEND_SMS("Send SMS directly", ActionCategory.SMS, CapabilityRequirement.SEND_SMS),
    DRAFT_SMS("Prepare SMS draft", ActionCategory.SMS, CapabilityRequirement.NONE),
    MOBILE_DATA_ON("Turn Mobile Data on", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    MOBILE_DATA_OFF("Turn Mobile Data off", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    WIFI_ON("Turn Wi-Fi on", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    WIFI_OFF("Turn Wi-Fi off", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    AIRPLANE_MODE_ON("Turn Airplane mode on", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    AIRPLANE_MODE_OFF("Turn Airplane mode off", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    TORCH_ON("Turn Flashlight on", ActionCategory.DISPLAY, CapabilityRequirement.NONE),
    TORCH_OFF("Turn Flashlight off", ActionCategory.DISPLAY, CapabilityRequirement.NONE),
    SET_SCREEN_BRIGHTNESS("Set screen brightness", ActionCategory.DISPLAY, CapabilityRequirement.WRITE_SETTINGS),
    AUTO_BRIGHTNESS_ON("Turn Auto-brightness on", ActionCategory.DISPLAY, CapabilityRequirement.WRITE_SETTINGS),
    AUTO_BRIGHTNESS_OFF("Turn Auto-brightness off", ActionCategory.DISPLAY, CapabilityRequirement.WRITE_SETTINGS),
    LOCK_SCREEN("Lock screen", ActionCategory.DISPLAY, CapabilityRequirement.SHIZUKU),
    FORCE_STOP_APP("Force stop app", ActionCategory.APPS_LINKS, CapabilityRequirement.SHIZUKU),
    LOCATION_ON("Turn Location on", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU),
    LOCATION_OFF("Turn Location off", ActionCategory.CONNECTIVITY, CapabilityRequirement.SHIZUKU);

    companion object {
        fun fromId(id: String): ActionType? = entries.firstOrNull { it.name == id }
    }
}

/** Groups trigger choices in the picker. */
@Serializable
enum class TriggerCategory(val label: String) {
    APP("App"),
    POWER("Power"),
    DISPLAY("Display"),
    TIME("Time"),
    NETWORK("Network"),
    BLUETOOTH("Bluetooth"),
    NFC_TAG("NFC"),
    NOTIFICATION("Notification"),
    PHONE("Phone"),
    SMS("SMS"),
    MOTION("Motion"),
}

@Serializable
enum class ConditionType(val label: String) {
    BATTERY_BELOW("Battery below level"),
    BATTERY_ABOVE("Battery above level"),
    CHARGER_CONNECTED("Charger connected"),
    CHARGER_DISCONNECTED("Charger disconnected"),
    SCREEN_ON("Screen is on"),
    SCREEN_OFF("Screen is off"),
    WIFI_CONNECTED("Connected to Wi-Fi"),
    WIFI_DISCONNECTED("Disconnected from Wi-Fi"),
    TIME_BETWEEN("Time is between"),
    DAYS_OF_WEEK("Days of week"),
}

@Serializable
data class RuleCondition(
    val type: ConditionType,
    val batteryLevel: Int = 50,
    val wifiSsid: String = "",
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
    val days: Set<Int> = emptySet(),
)

@Serializable
enum class VibrationPattern(val label: String) {
    PULSE("Pulse"),
    DOUBLE_TAP("Double tap"),
    ALERT("Alert"),
    HEARTBEAT("Heartbeat"),
    TRIPLE_TAP("Triple tap"),
    SOS("SOS"),
}

@Serializable
enum class SoundPreset(val label: String) {
    NOTIFICATION("Notification sound"),
    ALARM("Alarm sound"),
    RINGTONE("Ringtone"),
    CUSTOM("Custom audio file"),
}

@Serializable
data class TtsVoiceOption(
    val name: String,
    val locale: String,
    val displayName: String,
)

/** What event on a chosen app triggers a rule. */
@Serializable
enum class TriggerEvent(val label: String, val category: TriggerCategory) {
    APP_OPENED("App opened", TriggerCategory.APP),
    APP_CLOSED("App closed", TriggerCategory.APP),
    CHARGER_CONNECTED("Charger connected", TriggerCategory.POWER),
    CHARGER_DISCONNECTED("Charger disconnected", TriggerCategory.POWER),
    BATTERY_BELOW("Battery below level", TriggerCategory.POWER),
    BATTERY_ABOVE("Battery above level", TriggerCategory.POWER),
    SCREEN_ON("Screen turned on", TriggerCategory.DISPLAY),
    SCREEN_OFF("Screen turned off", TriggerCategory.DISPLAY),
    TIME_SCHEDULE("At scheduled time", TriggerCategory.TIME),
    WIFI_CONNECTED("Connected to Wi-Fi", TriggerCategory.NETWORK),
    WIFI_DISCONNECTED("Disconnected from Wi-Fi", TriggerCategory.NETWORK),
    BLUETOOTH_CONNECTED("Bluetooth device connected", TriggerCategory.BLUETOOTH),
    BLUETOOTH_DISCONNECTED("Bluetooth device disconnected", TriggerCategory.BLUETOOTH),
    NFC_TAG_SCANNED("NFC tag scanned", TriggerCategory.NFC_TAG),
    NOTIFICATION_RECEIVED("Notification received", TriggerCategory.NOTIFICATION),
    CALL_RINGING("Incoming call ringing", TriggerCategory.PHONE),
    CALL_ANSWERED("Call active / answered", TriggerCategory.PHONE),
    CALL_OUTGOING("Outgoing call started", TriggerCategory.PHONE),
    CALL_ENDED("Call ended", TriggerCategory.PHONE),
    DEVICE_FLIPPED_DOWN("Device flipped face down", TriggerCategory.MOTION),
    DEVICE_FLIPPED_UP("Device flipped face up", TriggerCategory.MOTION),
    DEVICE_SHAKE("Device shaken", TriggerCategory.MOTION),
    DEVICE_UNLOCKED("Device unlocked", TriggerCategory.DISPLAY),
    LIGHT_BELOW("Ambient light below level", TriggerCategory.DISPLAY),
    LIGHT_ABOVE("Ambient light above level", TriggerCategory.DISPLAY),
    SMS_RECEIVED("SMS received", TriggerCategory.SMS);

    companion object {
        fun fromId(id: String): TriggerEvent? = entries.firstOrNull { it.name == id }
    }
}

/** Content match criteria for incoming SMS triggers. */
@Serializable
enum class SmsMatchMode(val label: String) {
    CONTAINS("Contains keyword"),
    EQUALS("Equals text exactly"),
    STARTS_WITH("Starts with text"),
    REGEX("Matches regex"),
    ANY("Any SMS"),
}

/**
 * A single automation rule.
 *
 * @property id stable identifier
 * @property name user-facing name
 * @property enabled whether the engine considers this rule
 * @property triggerEvent opened / closed / call / etc.
 * @property appPackage package that must open/close
 * @property appName cached display name for UI
 * @property action what to do when triggered
 * @property createdAt epoch millis
 * @property lastTriggeredAt epoch millis, 0 if never
 */
@Serializable
data class Automation(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val triggerEvent: TriggerEvent = TriggerEvent.APP_OPENED,
    val appPackage: String = "",
    val appName: String = "",
    val scheduledMinute: Int = 0,
    val scheduledDays: Set<Int> = emptySet(),
    val batteryLevel: Int = 50,
    val wifiSsid: String = "",
    /** Bonded Bluetooth device selected for Bluetooth trigger matching. */
    val bluetoothDeviceAddress: String = "",
    /** Cached Bluetooth device name for readable rule UI; address remains matcher. */
    val bluetoothDeviceName: String = "",
    /** Normalized NFC tag ID (hex string, uppercase, without colons) selected for NFC tag trigger matching. */
    val nfcTagId: String = "",
    val notificationAppPackage: String = "",
    val notificationAppName: String = "",
    val notificationKeyword: String = "",
    val conditions: List<RuleCondition> = emptyList(),
    val notificationTitle: String = "FlowPilot",
    val notificationBody: String = "Automation ran",
    val vibrationPattern: VibrationPattern = VibrationPattern.PULSE,
    val vibrationDurationMs: Int = 220,
    val vibrationAmplitude: Int = 180,
    val mediaVolumePercent: Int = 50,
    val soundPreset: SoundPreset = SoundPreset.NOTIFICATION,
    val soundUri: String = "",
    val soundName: String = "",
    val soundDurationMs: Int = 3_000,
    val launchPackage: String = "",
    val launchAppName: String = "",
    val url: String = "",
    val ttsText: String = "",
    val ttsVoiceName: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsAudioFileName: String = "",
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val alarmMessage: String = "",
    val timerDurationSeconds: Int = 300,
    val timerMessage: String = "",
    val webhookMethod: String = "POST",
    val webhookUrl: String = "",
    val webhookHeaders: String = "",
    val webhookBody: String = "",
    val webhookTimeoutSeconds: Int = 10,
    /** Telephone number used for DIAL_NUMBER and CALL_NUMBER actions. */
    val phoneNumber: String = "",
    /** Sender filter for SMS_RECEIVED trigger (empty matches any). */
    val smsSenderFilter: String = "",
    /** Matching mode for SMS_RECEIVED trigger text. */
    val smsMatchMode: SmsMatchMode = SmsMatchMode.CONTAINS,
    /** Keyword or pattern for SMS_RECEIVED trigger. */
    val smsKeyword: String = "",
    /** Recipient telephone number or variable (${sms.sender}) for SEND_SMS / DRAFT_SMS actions. */
    val smsRecipient: String = "",
    /** Message text template for SEND_SMS / DRAFT_SMS actions. */
    val smsMessage: String = "",
    val action: ActionType = ActionType.NFC_ON,
    val actions: List<ActionType> = emptyList(),
    /** Per-action optional delay in seconds (0..300), sequential with matching index in actions. */
    val actionDelays: List<Int> = emptyList(),
    /** Cooldown duration in minutes (0 means disabled, stored values clamp to 1440). Blocks automatic trigger evaluation when (now - lastTriggeredAt) < cooldown. */
    val cooldownMinutes: Int = 0,
    /** Whether motion/flip triggers should listen and evaluate even when the device screen is off. */
    val flipScreenOffDetection: Boolean = false,
    /** Threshold in lux for LIGHT_BELOW / LIGHT_ABOVE triggers. */
    val lightLux: Int = 10,
    /** Screen brightness percent (0..100) for SET_SCREEN_BRIGHTNESS action. */
    val screenBrightnessPercent: Int = 50,
    /** Target package for FORCE_STOP_APP action. */
    val forceStopPackage: String = "",
    /** Cached target app display name for FORCE_STOP_APP action. */
    val forceStopAppName: String = "",
    val createdAt: Long,
    val lastTriggeredAt: Long = 0L,
) {
    /** Legacy SMS-generated names exposed sender filters; keep custom names unchanged. */
    val normalizedName: String
        get() = if (triggerEvent == TriggerEvent.SMS_RECEIVED &&
            LEGACY_SMS_GENERATED_NAME.matches(name)
        ) {
            "SMS Received · ${effectiveActions.joinToString(" + ") { it.label }}"
        } else {
            name
        }

    val effectiveActions: List<ActionType>
        get() = if (actions.isNotEmpty()) actions else listOf(action)

    val effectiveActionDelays: List<Int>
        get() {
            val count = effectiveActions.size
            return List(count) { i ->
                actionDelays.getOrNull(i)?.coerceIn(0, 300) ?: 0
            }
        }

    val effectiveCooldownMinutes: Int
        get() = cooldownMinutes.coerceIn(0, 1440)

    fun isCoolingDown(nowMs: Long): Boolean {
        val cdMinutes = effectiveCooldownMinutes
        if (cdMinutes <= 0 || lastTriggeredAt <= 0L) return false
        val elapsedMs = nowMs - lastTriggeredAt
        if (elapsedMs < 0L) return true // Future timestamp safety
        val cooldownMs = cdMinutes * 60_000L
        return elapsedMs < cooldownMs
    }

    val actionSummary: String
        get() {
            val delays = effectiveActionDelays
            return effectiveActions.mapIndexed { idx, act ->
                val delaySec = delays.getOrElse(idx) { 0 }
                if (delaySec > 0) "${act.label} (+${delaySec}s)" else act.label
            }.joinToString(" + ")
        }

    /**
     * Returns a copy with sensitive webhook fields encrypted for storage.
     */
    fun withEncryptedSecrets(): Automation {
        val needsEncryption = webhookUrl.isNotEmpty() || webhookHeaders.isNotEmpty() || webhookBody.isNotEmpty()
        if (!needsEncryption) return this
        return copy(
            webhookUrl = SecretCipher.encrypt(webhookUrl),
            webhookHeaders = SecretCipher.encrypt(webhookHeaders),
            webhookBody = SecretCipher.encrypt(webhookBody),
        )
    }

    /**
     * Returns a copy with sensitive webhook fields decrypted for runtime/UI usage.
     */
    fun withDecryptedSecrets(): Automation {
        val needsDecryption = SecretCipher.isEncrypted(webhookUrl) ||
            SecretCipher.isEncrypted(webhookHeaders) ||
            SecretCipher.isEncrypted(webhookBody)
        if (!needsDecryption) return this
        return copy(
            webhookUrl = SecretCipher.decrypt(webhookUrl),
            webhookHeaders = SecretCipher.decrypt(webhookHeaders),
            webhookBody = SecretCipher.decrypt(webhookBody),
        )
    }

    private companion object {
        val LEGACY_SMS_GENERATED_NAME = Regex("SMS from .+ · .+")
    }
}
