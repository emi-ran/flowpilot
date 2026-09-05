package com.flowpilot.app.engine

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineScheduleWindowRegressionTest {
    private val start = LocalDateTime.of(2026, 9, 5, 23, 58)

    @Test fun startupDoesNotReplayAndDelayCatchesMissedMinutes() {
        val window = ScheduleWindow()
        assertTrue(window.advance(start).isEmpty())
        assertEquals(listOf(start.plusMinutes(1), start.plusMinutes(2)), window.advance(start.plusMinutes(2)))
        assertTrue(window.advance(start.plusMinutes(2)).isEmpty())
    }

    @Test fun clockRollbackNeverReplaysClaimedMinutes() {
        val window = ScheduleWindow()
        window.advance(start)
        window.advance(start.plusMinutes(2))
        assertTrue(window.advance(start).isEmpty())
        assertTrue(window.advance(start.plusMinutes(2)).isEmpty())
        assertEquals(listOf(start.plusMinutes(3)), window.advance(start.plusMinutes(3)))
    }

    @Test fun longGapOnlyReplaysLastFiveMinutes() {
        val window = ScheduleWindow()
        window.advance(start)
        assertEquals((16L..20L).map(start::plusMinutes), window.advance(start.plusMinutes(20)))
        assertTrue(window.advance(start.plusMinutes(20)).isEmpty())
    }
}
