package com.flowpilot.app.engine

import java.util.ArrayDeque
import java.util.LinkedHashMap

/**
 * Event generated when an incoming SMS message is received and reassembled.
 */
data class SmsReceivedEvent(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Thread-safe tracker and deduplicator for incoming SMS events.
 */
object SmsEventTracker {

    internal const val MAX_EVENT_AGE_MS = 2 * 60 * 1000L
    internal const val MAX_QUEUE_SIZE = 100
    internal const val MAX_DEDUPE_ENTRIES = 200
    private const val DEDUPE_TTL_MS = 60_000L

    private val lock = Any()
    private val eventQueue = ArrayDeque<SmsReceivedEvent>(MAX_QUEUE_SIZE)
    private val recentDedupeKeys = LinkedHashMap<String, Long>(MAX_DEDUPE_ENTRIES)

    /**
     * Accepts an SMS only after AutomationService has checked persisted engine state under its control lock.
     */
    fun enqueueIfEnabled(
        engineEnabled: Boolean,
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        if (!engineEnabled || eventQueue.size >= MAX_QUEUE_SIZE) return false
        val dedupeKey = "${PhoneNumberUtils.normalize(sender)}_${body.hashCode()}_${timestamp / 5000}"
        val now = System.currentTimeMillis()

        trimOldKeys(now)
        val lastSeen = recentDedupeKeys[dedupeKey]
        if (lastSeen != null && now - lastSeen < DEDUPE_TTL_MS) {
            return false
        }
        recentDedupeKeys[dedupeKey] = now
        while (recentDedupeKeys.size > MAX_DEDUPE_ENTRIES) {
            recentDedupeKeys.entries.iterator().apply {
                next()
                remove()
            }
        }
        eventQueue.addLast(SmsReceivedEvent(sender = sender, body = body, timestamp = timestamp))
        return true
    }

    /**
     * Drains all pending SMS received events in FIFO order.
     */
    fun drainEvents(currentTime: Long = System.currentTimeMillis()): List<SmsReceivedEvent> = synchronized(lock) {
        buildList {
            while (eventQueue.isNotEmpty()) {
                val event = eventQueue.removeFirst()
                if (event.timestamp in (currentTime - MAX_EVENT_AGE_MS)..currentTime) {
                    add(event)
                }
            }
        }
    }

    fun clear() = synchronized(lock) {
        eventQueue.clear()
        recentDedupeKeys.clear()
    }

    private fun trimOldKeys(currentTime: Long) {
        val oldestAllowed = currentTime - DEDUPE_TTL_MS
        val it = recentDedupeKeys.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value < oldestAllowed) {
                it.remove()
            }
        }
    }
}
