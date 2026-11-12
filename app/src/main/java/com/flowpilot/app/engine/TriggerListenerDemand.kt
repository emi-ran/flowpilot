package com.flowpilot.app.engine

import com.flowpilot.app.data.model.*

/** Demand is recalculated from enabled rules after every DataStore emission. Conditions need state too. */
data class TriggerListenerDemand(
    val events: Set<TriggerEvent>,
    val appPackages: Set<String>,
    val shakeThreshold: Float?,
    val flipScreenOff: Boolean,
    val needsBattery: Boolean,
    val needsCharger: Boolean,
    val needsScreen: Boolean,
    val needsWifi: Boolean,
) {
    val needsMedia: Boolean
        get() = needs(TriggerEvent.MEDIA_PLAYING, TriggerEvent.MEDIA_PAUSED, TriggerEvent.MEDIA_TRACK_CHANGED)
    fun needs(vararg types: TriggerEvent) = types.any { it in events }
    companion object {
        fun from(rules: List<Automation>): TriggerListenerDemand {
            val active = rules.filter { it.enabled }
            val views = active.triggerViews()
            val events = views.mapTo(mutableSetOf()) { it.triggerEvent }
            val conditions = active.flatMap { it.conditionTree?.flattenConditions() ?: it.conditions }.map { it.type }.toSet()
            return TriggerListenerDemand(
                events = events,
                appPackages = views.filter { it.triggerEvent in setOf(TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED) }.mapTo(mutableSetOf()) { it.appPackage },
                shakeThreshold = ShakeReducer.minimumThreshold(views.filter { it.triggerEvent == TriggerEvent.DEVICE_SHAKE }.map { it.shakeThreshold }),
                flipScreenOff = views.any { it.triggerEvent in setOf(TriggerEvent.DEVICE_FLIPPED_DOWN, TriggerEvent.DEVICE_FLIPPED_UP) && it.flipScreenOffDetection },
                needsBattery = events.any { it in setOf(TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE) } || conditions.any { it in setOf(ConditionType.BATTERY_BELOW, ConditionType.BATTERY_ABOVE, ConditionType.CHARGER_CONNECTED, ConditionType.CHARGER_DISCONNECTED) },
                needsCharger = events.any { it in setOf(TriggerEvent.CHARGER_CONNECTED, TriggerEvent.CHARGER_DISCONNECTED) },
                needsScreen = events.any { it in setOf(TriggerEvent.SCREEN_ON, TriggerEvent.SCREEN_OFF, TriggerEvent.DEVICE_UNLOCKED) } || conditions.any { it in setOf(ConditionType.SCREEN_ON, ConditionType.SCREEN_OFF) },
                needsWifi = events.any { it in setOf(TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED) } || conditions.any { it in setOf(ConditionType.WIFI_CONNECTED, ConditionType.WIFI_DISCONNECTED) },
            )
        }
    }
}

/** Bounded same-source callback burst dedupe, independent of the shared durable rule cooldown.
 * Exact event IDs are retained for two minutes; state-only callbacks coalesce for one second.
 * No event payload is persisted. Different sources are never coalesced by this gate.
 */
class TriggerExecutionDeduplicator {
    private val seen = LinkedHashMap<String, Long>()
    @Synchronized
    fun accept(ruleId: String, revision: Long, source: TriggerEvent, sourceIndex: Int,
        eventId: String? = null, nowMs: Long = System.currentTimeMillis()): Boolean {
        seen.entries.removeAll { nowMs - it.value >= 120_000L }
        val key = "$ruleId:$revision:${source.name}:$sourceIndex:${eventId ?: "state"}"
        val previous = seen[key]
        val ttl = if (eventId == null) 1_000L else 120_000L
        if (previous != null && nowMs - previous < ttl) return false
        seen[key] = nowMs
        while (seen.size > 1000) seen.remove(seen.keys.first())
        return true
    }
}
