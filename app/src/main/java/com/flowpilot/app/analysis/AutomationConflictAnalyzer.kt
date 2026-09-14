package com.flowpilot.app.analysis

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent

enum class ConflictConfidence { CERTAIN, POSSIBLE }

data class AutomationConflict(
    val ruleId: String,
    val conflictingRuleId: String,
    val overlapReason: String,
    val candidateAction: ActionType,
    val conflictingAction: ActionType,
    val confidence: ConflictConfidence,
)

object AutomationConflictAnalyzer {
    const val OPPOSING_STATE_ACTIONS = "opposing-state-actions"

    fun analyze(candidate: Automation, rules: List<Automation>): List<AutomationConflict> = rules.asSequence()
        .filter { it.enabled && it.id != candidate.id }
        .filter { sameTriggerTarget(candidate, it) }
        .filterNot { conditionsAreDisjoint(candidate.conditions, it.conditions) }
        .flatMap { other ->
            opposingActions(candidate.effectiveActions, other.effectiveActions).map { (candidateAction, otherAction) ->
                val exactConditions = candidate.conditions.toSet() == other.conditions.toSet()
                AutomationConflict(
                    ruleId = OPPOSING_STATE_ACTIONS,
                    conflictingRuleId = other.id,
                    overlapReason = if (exactConditions) "Exact trigger target and conditions overlap" else "Exact trigger target; condition overlap cannot be proven disjoint",
                    candidateAction = candidateAction,
                    conflictingAction = otherAction,
                    confidence = if (exactConditions) ConflictConfidence.CERTAIN else ConflictConfidence.POSSIBLE,
                )
            }
        }
        .toList()

    private fun opposingActions(left: List<ActionType>, right: List<ActionType>): Sequence<Pair<ActionType, ActionType>> =
        left.asSequence().flatMap { candidate ->
            val candidateState = actionState(candidate) ?: return@flatMap emptySequence()
            right.asSequence().filter { other ->
                val otherState = actionState(other)
                otherState != null && candidateState.first == otherState.first && candidateState.second != otherState.second
            }.map { candidate to it }
        }

    private fun actionState(action: ActionType): Pair<String, String>? = when (action) {
        ActionType.WIFI_ON -> "wifi" to "on"
        ActionType.WIFI_OFF -> "wifi" to "off"
        ActionType.BLUETOOTH_ON -> "bluetooth" to "on"
        ActionType.BLUETOOTH_OFF -> "bluetooth" to "off"
        ActionType.MOBILE_DATA_ON -> "mobile-data" to "on"
        ActionType.MOBILE_DATA_OFF -> "mobile-data" to "off"
        ActionType.AIRPLANE_MODE_ON -> "airplane-mode" to "on"
        ActionType.AIRPLANE_MODE_OFF -> "airplane-mode" to "off"
        ActionType.NFC_ON -> "nfc" to "on"
        ActionType.NFC_OFF -> "nfc" to "off"
        ActionType.BATTERY_SAVER_ON -> "battery-saver" to "on"
        ActionType.BATTERY_SAVER_OFF -> "battery-saver" to "off"
        ActionType.DARK_THEME_ON -> "dark-theme" to "on"
        ActionType.DARK_THEME_OFF -> "dark-theme" to "off"
        ActionType.AUTO_ROTATE_ON -> "auto-rotate" to "on"
        ActionType.AUTO_ROTATE_OFF -> "auto-rotate" to "off"
        ActionType.DND_ON -> "dnd" to "on"
        ActionType.DND_OFF -> "dnd" to "off"
        ActionType.SOUND_PROFILE_NORMAL -> "sound-profile" to "normal"
        ActionType.SOUND_PROFILE_VIBRATE -> "sound-profile" to "vibrate"
        ActionType.SOUND_PROFILE_SILENT -> "sound-profile" to "silent"
        ActionType.TORCH_ON -> "torch" to "on"
        ActionType.TORCH_OFF -> "torch" to "off"
        ActionType.LOCATION_ON -> "location" to "on"
        ActionType.LOCATION_OFF -> "location" to "off"
        else -> null
    }

    private fun sameTriggerTarget(a: Automation, b: Automation): Boolean =
        a.triggerEvent == b.triggerEvent && triggerTarget(a) == triggerTarget(b)

    private fun triggerTarget(rule: Automation): Any = when (rule.triggerEvent) {
        TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> rule.appPackage
        TriggerEvent.TIME_SCHEDULE -> rule.scheduledMinute to rule.scheduledDays
        TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> rule.batteryLevel
        TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED -> rule.wifiSsid.trim()
        TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> rule.bluetoothDeviceAddress.uppercase()
        TriggerEvent.NFC_TAG_SCANNED -> rule.nfcTagId.uppercase()
        TriggerEvent.NOTIFICATION_RECEIVED -> rule.notificationAppPackage to rule.notificationKeyword
        TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> rule.lightLux
        TriggerEvent.SMS_RECEIVED -> listOf(rule.smsSenderFilter, rule.smsMatchMode.name, rule.smsKeyword)
        TriggerEvent.GEOFENCE_ENTER, TriggerEvent.GEOFENCE_EXIT -> listOf(rule.geofenceLatitude, rule.geofenceLongitude, rule.geofenceRadiusMeters)
        else -> Unit
    }

    private fun conditionsAreDisjoint(a: List<RuleCondition>, b: List<RuleCondition>): Boolean {
        val typesA = a.map { it.type }.toSet()
        val typesB = b.map { it.type }.toSet()
        if (ConditionType.CHARGER_CONNECTED in typesA && ConditionType.CHARGER_DISCONNECTED in typesB ||
            ConditionType.CHARGER_DISCONNECTED in typesA && ConditionType.CHARGER_CONNECTED in typesB ||
            ConditionType.SCREEN_ON in typesA && ConditionType.SCREEN_OFF in typesB ||
            ConditionType.SCREEN_OFF in typesA && ConditionType.SCREEN_ON in typesB
        ) return true
        return false
    }
}
