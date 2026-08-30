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
    private var lastKnownPackage: String? = null

    /**
     * @return the most recently foregrounded package, or null if none found /
     *         if Usage Access is not granted.
     */
    fun currentForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Look back far enough to catch a launched app even on a slow poll.
        val now = System.currentTimeMillis()
        val begin = now - 60_000L
        val events = try {
            usm.queryEvents(begin, now)
        } catch (_: SecurityException) {
            return null
        }

        var current: String? = null
        var event: UsageEvents.Event
        while (events.hasNextEvent()) {
            event = UsageEvents.Event()
            if (!events.getNextEvent(event)) break
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                current = event.packageName
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                current = event.packageName
            }
        }
        if (current != null) lastKnownPackage = current
        // UsageEvents only records transitions. Once an app has been foreground
        // for longer than the query window, retain the last known foreground
        // package instead of incorrectly reporting "no app".
        return current ?: lastKnownPackage
    }
}
