package com.flowpilot.app.engine

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** In-memory, at-most-once local minutes; startup skips replay, rollback keeps high-water mark. */
internal class ScheduleWindow {
    private var lastMinute: LocalDateTime? = null

    fun advance(now: LocalDateTime): List<LocalDateTime> {
        val minute = now.truncatedTo(ChronoUnit.MINUTES)
        val previous = lastMinute
        if (previous != null && minute <= previous) return emptyList()
        lastMinute = minute // Claim before actions: failures/cancellation must not replay side effects.
        if (previous == null) return emptyList()
        // ponytail: catch up only last five minutes; durable/offline schedules need persisted claims.
        val first = maxOf(previous.plusMinutes(1), minute.minusMinutes(4))
        return generateSequence(first) { it.plusMinutes(1) }.takeWhile { it <= minute }.toList()
    }
}
