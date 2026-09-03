package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import java.time.LocalDateTime

/** Pure time-schedule matching. Process startup primes time instead of replaying it. */
object ScheduleEvaluator {
    fun matchingRules(
        rules: List<Automation>,
        now: LocalDateTime,
        liveState: LiveSystemState = LiveSystemState(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<Automation> {
        val minute = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        return rules.filter { rule ->
            rule.enabled && rule.triggerEvent == TriggerEvent.TIME_SCHEDULE &&
                !rule.isCoolingDown(nowMs) &&
                rule.scheduledMinute == minute &&
                (rule.scheduledDays.isEmpty() || day in rule.scheduledDays) &&
                RuleEvaluator.matchesConditions(rule.conditions, liveState, nowMs)
        }
    }
}
