package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent

/** Current live environmental state evaluated against rule conditions. */
data class LiveSystemState(
    val batteryPercent: Int? = null,
    val isChargerConnected: Boolean? = null,
    val isScreenOn: Boolean? = null,
    val connectedWifiSsid: String? = null,
)

/** A foreground-app transition event fed into the engine. */
enum class AppEvent {
    OPENED,
    CLOSED,
}

/** Outcome of evaluating rules against an event. */
data class EvaluationResult(
    /** Rules that matched and are not deduped; these should be executed. */
    val toExecute: List<Automation>,
    /** All enabled rules that matched this app+event, including those suppressed by dedupe. */
    val matched: List<Automation>,
)

/**
 * Pure rule-matching + condition evaluation + dedup logic. Unit-testable.
 */
object RuleEvaluator {

    /**
     * Checks if all configured conditions on a rule match the live state (AND semantics).
     * If rule has no conditions, returns true (existing behavior unchanged).
     */
    fun matchesConditions(conditions: List<RuleCondition>, state: LiveSystemState): Boolean {
        if (conditions.isEmpty()) return true
        return conditions.all { condition ->
            when (condition.type) {
                ConditionType.BATTERY_BELOW -> {
                    state.batteryPercent != null && state.batteryPercent <= condition.batteryLevel
                }
                ConditionType.BATTERY_ABOVE -> {
                    state.batteryPercent != null && state.batteryPercent >= condition.batteryLevel
                }
                ConditionType.CHARGER_CONNECTED -> {
                    state.isChargerConnected == true
                }
                ConditionType.CHARGER_DISCONNECTED -> {
                    state.isChargerConnected == false
                }
                ConditionType.SCREEN_ON -> {
                    state.isScreenOn == true
                }
                ConditionType.SCREEN_OFF -> {
                    state.isScreenOn == false
                }
                ConditionType.WIFI_CONNECTED -> {
                    if (state.connectedWifiSsid == null) false
                    else if (condition.wifiSsid.isBlank()) true
                    else state.connectedWifiSsid.trim().equals(condition.wifiSsid.trim(), ignoreCase = true)
                }
                ConditionType.WIFI_DISCONNECTED -> {
                    if (state.connectedWifiSsid == null) true
                    else if (condition.wifiSsid.isBlank()) false
                    else !state.connectedWifiSsid.trim().equals(condition.wifiSsid.trim(), ignoreCase = true)
                }
            }
        }
    }

    fun evaluateCharger(
        rules: List<Automation>,
        event: ChargerEvent,
        liveState: LiveSystemState = LiveSystemState(),
    ): List<Automation> {
        val triggerEvent = when (event) {
            ChargerEvent.CONNECTED -> TriggerEvent.CHARGER_CONNECTED
            ChargerEvent.DISCONNECTED -> TriggerEvent.CHARGER_DISCONNECTED
        }
        val effectiveState = liveState.copy(isChargerConnected = (event == ChargerEvent.CONNECTED))
        return rules.filter { it.enabled && it.triggerEvent == triggerEvent && matchesConditions(it.conditions, effectiveState) }
    }

    fun evaluateBattery(
        rules: List<Automation>,
        transition: BatteryLevelTransition,
        liveState: LiveSystemState = LiveSystemState(),
    ): List<Automation> {
        val effectiveState = liveState.copy(batteryPercent = transition.current)
        return rules.filter { rule ->
            if (!rule.enabled) return@filter false
            val triggerMatches = when (rule.triggerEvent) {
                TriggerEvent.BATTERY_BELOW -> transition.previous > rule.batteryLevel && transition.current <= rule.batteryLevel
                TriggerEvent.BATTERY_ABOVE -> transition.previous < rule.batteryLevel && transition.current >= rule.batteryLevel
                else -> false
            }
            triggerMatches && matchesConditions(rule.conditions, effectiveState)
        }
    }

    fun evaluateScreen(
        rules: List<Automation>,
        event: ScreenEvent,
        liveState: LiveSystemState = LiveSystemState(),
    ): List<Automation> {
        val trigger = when (event) {
            ScreenEvent.ON -> TriggerEvent.SCREEN_ON
            ScreenEvent.OFF -> TriggerEvent.SCREEN_OFF
        }
        val effectiveState = liveState.copy(isScreenOn = (event == ScreenEvent.ON))
        return rules.filter { it.enabled && it.triggerEvent == trigger && matchesConditions(it.conditions, effectiveState) }
    }

    fun evaluateWifi(
        rules: List<Automation>,
        transition: WifiTransition,
        liveState: LiveSystemState = LiveSystemState(),
    ): List<Automation> {
        val trigger = when (transition.event) {
            WifiStateEvent.CONNECTED -> TriggerEvent.WIFI_CONNECTED
            WifiStateEvent.DISCONNECTED -> TriggerEvent.WIFI_DISCONNECTED
        }
        val effectiveState = liveState.copy(
            connectedWifiSsid = if (transition.event == WifiStateEvent.CONNECTED) transition.ssid else null
        )
        return rules.filter { rule ->
            if (!rule.enabled || rule.triggerEvent != trigger) return@filter false
            val ssidMatches = if (rule.wifiSsid.isBlank()) true
            else rule.wifiSsid.trim().equals(transition.ssid.trim(), ignoreCase = true)
            ssidMatches && matchesConditions(rule.conditions, effectiveState)
        }
    }

    fun evaluateNotification(
        rules: List<Automation>,
        event: TransientNotificationEvent,
        liveState: LiveSystemState = LiveSystemState(),
    ): List<Automation> {
        return rules.filter { rule ->
            if (!rule.enabled || rule.triggerEvent != TriggerEvent.NOTIFICATION_RECEIVED) return@filter false
            // Package match: must match selected package if configured
            if (rule.notificationAppPackage.isNotBlank() && rule.notificationAppPackage != event.packageName) {
                return@filter false
            }
            // Keyword match: case-insensitive check in title or text
            if (rule.notificationKeyword.isNotBlank()) {
                val kw = rule.notificationKeyword.trim().lowercase()
                val inTitle = event.title.lowercase().contains(kw)
                val inText = event.text.lowercase().contains(kw)
                if (!inTitle && !inText) return@filter false
            }
            matchesConditions(rule.conditions, liveState)
        }
    }

    fun evaluate(
        rules: List<Automation>,
        event: AppEvent,
        pkg: String,
        heldOpenLock: Boolean,
        liveState: LiveSystemState = LiveSystemState(),
    ): EvaluationResult {
        val triggerEvent = when (event) {
            AppEvent.OPENED -> TriggerEvent.APP_OPENED
            AppEvent.CLOSED -> TriggerEvent.APP_CLOSED
        }

        val matching = rules.filter { r ->
            r.enabled && r.triggerEvent == triggerEvent && r.appPackage == pkg && matchesConditions(r.conditions, liveState)
        }

        val toExecute = when (event) {
            AppEvent.OPENED -> if (heldOpenLock) emptyList() else matching
            AppEvent.CLOSED -> matching
        }

        return EvaluationResult(toExecute = toExecute, matched = matching)
    }
}
