package com.flowpilot.app.analysis

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.engine.TriggerTargetMatcher

enum class ConflictConfidence { CERTAIN, POSSIBLE }

data class AutomationConflict(
    val ruleId: String,
    val candidateRuleId: String,
    val candidateRuleName: String,
    val conflictingRuleId: String,
    val conflictingRuleName: String,
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
                val certain = triggerOverlapIsCertain(candidate, other) && candidate.conditions.toSet() == other.conditions.toSet()
                AutomationConflict(
                    ruleId = OPPOSING_STATE_ACTIONS,
                    candidateRuleId = candidate.id,
                    candidateRuleName = candidate.name,
                    conflictingRuleId = other.id,
                    conflictingRuleName = other.name,
                    overlapReason = if (certain) "Trigger target and conditions overlap" else "Trigger and condition overlap cannot be proven disjoint",
                    candidateAction = candidateAction,
                    conflictingAction = otherAction,
                    confidence = if (certain) ConflictConfidence.CERTAIN else ConflictConfidence.POSSIBLE,
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

    private fun sameTriggerTarget(a: Automation, b: Automation): Boolean {
        if (a.triggerEvent != b.triggerEvent) return false
        return when (a.triggerEvent) {
            TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED ->
                TriggerTargetMatcher.wifiTargetsOverlap(a.wifiSsid, b.wifiSsid)
            TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED ->
                TriggerTargetMatcher.bluetoothTargetsMatch(a.bluetoothDeviceAddress, b.bluetoothDeviceAddress)
            TriggerEvent.NFC_TAG_SCANNED -> TriggerTargetMatcher.nfcTargetsMatch(a.nfcTagId, b.nfcTagId)
            TriggerEvent.TIME_SCHEDULE -> TriggerTargetMatcher.scheduleTargetsOverlap(
                a.scheduledMinute, a.scheduledDays, b.scheduledMinute, b.scheduledDays,
            )
            TriggerEvent.NOTIFICATION_RECEIVED ->
                TriggerTargetMatcher.notificationPackageTargetsOverlap(a.notificationAppPackage, b.notificationAppPackage) &&
                    TriggerTargetMatcher.notificationKeywordsOverlap(a.notificationKeyword, b.notificationKeyword)
            else -> triggerTarget(a) == triggerTarget(b)
        }
    }

    private fun triggerOverlapIsCertain(a: Automation, b: Automation): Boolean = when (a.triggerEvent) {
        TriggerEvent.NOTIFICATION_RECEIVED ->
            TriggerTargetMatcher.notificationKeywordsCertainlyOverlap(a.notificationKeyword, b.notificationKeyword)
        else -> true
    }

    private fun triggerTarget(rule: Automation): Any = when (rule.triggerEvent) {
        TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> rule.appPackage
        TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> rule.batteryLevel
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
