package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RuleEvaluatorConditionTest {

    private val zoneId = ZoneId.of("UTC")

    private fun timeMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun timeBetween_daytime_matches_within_window_and_edges() {
        // Window: 09:00 (540 min) to 17:00 (1020 min)
        val condition = RuleCondition(
            type = ConditionType.TIME_BETWEEN,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )
        val state = LiveSystemState()

        // 2026-09-07 is a Monday
        val t0859 = timeMs(2026, 9, 7, 8, 59)
        val t0900 = timeMs(2026, 9, 7, 9, 0)
        val t1300 = timeMs(2026, 9, 7, 13, 0)
        val t1700 = timeMs(2026, 9, 7, 17, 0)
        val t1701 = timeMs(2026, 9, 7, 17, 1)

        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t0859, zoneId)).isFalse()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t0900, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t1300, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t1700, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t1701, zoneId)).isFalse()
    }

    @Test
    fun timeBetween_overnight_matches_late_night_and_early_morning() {
        // Window: 23:00 (1380 min) to 07:00 (420 min)
        val condition = RuleCondition(
            type = ConditionType.TIME_BETWEEN,
            startMinute = 23 * 60,
            endMinute = 7 * 60,
        )
        val state = LiveSystemState()

        val t2259 = timeMs(2026, 9, 7, 22, 59)
        val t2300 = timeMs(2026, 9, 7, 23, 0)
        val t2345 = timeMs(2026, 9, 7, 23, 45)
        val t0000 = timeMs(2026, 9, 8, 0, 0)
        val t0430 = timeMs(2026, 9, 8, 4, 30)
        val t0700 = timeMs(2026, 9, 8, 7, 0)
        val t0701 = timeMs(2026, 9, 8, 7, 1)
        val t1400 = timeMs(2026, 9, 8, 14, 0)

        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t2259, zoneId)).isFalse()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t2300, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t2345, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t0000, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t0430, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t0700, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t0701, zoneId)).isFalse()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, t1400, zoneId)).isFalse()
    }

    @Test
    fun daysOfWeek_matches_weekdays_and_rejects_weekends() {
        // Weekdays: 1 (Mon) .. 5 (Fri)
        val condition = RuleCondition(
            type = ConditionType.DAYS_OF_WEEK,
            days = setOf(1, 2, 3, 4, 5),
        )
        val state = LiveSystemState()

        // 2026-09-07 is Monday (1), 2026-09-11 is Friday (5), 2026-09-12 is Saturday (6), 2026-09-13 is Sunday (7)
        val mon = timeMs(2026, 9, 7, 12, 0)
        val wed = timeMs(2026, 9, 9, 12, 0)
        val fri = timeMs(2026, 9, 11, 12, 0)
        val sat = timeMs(2026, 9, 12, 12, 0)
        val sun = timeMs(2026, 9, 13, 12, 0)

        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, mon, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, wed, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, fri, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, sat, zoneId)).isFalse()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, sun, zoneId)).isFalse()
    }

    @Test
    fun daysOfWeek_empty_days_matches_all_days() {
        val condition = RuleCondition(
            type = ConditionType.DAYS_OF_WEEK,
            days = emptySet(),
        )
        val state = LiveSystemState()

        val sat = timeMs(2026, 9, 12, 12, 0)
        val mon = timeMs(2026, 9, 7, 12, 0)

        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, sat, zoneId)).isTrue()
        assertThat(RuleEvaluator.matchesConditions(listOf(condition), state, mon, zoneId)).isTrue()
    }

    @Test
    fun combined_timeBetween_and_daysOfWeek_and_charger_condition() {
        val conditions = listOf(
            RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 23 * 60, endMinute = 7 * 60),
            RuleCondition(type = ConditionType.DAYS_OF_WEEK, days = setOf(1, 2, 3, 4, 5)),
            RuleCondition(type = ConditionType.CHARGER_CONNECTED),
        )

        val monNight = timeMs(2026, 9, 7, 23, 30) // Mon 23:30
        val satNight = timeMs(2026, 9, 12, 23, 30) // Sat 23:30
        val monDay = timeMs(2026, 9, 7, 14, 0) // Mon 14:00

        // 1. All match: Monday 23:30 + charger connected
        val stateConnected = LiveSystemState(isChargerConnected = true)
        assertThat(RuleEvaluator.matchesConditions(conditions, stateConnected, monNight, zoneId)).isTrue()

        // 2. Charger disconnected -> fails
        val stateDisconnected = LiveSystemState(isChargerConnected = false)
        assertThat(RuleEvaluator.matchesConditions(conditions, stateDisconnected, monNight, zoneId)).isFalse()

        // 3. Weekend -> fails
        assertThat(RuleEvaluator.matchesConditions(conditions, stateConnected, satNight, zoneId)).isFalse()

        // 4. Outside time window -> fails
        assertThat(RuleEvaluator.matchesConditions(conditions, stateConnected, monDay, zoneId)).isFalse()
    }

    @Test
    fun evaluateFlip_with_timeBetween_and_daysOfWeek() {
        val rule = Automation(
            id = "flip-night-rule",
            name = "Night DND on Flip",
            enabled = true,
            triggerEvent = TriggerEvent.DEVICE_FLIPPED_DOWN,
            action = ActionType.DND_ON,
            conditions = listOf(
                RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 23 * 60, endMinute = 7 * 60),
                RuleCondition(type = ConditionType.DAYS_OF_WEEK, days = setOf(1, 2, 3, 4, 5)),
            ),
            createdAt = 1000L,
        )

        val monNight = timeMs(2026, 9, 7, 23, 30)
        val monDay = timeMs(2026, 9, 7, 14, 0)
        val liveState = LiveSystemState(isScreenOn = true)

        // Monday night -> executes
        val matchesNight = RuleEvaluator.evaluateFlip(listOf(rule), FlipEvent.FLIPPED_DOWN, liveState, monNight)
        assertThat(matchesNight).containsExactly(rule)

        // Monday daytime -> filtered out by condition
        val matchesDay = RuleEvaluator.evaluateFlip(listOf(rule), FlipEvent.FLIPPED_DOWN, liveState, monDay)
        assertThat(matchesDay).isEmpty()
    }
}
