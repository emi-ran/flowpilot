package com.flowpilot.app.engine

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Detects which app is currently in the foreground using UsageStatsManager
 * (Usage Access). Uses queryEvents for precise MOVE_TO_FOREGROUND timestamps
 * rather than the coarse queryUsageStats aggregation.
 */
class ForegroundAppTracker(private val context: Context) {

    @Volatile
    private var lastEventTime: Long = System.currentTimeMillis() - 10_000L

    @Volatile
    private var lastKnownPackage: String? = null

    data class Transition(
        val packageName: String,
        val isForeground: Boolean,
        val timestamp: Long,
    )

    /**
     * @return all foreground/background transition events since the last poll.
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
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                list.add(Transition(event.packageName, isForeground = true, timestamp = event.timeStamp))
                lastKnownPackage = event.packageName
            }
        }
        if (maxTime > lastEventTime) {
            lastEventTime = maxTime
        }
        return list
    }

    /**
     * @return the most recently foregrounded package, or null if none found /
     *         if Usage Access is not granted.
     */
    fun currentForegroundPackage(): String? {
        val transitions = queryNewTransitions()
        val lastFg = transitions.lastOrNull { it.isForeground }?.packageName
        if (lastFg != null) return lastFg

        if (lastKnownPackage != null) return lastKnownPackage

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val begin = now - 15 * 60_000L
        return try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
            val mostRecent = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (mostRecent != null) {
                lastKnownPackage = mostRecent
            }
            mostRecent
        } catch (_: Throwable) {
            null
        }
    }
}
