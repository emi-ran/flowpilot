package com.flowpilot.app.analysis

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationConflictAnalyzerTest {
    private fun rule(
        id: String,
        trigger: TriggerEvent = TriggerEvent.SCREEN_ON,
        actions: List<ActionType>,
        enabled: Boolean = true,
        appPackage: String = "",
        wifiSsid: String = "",
        bluetoothAddress: String = "",
        nfcTagId: String = "",
        scheduledMinute: Int = 0,
        scheduledDays: Set<Int> = emptySet(),
        notificationAppPackage: String = "",
        notificationKeyword: String = "",
        conditions: List<RuleCondition> = emptyList(),
    ) = Automation(
        id = id,
        name = "Rule $id",
        enabled = enabled,
        triggerEvent = trigger,
        appPackage = appPackage,
        wifiSsid = wifiSsid,
        bluetoothDeviceAddress = bluetoothAddress,
        nfcTagId = nfcTagId,
        scheduledMinute = scheduledMinute,
        scheduledDays = scheduledDays,
        notificationAppPackage = notificationAppPackage,
        notificationKeyword = notificationKeyword,
        action = actions.first(),
        actions = actions,
        conditions = conditions,
        createdAt = 1L,
    )

    @Test
    fun exactTriggerAndOpposingActionsReturnCertainExplainableFinding() {
        val pairs = listOf(
            ActionType.WIFI_ON to ActionType.WIFI_OFF,
            ActionType.BLUETOOTH_ON to ActionType.BLUETOOTH_OFF,
            ActionType.MOBILE_DATA_ON to ActionType.MOBILE_DATA_OFF,
            ActionType.AIRPLANE_MODE_ON to ActionType.AIRPLANE_MODE_OFF,
            ActionType.NFC_ON to ActionType.NFC_OFF,
            ActionType.BATTERY_SAVER_ON to ActionType.BATTERY_SAVER_OFF,
            ActionType.DARK_THEME_ON to ActionType.DARK_THEME_OFF,
            ActionType.AUTO_ROTATE_ON to ActionType.AUTO_ROTATE_OFF,
            ActionType.DND_ON to ActionType.DND_OFF,
            ActionType.TORCH_ON to ActionType.TORCH_OFF,
            ActionType.LOCATION_ON to ActionType.LOCATION_OFF,
        )

        pairs.forEachIndexed { index, (on, off) ->
            val candidate = rule("candidate-$index", actions = listOf(on))
            val finding = AutomationConflictAnalyzer.analyze(candidate, listOf(rule("other-$index", actions = listOf(off)))).single()
            assertEquals("opposing-state-actions", finding.ruleId)
            assertEquals("candidate-$index", finding.candidateRuleId)
            assertEquals("Rule candidate-$index", finding.candidateRuleName)
            assertEquals(ConflictConfidence.CERTAIN, finding.confidence)
            assertEquals("other-$index", finding.conflictingRuleId)
            assertEquals("Rule other-$index", finding.conflictingRuleName)
            assertEquals(on, finding.candidateAction)
            assertEquals(off, finding.conflictingAction)
            assertTrue(finding.overlapReason.isNotBlank())
        }
    }

    @Test
    fun multipleActionsReturnOnlyCorrectOpposingPairs() {
        val candidate = rule(
            "candidate",
            actions = listOf(ActionType.WIFI_ON, ActionType.BLUETOOTH_ON, ActionType.DND_ON),
        )
        val other = rule(
            "other",
            actions = listOf(ActionType.WIFI_ON, ActionType.BLUETOOTH_OFF, ActionType.DND_ON),
        )

        val findings = AutomationConflictAnalyzer.analyze(candidate, listOf(other))

        assertEquals(1, findings.size)
        assertEquals(ActionType.BLUETOOTH_ON, findings.single().candidateAction)
        assertEquals(ActionType.BLUETOOTH_OFF, findings.single().conflictingAction)
    }

    @Test
    fun differentSoundProfilesConflict() {
        val finding = AutomationConflictAnalyzer.analyze(
            rule("candidate", actions = listOf(ActionType.SOUND_PROFILE_NORMAL)),
            listOf(rule("other", actions = listOf(ActionType.SOUND_PROFILE_SILENT))),
        ).single()
        assertEquals(ConflictConfidence.CERTAIN, finding.confidence)
    }

    @Test
    fun uncertainConditionOverlapIsPossibleConflict() {
        val candidate = rule(
            "candidate",
            actions = listOf(ActionType.WIFI_ON),
            conditions = listOf(RuleCondition(ConditionType.BATTERY_ABOVE, batteryLevel = 40)),
        )
        val other = rule(
            "other",
            actions = listOf(ActionType.WIFI_OFF),
            conditions = listOf(RuleCondition(ConditionType.TIME_BETWEEN, startMinute = 600, endMinute = 900)),
        )

        assertEquals(
            ConflictConfidence.POSSIBLE,
            AutomationConflictAnalyzer.analyze(candidate, listOf(other)).single().confidence,
        )
    }

    @Test
    fun sameActionDifferentTargetDisabledSelfAndDisjointConditionsDoNotWarn() {
        val candidate = rule("candidate", TriggerEvent.APP_OPENED, listOf(ActionType.WIFI_ON), appPackage = "a")
        val rules = listOf(
            rule("same-action", TriggerEvent.APP_OPENED, listOf(ActionType.WIFI_ON), appPackage = "a"),
            rule("other-target", TriggerEvent.APP_OPENED, listOf(ActionType.WIFI_OFF), appPackage = "b"),
            rule("disabled", TriggerEvent.APP_OPENED, listOf(ActionType.WIFI_OFF), enabled = false, appPackage = "a"),
            candidate.copy(actions = listOf(ActionType.WIFI_OFF), action = ActionType.WIFI_OFF),
            rule(
                "screen-off-only",
                TriggerEvent.APP_OPENED,
                listOf(ActionType.WIFI_OFF),
                appPackage = "a",
                conditions = listOf(RuleCondition(ConditionType.SCREEN_OFF)),
            ),
        )
        val screenOnCandidate = candidate.copy(conditions = listOf(RuleCondition(ConditionType.SCREEN_ON)))

        assertTrue(AutomationConflictAnalyzer.analyze(screenOnCandidate, rules).isEmpty())
    }

    @Test
    fun wifiTriggerUsesRuntimeCaseWhitespaceAndWildcardMatching() {
        val candidate = rule("candidate", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_ON), wifiSsid = " Home ")
        val normalized = rule("normalized", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_OFF), wifiSsid = "home")
        val wildcard = rule("wildcard", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_OFF), wifiSsid = "  ")
        val other = rule("other", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_OFF), wifiSsid = "Work")

        assertEquals(
            listOf("normalized", "wildcard"),
            AutomationConflictAnalyzer.analyze(candidate, listOf(normalized, wildcard, other)).map { it.conflictingRuleId },
        )
    }

    @Test
    fun timeScheduleOverlapsAtSameMinuteWhenDaysIntersectOrEitherIsDaily() {
        val candidate = rule(
            "candidate",
            TriggerEvent.TIME_SCHEDULE,
            listOf(ActionType.WIFI_ON),
            scheduledMinute = 600,
            scheduledDays = setOf(1, 3),
        )
        val rules = listOf(
            rule("intersecting", TriggerEvent.TIME_SCHEDULE, listOf(ActionType.WIFI_OFF), scheduledMinute = 600, scheduledDays = setOf(3, 5)),
            rule("daily", TriggerEvent.TIME_SCHEDULE, listOf(ActionType.WIFI_OFF), scheduledMinute = 600),
            rule("disjoint", TriggerEvent.TIME_SCHEDULE, listOf(ActionType.WIFI_OFF), scheduledMinute = 600, scheduledDays = setOf(2, 4)),
            rule("other-minute", TriggerEvent.TIME_SCHEDULE, listOf(ActionType.WIFI_OFF), scheduledMinute = 601, scheduledDays = setOf(1, 3)),
        )

        assertEquals(
            listOf("intersecting", "daily"),
            AutomationConflictAnalyzer.analyze(candidate, rules).map { it.conflictingRuleId },
        )
        assertEquals(
            listOf("intersecting", "daily", "disjoint"),
            AutomationConflictAnalyzer.analyze(candidate.copy(scheduledDays = emptySet()), rules).map { it.conflictingRuleId },
        )
    }

    @Test
    fun notificationOverlapUsesRuntimePackageWildcardAndConservativeKeywordSemantics() {
        val candidate = rule(
            "candidate",
            TriggerEvent.NOTIFICATION_RECEIVED,
            listOf(ActionType.WIFI_ON),
            notificationAppPackage = "com.chat",
            notificationKeyword = "secret-one",
        )
        val rules = listOf(
            rule("same", TriggerEvent.NOTIFICATION_RECEIVED, listOf(ActionType.WIFI_OFF), notificationAppPackage = "com.chat", notificationKeyword = " SECRET-ONE "),
            rule("keyword-wildcard", TriggerEvent.NOTIFICATION_RECEIVED, listOf(ActionType.WIFI_OFF), notificationAppPackage = "com.chat"),
            rule("package-wildcard", TriggerEvent.NOTIFICATION_RECEIVED, listOf(ActionType.WIFI_OFF), notificationKeyword = "secret-one"),
            rule("coexistent-keywords", TriggerEvent.NOTIFICATION_RECEIVED, listOf(ActionType.WIFI_OFF), notificationAppPackage = "com.chat", notificationKeyword = "secret-two"),
            rule("other-package", TriggerEvent.NOTIFICATION_RECEIVED, listOf(ActionType.WIFI_OFF), notificationAppPackage = "com.mail", notificationKeyword = "secret-one"),
        )

        val findings = AutomationConflictAnalyzer.analyze(candidate, rules)
        assertEquals(listOf("same", "keyword-wildcard", "package-wildcard", "coexistent-keywords"), findings.map { it.conflictingRuleId })
        assertEquals(ConflictConfidence.CERTAIN, findings.first { it.conflictingRuleId == "same" }.confidence)
        assertEquals(ConflictConfidence.CERTAIN, findings.first { it.conflictingRuleId == "keyword-wildcard" }.confidence)
        assertEquals(ConflictConfidence.POSSIBLE, findings.first { it.conflictingRuleId == "coexistent-keywords" }.confidence)
        findings.forEach {
            assertTrue(!it.overlapReason.contains("secret-one", ignoreCase = true))
            assertTrue(!it.overlapReason.contains("secret-two", ignoreCase = true))
        }
    }

    @Test
    fun bluetoothAndNfcTriggersUseRuntimeNormalization() {
        val bluetooth = rule(
            "bluetooth",
            TriggerEvent.BLUETOOTH_CONNECTED,
            listOf(ActionType.BLUETOOTH_ON),
            bluetoothAddress = " aa:bb ",
        )
        val bluetoothOther = rule(
            "bluetooth-other",
            TriggerEvent.BLUETOOTH_CONNECTED,
            listOf(ActionType.BLUETOOTH_OFF),
            bluetoothAddress = "AA:BB",
        )
        val nfc = rule("nfc", TriggerEvent.NFC_TAG_SCANNED, listOf(ActionType.NFC_ON), nfcTagId = "04:a1-b2")
        val nfcOther = rule("nfc-other", TriggerEvent.NFC_TAG_SCANNED, listOf(ActionType.NFC_OFF), nfcTagId = "04A1B2")

        assertEquals(1, AutomationConflictAnalyzer.analyze(bluetooth, listOf(bluetoothOther)).size)
        assertEquals(1, AutomationConflictAnalyzer.analyze(nfc, listOf(nfcOther)).size)
    }
}
