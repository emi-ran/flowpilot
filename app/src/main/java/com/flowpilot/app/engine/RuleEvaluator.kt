package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent

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
 * Pure rule-matching + dedup logic. No Android dependencies so it is unit-testable.
 *
 * Dedupe semantics:
 *  - An "APP_OPENED" rule fires once when the app opens, and is locked until that app
 *    closes (so it won't re-fire every poll while the app stays foreground).
 *  - An "APP_CLOSED" rule fires once when the app closes.
 *
 * @param lockedOpen whether this app's OPENED rules have already fired for the current
 *        foreground residency. Managed by the engine.
 */
object RuleEvaluator {

    fun evaluateCharger(rules: List<Automation>, event: ChargerEvent): List<Automation> {
        val triggerEvent = when (event) {
            ChargerEvent.CONNECTED -> TriggerEvent.CHARGER_CONNECTED
            ChargerEvent.DISCONNECTED -> TriggerEvent.CHARGER_DISCONNECTED
        }
        return rules.filter { it.enabled && it.triggerEvent == triggerEvent }
    }

    fun evaluateBattery(rules: List<Automation>, transition: BatteryLevelTransition): List<Automation> =
        rules.filter { rule ->
            if (!rule.enabled) return@filter false
            when (rule.triggerEvent) {
                TriggerEvent.BATTERY_BELOW -> transition.previous > rule.batteryLevel && transition.current <= rule.batteryLevel
                TriggerEvent.BATTERY_ABOVE -> transition.previous < rule.batteryLevel && transition.current >= rule.batteryLevel
                else -> false
            }
        }

    fun evaluateScreen(rules: List<Automation>, event: ScreenEvent): List<Automation> {
        val trigger = when (event) {
            ScreenEvent.ON -> TriggerEvent.SCREEN_ON
            ScreenEvent.OFF -> TriggerEvent.SCREEN_OFF
        }
        return rules.filter { it.enabled && it.triggerEvent == trigger }
    }

    fun evaluate(
        rules: List<Automation>,
        event: AppEvent,
        pkg: String,
        heldOpenLock: Boolean,
    ): EvaluationResult {
        val triggerEvent = when (event) {
            AppEvent.OPENED -> TriggerEvent.APP_OPENED
            AppEvent.CLOSED -> TriggerEvent.APP_CLOSED
        }

        val matching = rules.filter { r ->
            r.enabled && r.triggerEvent == triggerEvent && r.appPackage == pkg
        }

        val toExecute = when (event) {
            AppEvent.OPENED -> if (heldOpenLock) emptyList() else matching
            AppEvent.CLOSED -> matching
        }

        return EvaluationResult(toExecute = toExecute, matched = matching)
    }
}
