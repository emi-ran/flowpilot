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

    /** Not possible on this device at all (e.g. no NFC hardware). */
    UNSUPPORTED,
}

/** Groups actions shown in the picker (future actions slide into existing/new categories). */
@Serializable
enum class ActionCategory(val label: String) {
    NFC("NFC"),
    BATTERY("Battery"),
    SYSTEM("System"),
}

/** A concrete system action a rule can perform. */
@Serializable
enum class ActionType(val label: String, val category: ActionCategory, val requirement: CapabilityRequirement) {
    NFC_ON("Turn NFC on", ActionCategory.NFC, CapabilityRequirement.SHIZUKU),
    NFC_OFF("Turn NFC off", ActionCategory.NFC, CapabilityRequirement.SHIZUKU),
    BATTERY_SAVER_ON("Turn Battery Saver on", ActionCategory.BATTERY, CapabilityRequirement.WRITE_SECURE_SETTINGS),
    BATTERY_SAVER_OFF("Turn Battery Saver off", ActionCategory.BATTERY, CapabilityRequirement.WRITE_SECURE_SETTINGS);

    companion object {
        fun fromId(id: String): ActionType? = entries.firstOrNull { it.name == id }
    }
}

/** Groups trigger choices in the picker. */
@Serializable
enum class TriggerCategory(val label: String) {
    APP("App"),
    SYSTEM("System"),
    TIME("Time"),
}

/** What event on a chosen app triggers a rule. */
@Serializable
enum class TriggerEvent(val label: String, val category: TriggerCategory) {
    APP_OPENED("App opened", TriggerCategory.APP),
    APP_CLOSED("App closed", TriggerCategory.APP),
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
