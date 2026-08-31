package com.flowpilot.app.engine

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Detects foreground/background app transitions using UsageStatsManager (Usage Access).
 * Uses queryEvents for precise event sequence rather than coarse aggregates.
 */
class ForegroundAppTracker(
    private val context: Context,
    initialEventTime: Long = System.currentTimeMillis(),
) {

    @Volatile
    private var lastEventTime: Long = initialEventTime

    data class Transition(
        val packageName: String,
        val isForeground: Boolean,
        val timestamp: Long,
    )

    /**
     * @return all foreground/background transition events since the last poll in chronological order.
     */
    fun queryNewTransitions(): List<Transition> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val begin = (lastEventTime - 2000L).coerceAtLeast(now - 15 * 60_000L)
        val events = try {
            usm.queryEvents(begin, now)
        } catch (_: Throwable) {
            return emptyList()
        } ?: return emptyList()

        val list = mutableListOf<Transition>()
        var event: UsageEvents.Event
        var maxTime = lastEventTime
        while (events.hasNextEvent()) {
            event = UsageEvents.Event()
            if (!events.getNextEvent(event)) break
            if (event.timeStamp <= lastEventTime) continue
            if (event.timeStamp > maxTime) maxTime = event.timeStamp

            @Suppress("DEPRECATION")
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    list.add(Transition(event.packageName, isForeground = true, timestamp = event.timeStamp))
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    list.add(Transition(event.packageName, isForeground = false, timestamp = event.timeStamp))
                }
            }
        }
        if (maxTime > lastEventTime) {
            lastEventTime = maxTime
        }
        return list
    }
}
