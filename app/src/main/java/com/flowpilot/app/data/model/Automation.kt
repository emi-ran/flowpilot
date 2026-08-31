package com.flowpilot.app.data.model

import kotlinx.serialization.Serializable

/**
 * Where / how an action can be executed, tied to the permission/access it needs.
 */
@Serializable
enum class CapabilityRequirement {
    /** Plain app with Usage Access only — no extra system powers needed. */
    NONE,

    /** Needs the app to hold android.permission.WRITE_SECURE_SETTINGS (via ADB `pm grant`). */
    WRITE_SECURE_SETTINGS,

    /** Needs Shizuku running (and granted to this app). */
    SHIZUKU,

    /** Needs Android's notification runtime permission on Android 13+. */
    NOTIFICATIONS,

    /** Needs a device vibrator. */
    VIBRATION,

    /** Not possible on this device at all (e.g. no NFC hardware). */
    UNSUPPORTED,
}

/** Groups actions shown in the picker (future actions slide into existing/new categories). */
@Serializable
enum class ActionCategory(val label: String) {
    NFC("NFC"),
    BATTERY("Battery"),
    ALERTS("Alerts"),
    AUDIO("Audio"),
    APPS_LINKS("Apps & Links"),
}

/** A concrete system action a rule can perform. */
@Serializable
enum class ActionType(val label: String, val category: ActionCategory, val requirement: CapabilityRequirement) {
    NFC_ON("Turn NFC on", ActionCategory.NFC, CapabilityRequirement.SHIZUKU),
    NFC_OFF("Turn NFC off", ActionCategory.NFC, CapabilityRequirement.SHIZUKU),
    BATTERY_SAVER_ON("Turn Battery Saver on", ActionCategory.BATTERY, CapabilityRequirement.WRITE_SECURE_SETTINGS),
    BATTERY_SAVER_OFF("Turn Battery Saver off", ActionCategory.BATTERY, CapabilityRequirement.WRITE_SECURE_SETTINGS),
    SHOW_NOTIFICATION("Show notification", ActionCategory.ALERTS, CapabilityRequirement.NOTIFICATIONS),
    VIBRATE("Vibrate", ActionCategory.ALERTS, CapabilityRequirement.VIBRATION),
    PLAY_SOUND("Play sound", ActionCategory.AUDIO, CapabilityRequirement.NONE),
    SET_MEDIA_VOLUME("Set media volume", ActionCategory.AUDIO, CapabilityRequirement.NONE),
    SPEAK_TEXT("Speak text (TTS)", ActionCategory.AUDIO, CapabilityRequirement.NONE),
    LAUNCH_APP("Launch app", ActionCategory.APPS_LINKS, CapabilityRequirement.NONE),
    OPEN_URL("Open URL", ActionCategory.APPS_LINKS, CapabilityRequirement.NONE);

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
}

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
    TIME_SCHEDULE("At scheduled time", TriggerCategory.TIME);

    companion object {
        fun fromId(id: String): TriggerEvent? = entries.firstOrNull { it.name == id }
    }
}

/**
 * A single automation rule.
 *
 * @property id stable identifier
 * @property name user-facing name
 * @property enabled whether the engine considers this rule
 * @property triggerEvent opened / closed
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
    val appPackage: String,
    val appName: String = "",
    val scheduledMinute: Int = 0,
    val scheduledDays: Set<Int> = emptySet(),
    val batteryLevel: Int = 50,
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
    val action: ActionType = ActionType.NFC_ON,
    val actions: List<ActionType> = emptyList(),
    val createdAt: Long,
    val lastTriggeredAt: Long = 0L,
) {
    val effectiveActions: List<ActionType>
        get() = if (actions.isNotEmpty()) actions else listOf(action)

    val actionSummary: String
        get() = effectiveActions.joinToString(" + ") { it.label }
}
