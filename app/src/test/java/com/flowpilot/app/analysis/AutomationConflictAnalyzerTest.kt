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
        conditions: List<RuleCondition> = emptyList(),
    ) = Automation(
        id = id,
        name = "Rule $id",
        enabled = enabled,
        triggerEvent = trigger,
        appPackage = appPackage,
        wifiSsid = wifiSsid,
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
    fun exactWifiTriggerRequiresSameSsidTarget() {
        val candidate = rule("candidate", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_ON), wifiSsid = "Home")
        val exact = rule("exact", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_OFF), wifiSsid = "Home")
        val other = rule("other", TriggerEvent.WIFI_CONNECTED, listOf(ActionType.WIFI_OFF), wifiSsid = "Work")

        assertEquals(listOf("exact"), AutomationConflictAnalyzer.analyze(candidate, listOf(exact, other)).map { it.conflictingRuleId })
    }
}
