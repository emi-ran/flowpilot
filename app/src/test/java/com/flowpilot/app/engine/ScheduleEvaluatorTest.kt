package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

class ScheduleEvaluatorTest {
    private fun rule(minute: Int, days: Set<Int> = emptySet()) = Automation(
        id = "time", name = "Time", appPackage = "", triggerEvent = TriggerEvent.TIME_SCHEDULE,
        scheduledMinute = minute, scheduledDays = days, action = ActionType.NFC_ON, createdAt = 1L,
    )

    @Test fun daily_schedule_matches_selected_minute() {
        val now = LocalDateTime.of(2026, 8, 31, 22, 0)
        assertThat(ScheduleEvaluator.matchingRules(listOf(rule(1320)), now)).containsExactly(rule(1320))
    }

    @Test fun weekdays_schedule_skips_sunday() {
        val weekdays = setOf(1, 2, 3, 4, 5)
        val sunday = LocalDateTime.of(2026, 8, 30, 22, 0)
        assertThat(ScheduleEvaluator.matchingRules(listOf(rule(1320, weekdays)), sunday)).isEmpty()
    }

    @Test fun schedule_does_not_match_other_minute() {
        val now = LocalDateTime.of(2026, 8, 31, 22, 1)
        assertThat(ScheduleEvaluator.matchingRules(listOf(rule(1320)), now)).isEmpty()
    }
}
