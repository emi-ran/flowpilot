package com.flowpilot.app.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

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

    private val eventQueue = ConcurrentLinkedQueue<SmsReceivedEvent>()
    private val recentDedupeKeys = ConcurrentHashMap<String, Long>()
    private const val DEDUPE_TTL_MS = 60_000L
    private const val MAX_DEDUPE_ENTRIES = 200

    /**
     * Enqueues an SMS received event if it has not been processed within the deduplication window.
     */
    fun enqueue(sender: String, body: String, timestamp: Long = System.currentTimeMillis()): Boolean {
        val dedupeKey = "${PhoneNumberUtils.normalize(sender)}_${body.hashCode()}_${timestamp / 5000}"
        val now = System.currentTimeMillis()

        val lastSeen = recentDedupeKeys[dedupeKey]
        if (lastSeen != null && now - lastSeen < DEDUPE_TTL_MS) {
            return false
        }
        recentDedupeKeys[dedupeKey] = now
        trimOldKeys(now)

        eventQueue.add(SmsReceivedEvent(sender = sender, body = body, timestamp = timestamp))
        return true
    }

    /**
     * Drains all pending SMS received events in FIFO order.
     */
    fun drainEvents(): List<SmsReceivedEvent> = buildList {
        while (true) {
            add(eventQueue.poll() ?: break)
        }
    }

    fun clear() {
        eventQueue.clear()
        recentDedupeKeys.clear()
    }

    private fun trimOldKeys(currentTime: Long) {
        if (recentDedupeKeys.size > MAX_DEDUPE_ENTRIES) {
            val oldestAllowed = currentTime - DEDUPE_TTL_MS
            val it = recentDedupeKeys.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.value < oldestAllowed) {
                    it.remove()
                }
            }
        }
    }
}
