package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEvaluatorTest {
    private fun rule(event: TriggerEvent = TriggerEvent.APP_OPENED) = Automation(
        id = "1", name = "Wallet", appPackage = "wallet.pkg", appName = "Wallet",
        triggerEvent = event, action = ActionType.NFC_ON, createdAt = 1L,
    )

    @Test fun opened_matches_only_same_package_and_event() {
        val result = RuleEvaluator.evaluate(listOf(rule()), AppEvent.OPENED, "wallet.pkg", false)
        assertThat(result.toExecute).containsExactly(rule())
    }

    @Test fun opened_is_suppressed_while_residency_lock_is_held() {
        val result = RuleEvaluator.evaluate(listOf(rule()), AppEvent.OPENED, "wallet.pkg", true)
        assertThat(result.toExecute).isEmpty()
        assertThat(result.matched).hasSize(1)
    }

    @Test fun closed_rule_matches_close_event() {
        val r = rule(TriggerEvent.APP_CLOSED)
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.CLOSED, "wallet.pkg", false)
        assertThat(result.toExecute).containsExactly(r)
    }

    @Test fun disabled_rule_never_matches() {
        val r = rule().copy(enabled = false)
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "wallet.pkg", false)
        assertThat(result.toExecute).isEmpty()
        assertThat(result.matched).isEmpty()
    }

    @Test fun charger_rules_match_only_their_transition() {
        val connected = rule(TriggerEvent.CHARGER_CONNECTED).copy(appPackage = "", appName = "")
        val disconnected = rule(TriggerEvent.CHARGER_DISCONNECTED).copy(id = "2", appPackage = "", appName = "")

        assertThat(RuleEvaluator.evaluateCharger(listOf(connected, disconnected), ChargerEvent.CONNECTED))
            .containsExactly(connected)
        assertThat(RuleEvaluator.evaluateCharger(listOf(connected, disconnected), ChargerEvent.DISCONNECTED))
            .containsExactly(disconnected)
    }

    @Test fun disabled_charger_rule_never_matches() {
        val rule = rule(TriggerEvent.CHARGER_CONNECTED).copy(enabled = false, appPackage = "", appName = "")

        assertThat(RuleEvaluator.evaluateCharger(listOf(rule), ChargerEvent.CONNECTED)).isEmpty()
    }

    @Test fun battery_rules_match_only_when_threshold_is_crossed() {
        val below = rule(TriggerEvent.BATTERY_BELOW).copy(appPackage = "", appName = "", batteryLevel = 20)
        val above = rule(TriggerEvent.BATTERY_ABOVE).copy(id = "2", appPackage = "", appName = "", batteryLevel = 80)
        val rules = listOf(below, above)

        assertThat(RuleEvaluator.evaluateBattery(rules, BatteryLevelTransition(21, 20))).containsExactly(below)
        assertThat(RuleEvaluator.evaluateBattery(rules, BatteryLevelTransition(20, 19))).isEmpty()
        assertThat(RuleEvaluator.evaluateBattery(rules, BatteryLevelTransition(79, 80))).containsExactly(above)
        assertThat(RuleEvaluator.evaluateBattery(rules, BatteryLevelTransition(80, 81))).isEmpty()
    }

    @Test fun disabled_battery_rule_never_matches() {
        val rule = rule(TriggerEvent.BATTERY_BELOW).copy(enabled = false, appPackage = "", appName = "", batteryLevel = 20)

        assertThat(RuleEvaluator.evaluateBattery(listOf(rule), BatteryLevelTransition(21, 20))).isEmpty()
    }

    @Test fun multi_action_rule_preserved_and_effective_actions_evaluated() {
        val multiActionRule = Automation(
            id = "2",
            name = "Akbank · Turn NFC on + Turn Battery Saver off",
            appPackage = "com.akbank.android.apps.akbank_direkt",
            appName = "Akbank",
            triggerEvent = TriggerEvent.APP_OPENED,
            action = ActionType.NFC_ON,
            actions = listOf(ActionType.NFC_ON, ActionType.BATTERY_SAVER_OFF),
            createdAt = 2L,
        )
        assertThat(multiActionRule.effectiveActions).containsExactly(
            ActionType.NFC_ON,
            ActionType.BATTERY_SAVER_OFF
        ).inOrder()
        assertThat(multiActionRule.actionSummary).isEqualTo("Turn NFC on + Turn Battery Saver off")

        val result = RuleEvaluator.evaluate(listOf(multiActionRule), AppEvent.OPENED, "com.akbank.android.apps.akbank_direkt", false)
        assertThat(result.toExecute).containsExactly(multiActionRule)
        assertThat(result.toExecute.first().effectiveActions).containsExactly(
            ActionType.NFC_ON,
            ActionType.BATTERY_SAVER_OFF
        ).inOrder()
    }
}
