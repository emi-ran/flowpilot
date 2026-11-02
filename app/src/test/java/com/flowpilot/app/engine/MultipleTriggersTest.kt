package com.flowpilot.app.engine

import com.flowpilot.app.data.model.*
import com.flowpilot.app.actions.WebhookTemplateContext
import com.flowpilot.app.actions.WebhookTemplateRenderer
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.LocalDateTime

class MultipleTriggersTest {
    private val json = Json { encodeDefaults = true }
    private fun rule(vararg triggers: TriggerDefinition) = Automation(
        id = "rule", name = "Any source", appPackage = "legacy.ignored", createdAt = 1,
        actions = listOf(ActionType.SOUND_PROFILE_NORMAL, ActionType.VIBRATE), actionDelays = listOf(0, 2),
        triggerDefinitions = TriggerDefinitions(items = triggers.toList()),
    )
    @Test fun legacy_normalizesToOneItem_withoutChangingJsonShape() {
        val legacy = Automation(id = "old", name = "Old", createdAt = 1,
            triggerEvent = TriggerEvent.WIFI_CONNECTED, wifiSsid = "Home")
        assertThat(legacy.normalizedTriggers()).containsExactly(TriggerDefinition.WifiConnected("Home"))
        assertThat(legacy.normalizeTriggerStorage()).isEqualTo(legacy)
        val encoded = json.encodeToString(Automation.serializer(), legacy)
        assertThat(encoded).doesNotContain("triggerDefinitions")
        assertThat(json.decodeFromString(Automation.serializer(), encoded)).isEqualTo(legacy)
        assertThat(listOf(legacy).triggerViews().single()).isEqualTo(legacy)
    }
    @Test fun multiTriggerJson_roundTripsTypedPayloads_withoutRuntimeSourceMetadata() {
        val original = rule(TriggerDefinition.AppOpened("com.example", "Example"),
            TriggerDefinition.WifiConnected("Home")).copy(matchedTriggerIndex = 1)
        val encoded = json.encodeToString(Automation.serializer(), original)
        assertThat(encoded).contains("\"type\":\"APP_OPENED\"")
        assertThat(encoded).contains("\"type\":\"WIFI_CONNECTED\"")
        assertThat(encoded).doesNotContain("matchedTriggerIndex")
        val decoded = json.decodeFromString(Automation.serializer(), encoded)
        assertThat(decoded.triggerDefinitions).isEqualTo(original.triggerDefinitions)
        assertThat(decoded.matchedTriggerIndex).isEqualTo(0)
        assertThat(decoded.normalizedTriggers()).containsExactlyElementsIn(original.triggerDefinitions!!.items).inOrder()
    }
    @Test fun everyLegacyType_roundTripsItsIsolatedPayload() {
        TriggerEvent.entries.forEach { event ->
            val legacy = Automation(id = "old", name = "Old", createdAt = 1, triggerEvent = event,
                appPackage = "com.example", appName = "Example", wifiSsid = "Home",
                scheduledMinute = 720, scheduledDays = setOf(1, 5), batteryLevel = 12,
                bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF", bluetoothDeviceName = "Headphones",
                nfcTagId = "AABB", notificationAppPackage = "com.notify", notificationKeyword = "word",
                shakeThreshold = 9f, lightLux = 100, flipScreenOffDetection = true,
                smsSenderFilter = "+12025550123", smsMatchMode = SmsMatchMode.STARTS_WITH, smsKeyword = "Hi",
                geofenceLatitude = 40.0, geofenceLongitude = 29.0, geofenceRadiusMeters = 200)
            val definition = legacy.normalizedTriggers().single()
            val encoded = json.encodeToString(TriggerDefinition.serializer(), definition)
            assertThat(json.decodeFromString(TriggerDefinition.serializer(), encoded)).isEqualTo(definition)
            assertThat(legacy.withTrigger(definition)).isEqualTo(legacy)
        }
    }
    @Test fun heterogeneousUnion_keepsTargetsIsolated_andLabelsActualSource() {
        val r = rule(TriggerDefinition.AppOpened("com.example", "Example"), TriggerDefinition.WifiConnected("Home"))
        val app = RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "com.example", false).toExecute.single()
        val wifi = RuleEvaluator.evaluateWifi(listOf(r), WifiTransition(WifiStateEvent.CONNECTED, "Home")).single()
        assertThat(app.triggerEvent).isEqualTo(TriggerEvent.APP_OPENED)
        assertThat(app.matchedTriggerIndex).isEqualTo(0)
        assertThat(wifi.triggerEvent).isEqualTo(TriggerEvent.WIFI_CONNECTED)
        assertThat(wifi.matchedTriggerIndex).isEqualTo(1)
        assertThat(wifi.effectiveActions).containsExactlyElementsIn(r.effectiveActions).inOrder()
        assertThat(wifi.effectiveActionDelays).containsExactly(0, 2).inOrder()
        assertThat(RuleEvaluator.evaluateWifi(listOf(r), WifiTransition(WifiStateEvent.CONNECTED, "Office"))).isEmpty()
        assertThat(RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "legacy.ignored", false).toExecute).isEmpty()
        assertThat(WebhookTemplateRenderer.render("${'$'}{trigger}:${'$'}{trigger.index}",
            WebhookTemplateContext(trigger = wifi.triggerEvent.name, triggerSourceIndex = wifi.matchedTriggerIndex + 1)))
            .isEqualTo("WIFI_CONNECTED:2")
    }
    @Test fun overlappingDefinitions_matchOnlyOnce_firstMatchingSourceWins() {
        val r = rule(TriggerDefinition.WifiConnected(), TriggerDefinition.WifiConnected("Home"))
        val matches = RuleEvaluator.evaluateWifi(listOf(r), WifiTransition(WifiStateEvent.CONNECTED, "Home"))
        assertThat(matches).hasSize(1)
        assertThat(matches.single().matchedTriggerIndex).isEqualTo(0)
    }
    @Test fun batteryScheduleShakeAndGeofence_useIndependentDefinitions() {
        val r = rule(TriggerDefinition.BatteryBelow(20), TriggerDefinition.BatteryBelow(60),
            TriggerDefinition.Schedule(12 * 60, setOf(1)), TriggerDefinition.Shake(8f),
            TriggerDefinition.GeofenceEnter(geofenceLatitude = 40.0, geofenceLongitude = 29.0))
        assertThat(RuleEvaluator.evaluateBattery(listOf(r), BatteryLevelTransition(61, 59)).single().matchedTriggerIndex).isEqualTo(1)
        assertThat(RuleEvaluator.evaluateBattery(listOf(r), BatteryLevelTransition(21, 19)).single().matchedTriggerIndex).isEqualTo(0)
        assertThat(ScheduleEvaluator.matchingRules(listOf(r), LocalDateTime.of(2026, 11, 2, 12, 0)).single().matchedTriggerIndex).isEqualTo(2)
        assertThat(RuleEvaluator.evaluateShake(listOf(r), shakeMagnitude = 9f).single().matchedTriggerIndex).isEqualTo(3)
        assertThat(RuleEvaluator.evaluateShake(listOf(r), shakeMagnitude = 7f)).isEmpty()
        assertThat(RuleEvaluator.evaluateGeofence(listOf(r), GeofenceTransition("rule", GeofenceEvent.ENTER, triggerIndex = 4)).single().matchedTriggerIndex).isEqualTo(4)
        assertThat(RuleEvaluator.evaluateGeofence(listOf(r), GeofenceTransition("rule", GeofenceEvent.ENTER, triggerIndex = 0))).isEmpty()
        assertThat(RuleEvaluator.evaluateGeofence(listOf(r.copy(executionRevision = 2)),
            GeofenceTransition("rule", GeofenceEvent.ENTER, triggerIndex = 4, executionRevision = 1))).isEmpty()
    }
    @Test fun sharedCooldown_blocksEverySource_andDisabledRulesNeverMatch() {
        val r = rule(TriggerDefinition.AppOpened("com.example"), TriggerDefinition.WifiConnected("Home"))
            .copy(cooldownMinutes = 5, lastTriggeredAt = 1000)
        assertThat(RuleEvaluator.evaluate(listOf(r), AppEvent.OPENED, "com.example", false, nowMs = 2000).toExecute).isEmpty()
        assertThat(RuleEvaluator.evaluateWifi(listOf(r), WifiTransition(WifiStateEvent.CONNECTED, "Home"), nowMs = 2000)).isEmpty()
        assertThat(RuleEvaluator.evaluateWifi(listOf(r), WifiTransition(WifiStateEvent.CONNECTED, "Home"), nowMs = 301000)).hasSize(1)
        assertThat(RuleEvaluator.evaluateWifi(listOf(r.copy(enabled = false)), WifiTransition(WifiStateEvent.CONNECTED, "Home"), nowMs = 301000)).isEmpty()
    }
    @Test fun callbackDedupe_rejectsReplay_butNotAnotherSourceOrRevision() {
        val dedupe = TriggerExecutionDeduplicator()
        assertThat(dedupe.accept("r", 1, TriggerEvent.APP_OPENED, 0, "event1", 1000)).isTrue()
        assertThat(dedupe.accept("r", 1, TriggerEvent.APP_OPENED, 0, "event1", 2000)).isFalse()
        assertThat(dedupe.accept("r", 1, TriggerEvent.APP_OPENED, 0, "event2", 2000)).isTrue()
        assertThat(dedupe.accept("r", 1, TriggerEvent.WIFI_CONNECTED, 1, "event1", 2000)).isTrue()
        assertThat(dedupe.accept("r", 2, TriggerEvent.APP_OPENED, 0, "event1", 2000)).isTrue()
        assertThat(dedupe.accept("r", 1, TriggerEvent.APP_OPENED, 0, "event1", 121000)).isTrue()
        assertThat(dedupe.accept("r", 1, TriggerEvent.SCREEN_ON, 0, nowMs = 200000)).isTrue()
        assertThat(dedupe.accept("r", 1, TriggerEvent.SCREEN_ON, 0, nowMs = 200500)).isFalse()
        assertThat(dedupe.accept("r", 1, TriggerEvent.SCREEN_ON, 0, nowMs = 201000)).isTrue()
    }
    @Test fun listenerDemand_unionsAllEnabledSources_andConditionState() {
        val r = rule(TriggerDefinition.AppOpened("com.one"), TriggerDefinition.Shake(16f),
            TriggerDefinition.Shake(8f), TriggerDefinition.WifiConnected("Home"), TriggerDefinition.FlipDown(true))
        val other = rule(TriggerDefinition.AppClosed("com.two"), TriggerDefinition.NfcTag("AABB"),
            TriggerDefinition.Schedule(), TriggerDefinition.BluetoothConnected("AA:BB:CC:DD:EE:FF"))
            .copy(id = "other", conditions = listOf(RuleCondition(ConditionType.BATTERY_BELOW)))
        val disabled = rule(TriggerDefinition.Shake(6f), TriggerDefinition.Notification()).copy(enabled = false)
        val demand = TriggerListenerDemand.from(listOf(r, other, disabled))
        assertThat(demand.appPackages).containsExactly("com.one", "com.two")
        assertThat(demand.shakeThreshold).isEqualTo(8f)
        assertThat(demand.flipScreenOff).isTrue()
        assertThat(demand.needsWifi).isTrue()
        assertThat(demand.needsBattery).isTrue()
        assertThat(demand.needs(TriggerEvent.NFC_TAG_SCANNED, TriggerEvent.TIME_SCHEDULE)).isTrue()
        assertThat(demand.needs(TriggerEvent.BLUETOOTH_CONNECTED)).isTrue()
        assertThat(demand.needs(TriggerEvent.NOTIFICATION_RECEIVED)).isFalse()
        val removed = TriggerListenerDemand.from(listOf(r.copy(enabled = false), other.copy(enabled = false)))
        assertThat(removed.events).isEmpty()
        assertThat(removed.appPackages).isEmpty()
        assertThat(removed.shakeThreshold).isNull()
        assertThat(removed.needsWifi).isFalse()
    }
    @Test fun multipleGeofences_haveDistinctRequestIds_andDisableRemovesAll() {
        val r = rule(TriggerDefinition.GeofenceEnter(geofenceLatitude = 40.0, geofenceLongitude = 29.0),
            TriggerDefinition.GeofenceExit(geofenceLatitude = 41.0, geofenceLongitude = 28.0))
        val added = calculateGeofenceDiff(emptyMap(), listOf(r)).toAdd
        assertThat(added).hasSize(2)
        assertThat(added.map { it.id }.distinct()).hasSize(2)
        val current = added.associateBy { it.id }
        assertThat(calculateGeofenceDiff(current, listOf(r.copy(enabled = false))).toRemoveIds).containsExactlyElementsIn(current.keys)
        val edited = calculateGeofenceDiff(current, listOf(r.copy(executionRevision = 1)))
        assertThat(edited.toRemoveIds).containsExactlyElementsIn(current.keys)
        assertThat(edited.toAdd).hasSize(2)
    }
}
