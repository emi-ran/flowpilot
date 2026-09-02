package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEvaluatorCallTest {

    private fun callRule(
        id: String = "1",
        event: TriggerEvent = TriggerEvent.CALL_RINGING,
        enabled: Boolean = true,
        cooldownMinutes: Int = 0,
        lastTriggeredAt: Long = 0L,
        conditions: List<RuleCondition> = emptyList(),
    ) = Automation(
        id = id,
        name = "Call Rule",
        triggerEvent = event,
        action = ActionType.SHOW_NOTIFICATION,
        enabled = enabled,
        cooldownMinutes = cooldownMinutes,
        lastTriggeredAt = lastTriggeredAt,
        conditions = conditions,
        createdAt = 1000L,
    )

    @Test
    fun evaluateCall_matches_ringing_event_broadly() {
        val rule = callRule(event = TriggerEvent.CALL_RINGING)
        val transition = CallTransition(TriggerEvent.CALL_RINGING, "+905551234567")

        val result = RuleEvaluator.evaluateCall(listOf(rule), transition)
        assertThat(result).containsExactly(rule)
    }

    @Test
    fun evaluateCall_suppresses_disabled_rule() {
        val rule = callRule(enabled = false)
        val transition = CallTransition(TriggerEvent.CALL_RINGING, "+905551234567")

        val result = RuleEvaluator.evaluateCall(listOf(rule), transition)
        assertThat(result).isEmpty()
    }

    @Test
    fun evaluateCall_respects_cooldown() {
        val now = 100_000L
        val ruleInCooldown = callRule(
            cooldownMinutes = 5,
            lastTriggeredAt = now - 60_000L, // 1 min ago < 5 min cooldown
        )
        val transition = CallTransition(TriggerEvent.CALL_RINGING, "+905551234567")

        val result = RuleEvaluator.evaluateCall(listOf(ruleInCooldown), transition, nowMs = now)
        assertThat(result).isEmpty()

        val rulePastCooldown = callRule(
            cooldownMinutes = 5,
            lastTriggeredAt = now - 360_000L, // 6 min ago > 5 min cooldown
        )
        val pastResult = RuleEvaluator.evaluateCall(listOf(rulePastCooldown), transition, nowMs = now)
        assertThat(pastResult).containsExactly(rulePastCooldown)
    }

    @Test
    fun evaluateCall_respects_conditions() {
        val condition = RuleCondition(type = ConditionType.CHARGER_CONNECTED)
        val ruleWithCondition = callRule(conditions = listOf(condition))
        val transition = CallTransition(TriggerEvent.CALL_RINGING, "+905551234567")

        val mismatched = RuleEvaluator.evaluateCall(
            listOf(ruleWithCondition),
            transition,
            liveState = LiveSystemState(isChargerConnected = false),
        )
        assertThat(mismatched).isEmpty()

        val matched = RuleEvaluator.evaluateCall(
            listOf(ruleWithCondition),
            transition,
            liveState = LiveSystemState(isChargerConnected = true),
        )
        assertThat(matched).containsExactly(ruleWithCondition)
    }

    @Test
    fun evaluateCall_handles_answered_outgoing_and_ended_events() {
        val ringingRule = callRule(id = "ring", event = TriggerEvent.CALL_RINGING)
        val answeredRule = callRule(id = "ans", event = TriggerEvent.CALL_ANSWERED)
        val outgoingRule = callRule(id = "out", event = TriggerEvent.CALL_OUTGOING)
        val endedRule = callRule(id = "end", event = TriggerEvent.CALL_ENDED)
        val rules = listOf(ringingRule, answeredRule, outgoingRule, endedRule)

        assertThat(RuleEvaluator.evaluateCall(rules, CallTransition(TriggerEvent.CALL_ANSWERED, "+905550000000")))
            .containsExactly(answeredRule)

        assertThat(RuleEvaluator.evaluateCall(rules, CallTransition(TriggerEvent.CALL_OUTGOING, "+905550000000")))
            .containsExactly(outgoingRule)

        assertThat(RuleEvaluator.evaluateCall(rules, CallTransition(TriggerEvent.CALL_ENDED, "+905550000000")))
            .containsExactly(endedRule)
    }
}
