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

    @Test fun bluetooth_triggers_match_exact_selected_device_address() {
        val connected = rule(TriggerEvent.BLUETOOTH_CONNECTED).copy(
            id = "connected",
            bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF",
            bluetoothDeviceName = "Headphones",
        )
        val disconnected = rule(TriggerEvent.BLUETOOTH_DISCONNECTED).copy(
            id = "disconnected",
            bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF",
        )
        val other = rule(TriggerEvent.BLUETOOTH_CONNECTED).copy(
            id = "other",
            bluetoothDeviceAddress = "11:22:33:44:55:66",
        )

        assertThat(RuleEvaluator.evaluateBluetooth(listOf(connected, disconnected, other), BluetoothDeviceTransition(BluetoothDeviceEvent.CONNECTED, "aa:bb:cc:dd:ee:ff")))
            .containsExactly(connected)
        assertThat(RuleEvaluator.evaluateBluetooth(listOf(connected, disconnected, other), BluetoothDeviceTransition(BluetoothDeviceEvent.DISCONNECTED, "AA:BB:CC:DD:EE:FF")))
            .containsExactly(disconnected)
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

    @Test fun nfc_tag_utils_normalization_and_validation() {
        assertThat(NfcTagUtils.formatTagId(byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0x1F))).isEqualTo("04A1B21F")
        assertThat(NfcTagUtils.formatTagId(null)).isEqualTo("")
        assertThat(NfcTagUtils.formatTagId(byteArrayOf())).isEqualTo("")

        assertThat(NfcTagUtils.normalizeTagId("04:a1:b2:1f")).isEqualTo("04A1B21F")
        assertThat(NfcTagUtils.normalizeTagId("04-A1-B2-1F")).isEqualTo("04A1B21F")
        assertThat(NfcTagUtils.normalizeTagId(" 04 a1 b2 1f ")).isEqualTo("04A1B21F")
        assertThat(NfcTagUtils.normalizeTagId(null)).isEqualTo("")

        assertThat(NfcTagUtils.isValidTagId("04A1B21F")).isTrue()
        assertThat(NfcTagUtils.isValidTagId("04:A1:B2:1F")).isTrue()
        assertThat(NfcTagUtils.isValidTagId("04A1B21")).isFalse() // odd length
        assertThat(NfcTagUtils.isValidTagId("04ZZ11")).isFalse() // invalid hex
        assertThat(NfcTagUtils.isValidTagId("")).isFalse()
    }

    @Test fun nfc_tag_triggers_match_normalized_tag_id() {
        val rTag1 = rule(TriggerEvent.NFC_TAG_SCANNED).copy(id = "tag1", nfcTagId = "04:A1:B2:1F")
        val rTag2 = rule(TriggerEvent.NFC_TAG_SCANNED).copy(id = "tag2", nfcTagId = "12345678")
        val rDisabled = rule(TriggerEvent.NFC_TAG_SCANNED).copy(id = "dis", nfcTagId = "04A1B21F", enabled = false)

        val rules = listOf(rTag1, rTag2, rDisabled)

        val event = NfcTagScannedEvent(tagId = "04A1B21F")
        val matches = RuleEvaluator.evaluateNfcTag(rules, event)

        assertThat(matches).containsExactly(rTag1)
    }

    @Test fun effective_action_delays_and_summary_formatting() {
        val ruleWithDelays = Automation(
            id = "delays",
            name = "Test Delays",
            triggerEvent = TriggerEvent.APP_OPENED,
            actions = listOf(ActionType.NFC_ON, ActionType.VIBRATE),
            actionDelays = listOf(0, 5),
            createdAt = 1L,
        )

        assertThat(ruleWithDelays.effectiveActionDelays).containsExactly(0, 5).inOrder()
        assertThat(ruleWithDelays.actionSummary).isEqualTo("Turn NFC on + Vibrate (+5s)")

        val ruleBoundedDelays = Automation(
            id = "delays2",
            name = "Bounded Delays",
            triggerEvent = TriggerEvent.APP_OPENED,
            actions = listOf(ActionType.NFC_ON, ActionType.VIBRATE, ActionType.SOUND_PROFILE_SILENT),
            actionDelays = listOf(-10, 5000),
            createdAt = 1L,
        )
        // Coerces negative to 0, values > 300 to 300, missing trailing indices to 0
        assertThat(ruleBoundedDelays.effectiveActionDelays).containsExactly(0, 300, 0).inOrder()
        assertThat(ruleBoundedDelays.actionSummary).isEqualTo("Turn NFC on + Vibrate (+300s) + Sound profile: Silent")
    }

    @Test fun nfc_tag_handoff_queue_operations() {
        NfcTagHandoff.clear()
        assertThat(NfcTagHandoff.drainEvents()).isEmpty()

        NfcTagHandoff.emitTagScanned(byteArrayOf(0x04, 0x12, 0x34))
        NfcTagHandoff.emitTagId("04:ab:cd:ef")
        NfcTagHandoff.emitTagId("")
        NfcTagHandoff.emitTagScanned(null)

        val events = NfcTagHandoff.drainEvents()
        assertThat(events).hasSize(2)
        assertThat(events[0].tagId).isEqualTo("041234")
        assertThat(events[1].tagId).isEqualTo("04ABCDEF")
        assertThat(NfcTagHandoff.latestScannedTagId.value).isEqualTo("04ABCDEF")
        assertThat(NfcTagHandoff.drainEvents()).isEmpty()

        NfcTagHandoff.clearLatestScannedTagId()
        assertThat(NfcTagHandoff.latestScannedTagId.value).isNull()
    }

    @Test fun cooldown_no_cooldown_always_matches() {
        val r = rule(TriggerEvent.APP_OPENED).copy(cooldownMinutes = 0, lastTriggeredAt = 1000L)
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "wallet.pkg", false, nowMs = 1500L)
        assertThat(result.toExecute).containsExactly(r)
    }

    @Test fun cooldown_inside_boundary_blocked() {
        val nowMs = 1_000_000L
        val r = rule(TriggerEvent.APP_OPENED).copy(cooldownMinutes = 5, lastTriggeredAt = nowMs - (4 * 60_000L))
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "wallet.pkg", false, nowMs = nowMs)
        assertThat(result.toExecute).isEmpty()
        assertThat(result.matched).isEmpty()
    }

    @Test fun cooldown_after_expiry_allowed() {
        val nowMs = 1_000_000L
        val r = rule(TriggerEvent.APP_OPENED).copy(cooldownMinutes = 5, lastTriggeredAt = nowMs - (5 * 60_000L))
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "wallet.pkg", false, nowMs = nowMs)
        assertThat(result.toExecute).containsExactly(r)
    }

    @Test fun cooldown_never_triggered_always_matches() {
        val nowMs = 1_000_000L
        val r = rule(TriggerEvent.APP_OPENED).copy(cooldownMinutes = 15, lastTriggeredAt = 0L)
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "wallet.pkg", false, nowMs = nowMs)
        assertThat(result.toExecute).containsExactly(r)
    }

    @Test fun cooldown_future_last_triggered_blocked_safely() {
        val nowMs = 100_000L
        val r = rule(TriggerEvent.APP_OPENED).copy(cooldownMinutes = 5, lastTriggeredAt = 200_000L)
        val result = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "wallet.pkg", false, nowMs = nowMs)
        assertThat(result.toExecute).isEmpty()
    }

    @Test fun cooldown_disabled_or_condition_mismatch_remains_blocked() {
        val nowMs = 1_000_000L
        val disabled = rule(TriggerEvent.APP_OPENED).copy(cooldownMinutes = 0, lastTriggeredAt = 0L, enabled = false)
        val condMismatch = rule(TriggerEvent.APP_OPENED).copy(
            cooldownMinutes = 0,
            lastTriggeredAt = 0L,
            conditions = listOf(com.flowpilot.app.data.model.RuleCondition(com.flowpilot.app.data.model.ConditionType.CHARGER_CONNECTED))
        )
        val state = LiveSystemState(isChargerConnected = false)
        val result = RuleEvaluator.evaluate(listOf(disabled, condMismatch), AppEvent.OPENED, "wallet.pkg", false, liveState = state, nowMs = nowMs)
        assertThat(result.toExecute).isEmpty()
    }

    @Test fun cooldown_applies_uniformly_across_all_evaluators() {
        val nowMs = 500_000L
        val recentSuccess = nowMs - 60_000L // 1 min ago
        val cdMinutes = 5

        // Charger
        val chargerRule = rule(TriggerEvent.CHARGER_CONNECTED).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess)
        assertThat(RuleEvaluator.evaluateCharger(listOf(chargerRule), ChargerEvent.CONNECTED, nowMs = nowMs)).isEmpty()

        // Battery
        val batteryRule = rule(TriggerEvent.BATTERY_BELOW).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, batteryLevel = 20)
        assertThat(RuleEvaluator.evaluateBattery(listOf(batteryRule), BatteryLevelTransition(21, 20), nowMs = nowMs)).isEmpty()

        // Screen
        val screenRule = rule(TriggerEvent.SCREEN_ON).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess)
        assertThat(RuleEvaluator.evaluateScreen(listOf(screenRule), ScreenEvent.ON, nowMs = nowMs)).isEmpty()

        // Wi-Fi
        val wifiRule = rule(TriggerEvent.WIFI_CONNECTED).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, wifiSsid = "Home")
        assertThat(RuleEvaluator.evaluateWifi(listOf(wifiRule), WifiTransition(WifiStateEvent.CONNECTED, "Home"), nowMs = nowMs)).isEmpty()

        // Bluetooth
        val btRule = rule(TriggerEvent.BLUETOOTH_CONNECTED).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF")
        assertThat(RuleEvaluator.evaluateBluetooth(listOf(btRule), BluetoothDeviceTransition(BluetoothDeviceEvent.CONNECTED, "AA:BB:CC:DD:EE:FF"), nowMs = nowMs)).isEmpty()

        // Notification
        val notifRule = rule(TriggerEvent.NOTIFICATION_RECEIVED).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, notificationAppPackage = "com.test.app")
        assertThat(
            RuleEvaluator.evaluateNotification(
                listOf(notifRule),
                TransientNotificationEvent(
                    packageName = "com.test.app",
                    postTime = nowMs,
                    key = "cooldown-test",
                    title = "Title",
                    text = "Text",
                ),
                nowMs = nowMs,
            )
        ).isEmpty()

        // NFC Tag
        val nfcRule = rule(TriggerEvent.NFC_TAG_SCANNED).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, nfcTagId = "04A1B2")
        assertThat(RuleEvaluator.evaluateNfcTag(listOf(nfcRule), NfcTagScannedEvent("04A1B2"), nowMs = nowMs)).isEmpty()

        // Schedule
        val schedRule = rule(TriggerEvent.TIME_SCHEDULE).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, scheduledMinute = 120)
        val now = java.time.LocalDateTime.of(2026, 9, 1, 2, 0)
        assertThat(ScheduleEvaluator.matchingRules(listOf(schedRule), now, nowMs = nowMs)).isEmpty()

        // Shake
        val shakeRule = rule(TriggerEvent.DEVICE_SHAKE).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess)
        assertThat(RuleEvaluator.evaluateShake(listOf(shakeRule), nowMs = nowMs)).isEmpty()

        // Light
        val lightRule = rule(TriggerEvent.LIGHT_BELOW).copy(cooldownMinutes = cdMinutes, lastTriggeredAt = recentSuccess, lightLux = 50)
        assertThat(RuleEvaluator.evaluateLight(listOf(lightRule), LightTransition(100f, 40f), nowMs = nowMs)).isEmpty()
    }

    @Test fun shake_matches_only_enabled_shake_rules() {
        val shake = rule(TriggerEvent.DEVICE_SHAKE).copy(id = "shake-1")
        val shakeDisabled = rule(TriggerEvent.DEVICE_SHAKE).copy(id = "shake-2", enabled = false)
        val other = rule(TriggerEvent.SCREEN_ON).copy(id = "other")

        val result = RuleEvaluator.evaluateShake(listOf(shake, shakeDisabled, other), nowMs = 1000L)
        assertThat(result).containsExactly(shake)
    }

    @Test fun unlocked_matches_only_unlocked_rules() {
        val unlocked = rule(TriggerEvent.DEVICE_UNLOCKED).copy(id = "u1")
        val on = rule(TriggerEvent.SCREEN_ON).copy(id = "s1")
        val off = rule(TriggerEvent.SCREEN_OFF).copy(id = "s2")

        val result = RuleEvaluator.evaluateScreen(listOf(unlocked, on, off), ScreenEvent.UNLOCKED)
        assertThat(result).containsExactly(unlocked)
    }

    @Test fun light_rules_match_only_when_threshold_crossed() {
        val below = rule(TriggerEvent.LIGHT_BELOW).copy(id = "below", lightLux = 50)
        val above = rule(TriggerEvent.LIGHT_ABOVE).copy(id = "above", lightLux = 500)
        val rules = listOf(below, above)

        // Dropping from 60 to 40 crosses below 50
        assertThat(RuleEvaluator.evaluateLight(rules, LightTransition(60f, 40f))).containsExactly(below)
        // Dropping from 40 to 30 does not cross
        assertThat(RuleEvaluator.evaluateLight(rules, LightTransition(40f, 30f))).isEmpty()
        // Rising from 400 to 600 crosses above 500
        assertThat(RuleEvaluator.evaluateLight(rules, LightTransition(400f, 600f))).containsExactly(above)
        // Rising from 600 to 700 does not cross
        assertThat(RuleEvaluator.evaluateLight(rules, LightTransition(600f, 700f))).isEmpty()
    }
}
