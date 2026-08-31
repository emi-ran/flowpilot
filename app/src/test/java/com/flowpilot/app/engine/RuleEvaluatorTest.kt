package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEvaluatorTest {

    @Test fun screenEvents_matchOnlyEnabledRulesForThatState() {
        val on = rule(TriggerEvent.SCREEN_ON).copy(id = "on")
        val off = rule(TriggerEvent.SCREEN_OFF).copy(id = "off")
        val disabled = rule(TriggerEvent.SCREEN_ON).copy(id = "disabled", enabled = false)

        assertThat(RuleEvaluator.evaluateScreen(listOf(on, off, disabled), ScreenEvent.ON)).containsExactly(on)
        assertThat(RuleEvaluator.evaluateScreen(listOf(on, off, disabled), ScreenEvent.OFF)).containsExactly(off)
    }
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

    @Test fun empty_conditions_match_any_state() {
        val r = rule().copy(conditions = emptyList())
        val state = LiveSystemState(batteryPercent = 15, isChargerConnected = false, isScreenOn = false)
        assertThat(RuleEvaluator.matchesConditions(r.conditions, state)).isTrue()
    }

    @Test fun conditions_and_semantics_require_all_to_match() {
        val conditions = listOf(
            com.flowpilot.app.data.model.RuleCondition(type = com.flowpilot.app.data.model.ConditionType.BATTERY_BELOW, batteryLevel = 30),
            com.flowpilot.app.data.model.RuleCondition(type = com.flowpilot.app.data.model.ConditionType.CHARGER_CONNECTED),
            com.flowpilot.app.data.model.RuleCondition(type = com.flowpilot.app.data.model.ConditionType.WIFI_CONNECTED, wifiSsid = "Home-WiFi"),
        )
        // All match
        val stateMatch = LiveSystemState(
            batteryPercent = 25,
            isChargerConnected = true,
            connectedWifiSsid = "Home-WiFi",
        )
        assertThat(RuleEvaluator.matchesConditions(conditions, stateMatch)).isTrue()

        // One fails (Wi-Fi mismatch)
        val stateFailWifi = LiveSystemState(
            batteryPercent = 25,
            isChargerConnected = true,
            connectedWifiSsid = "Office-WiFi",
        )
        assertThat(RuleEvaluator.matchesConditions(conditions, stateFailWifi)).isFalse()

        // One fails (Battery above)
        val stateFailBattery = LiveSystemState(
            batteryPercent = 35,
            isChargerConnected = true,
            connectedWifiSsid = "Home-WiFi",
        )
        assertThat(RuleEvaluator.matchesConditions(conditions, stateFailBattery)).isFalse()
    }

    @Test fun wifi_triggers_match_configured_ssid_and_respect_conditions() {
        val rHome = rule(TriggerEvent.WIFI_CONNECTED).copy(id = "home", wifiSsid = "Home-WiFi")
        val rAny = rule(TriggerEvent.WIFI_CONNECTED).copy(id = "any", wifiSsid = "")
        val rDisconnect = rule(TriggerEvent.WIFI_DISCONNECTED).copy(id = "disc", wifiSsid = "Home-WiFi")

        val connectHome = WifiTransition(WifiStateEvent.CONNECTED, "Home-WiFi")
        val connectOther = WifiTransition(WifiStateEvent.CONNECTED, "CoffeeShop")
        val disconnectHome = WifiTransition(WifiStateEvent.DISCONNECTED, "Home-WiFi")

        assertThat(RuleEvaluator.evaluateWifi(listOf(rHome, rAny, rDisconnect), connectHome))
            .containsExactly(rHome, rAny)
        assertThat(RuleEvaluator.evaluateWifi(listOf(rHome, rAny, rDisconnect), connectOther))
            .containsExactly(rAny)
        assertThat(RuleEvaluator.evaluateWifi(listOf(rHome, rAny, rDisconnect), disconnectHome))
            .containsExactly(rDisconnect)
    }

    @Test fun notification_triggers_match_package_and_keyword() {
        val rAppOnly = rule(TriggerEvent.NOTIFICATION_RECEIVED).copy(
            id = "app_only",
            notificationAppPackage = "com.whatsapp",
            notificationKeyword = "",
        )
        val rAppKeyword = rule(TriggerEvent.NOTIFICATION_RECEIVED).copy(
            id = "app_kw",
            notificationAppPackage = "com.whatsapp",
            notificationKeyword = "urgent",
        )
        val rAnyAppKeyword = rule(TriggerEvent.NOTIFICATION_RECEIVED).copy(
            id = "any_kw",
            notificationAppPackage = "",
            notificationKeyword = "otp",
        )

        val rules = listOf(rAppOnly, rAppKeyword, rAnyAppKeyword)

        // Event from WhatsApp containing "Urgent" (case-insensitive)
        val event1 = TransientNotificationEvent(
            packageName = "com.whatsapp",
            postTime = 1000L,
            key = "k1",
            title = "Mom",
            text = "This is URGENT please call",
        )
        assertThat(RuleEvaluator.evaluateNotification(rules, event1))
            .containsExactly(rAppOnly, rAppKeyword)

        // Event from WhatsApp without keyword
        val event2 = TransientNotificationEvent(
            packageName = "com.whatsapp",
            postTime = 2000L,
            key = "k2",
            title = "Mom",
            text = "Hello there",
        )
        assertThat(RuleEvaluator.evaluateNotification(rules, event2))
            .containsExactly(rAppOnly)

        // Event from Telegram containing "otp" in title
        val event3 = TransientNotificationEvent(
            packageName = "org.telegram.messenger",
            postTime = 3000L,
            key = "k3",
            title = "Your OTP code",
            text = "123456",
        )
        assertThat(RuleEvaluator.evaluateNotification(rules, event3))
            .containsExactly(rAnyAppKeyword)
    }

    @Test fun notification_deduplicator_suppresses_replayed_or_older_post_times() {
        val dedupe = NotificationDeduplicator(ttlMs = 10_000L, maxEntries = 2)
        val key = "com.pkg_1_100"

        // First event accepted
        assertThat(dedupe.shouldProcess(key, 100L, currentTime = 100L)).isTrue()

        // Same timestamp replayed -> suppressed
        assertThat(dedupe.shouldProcess(key, 100L, currentTime = 105L)).isFalse()

        // Older timestamp on same key -> suppressed
        assertThat(dedupe.shouldProcess(key, 90L, currentTime = 110L)).isFalse()

        // Newer timestamp on same key (e.g. updated notification) -> allowed
        assertThat(dedupe.shouldProcess(key, 200L, currentTime = 200L)).isTrue()
    }

    @Test fun wifi_state_tracker_ssid_helpers() {
        // Normalizes quoted strings from WifiManager
        assertThat(WifiStateTracker.normalizeSsid("\"Home-WiFi\"")).isEqualTo("Home-WiFi")
        assertThat(WifiStateTracker.normalizeSsid("Home-WiFi")).isEqualTo("Home-WiFi")
        assertThat(WifiStateTracker.normalizeSsid(null)).isEqualTo("")

        // Validates known bad values
        assertThat(WifiStateTracker.isValidSsid("Home-WiFi")).isTrue()
        assertThat(WifiStateTracker.isValidSsid("<unknown ssid>")).isFalse()
        assertThat(WifiStateTracker.isValidSsid("0x")).isFalse()
        assertThat(WifiStateTracker.isValidSsid("")).isFalse()
        assertThat(WifiStateTracker.isValidSsid("   ")).isFalse()
    }
}
