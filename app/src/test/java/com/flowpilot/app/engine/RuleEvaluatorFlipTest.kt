package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEvaluatorFlipTest {

    private fun buildFlipRule(
        triggerEvent: TriggerEvent,
        enabled: Boolean = true,
        flipScreenOffDetection: Boolean = false,
        cooldownMinutes: Int = 0,
        lastTriggeredAt: Long = 0L,
        conditions: List<RuleCondition> = emptyList(),
    ): Automation = Automation(
        id = "rule-flip-1",
        name = "Flip Rule",
        enabled = enabled,
        triggerEvent = triggerEvent,
        action = ActionType.DND_ON,
        flipScreenOffDetection = flipScreenOffDetection,
        cooldownMinutes = cooldownMinutes,
        lastTriggeredAt = lastTriggeredAt,
        conditions = conditions,
        createdAt = 1000L,
    )

    @Test
    fun matches_flip_down_and_flip_up_when_screen_is_on() {
        val ruleDown = buildFlipRule(TriggerEvent.DEVICE_FLIPPED_DOWN)
        val ruleUp = buildFlipRule(TriggerEvent.DEVICE_FLIPPED_UP)
        val liveState = LiveSystemState(isScreenOn = true)

        val matchesDown = RuleEvaluator.evaluateFlip(listOf(ruleDown, ruleUp), FlipEvent.FLIPPED_DOWN, liveState)
        assertThat(matchesDown).containsExactly(ruleDown)

        val matchesUp = RuleEvaluator.evaluateFlip(listOf(ruleDown, ruleUp), FlipEvent.FLIPPED_UP, liveState)
        assertThat(matchesUp).containsExactly(ruleUp)
    }

    @Test
    fun screen_off_filters_out_rules_unless_flipScreenOffDetection_is_true() {
        val ruleWithoutScreenOff = buildFlipRule(
            triggerEvent = TriggerEvent.DEVICE_FLIPPED_DOWN,
            flipScreenOffDetection = false,
        )
        val ruleWithScreenOff = buildFlipRule(
            triggerEvent = TriggerEvent.DEVICE_FLIPPED_DOWN,
            flipScreenOffDetection = true,
        )

        val screenOffState = LiveSystemState(isScreenOn = false)

        val matches = RuleEvaluator.evaluateFlip(
            rules = listOf(ruleWithoutScreenOff, ruleWithScreenOff),
            event = FlipEvent.FLIPPED_DOWN,
            liveState = screenOffState,
        )

        // Only the rule with flipScreenOffDetection enabled can trigger while screen is off
        assertThat(matches).containsExactly(ruleWithScreenOff)
    }

    @Test
    fun cooldown_suppresses_rapid_flip_triggers() {
        val now = 10_000_000L
        val coolingRule = buildFlipRule(
            triggerEvent = TriggerEvent.DEVICE_FLIPPED_DOWN,
            cooldownMinutes = 5,
            lastTriggeredAt = now - 60_000L, // Only 1 minute ago, cooldown is 5m
        )
        val liveState = LiveSystemState(isScreenOn = true)

        val matches = RuleEvaluator.evaluateFlip(listOf(coolingRule), FlipEvent.FLIPPED_DOWN, liveState, nowMs = now)
        assertThat(matches).isEmpty()

        // After cooldown expires (6 minutes later)
        val matchesAfterExpiry = RuleEvaluator.evaluateFlip(
            listOf(coolingRule),
            FlipEvent.FLIPPED_DOWN,
            liveState,
            nowMs = now + 360_000L,
        )
        assertThat(matchesAfterExpiry).containsExactly(coolingRule)
    }

    @Test
    fun respects_environmental_conditions() {
        val ruleWithCondition = buildFlipRule(
            triggerEvent = TriggerEvent.DEVICE_FLIPPED_DOWN,
            conditions = listOf(RuleCondition(type = ConditionType.CHARGER_CONNECTED)),
        )

        val unpluggedState = LiveSystemState(isScreenOn = true, isChargerConnected = false)
        val pluggedState = LiveSystemState(isScreenOn = true, isChargerConnected = true)

        assertThat(RuleEvaluator.evaluateFlip(listOf(ruleWithCondition), FlipEvent.FLIPPED_DOWN, unpluggedState)).isEmpty()
        assertThat(RuleEvaluator.evaluateFlip(listOf(ruleWithCondition), FlipEvent.FLIPPED_DOWN, pluggedState)).containsExactly(ruleWithCondition)
    }
}
