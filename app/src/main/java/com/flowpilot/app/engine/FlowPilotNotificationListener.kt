package com.flowpilot.app.engine

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.flowpilot.app.data.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Transient notification event abstraction. Never persists raw text/title/body.
 */
data class TransientNotificationEvent(
    val packageName: String,
    val postTime: Long,
    val key: String,
    val title: String,
    val text: String,
)

/**
 * Pure deduplication helper for notifications to ensure unit-testability without Android framework classes.
 */
class NotificationDeduplicator(private val ttlMs: Long = 60_000L, private val maxEntries: Int = 200) {
    private val recentKeys = ConcurrentHashMap<String, Long>()

    fun shouldProcess(key: String, postTime: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        val lastSeen = recentKeys[key]
        if (lastSeen != null && postTime <= lastSeen) {
            return false
        }
        recentKeys[key] = postTime
        trim(currentTime)
        return true
    }

    fun clear() {
        recentKeys.clear()
    }

    private fun trim(currentTime: Long) {
        if (recentKeys.size > maxEntries) {
            val oldestAllowed = currentTime - ttlMs
            val iterator = recentKeys.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < oldestAllowed) {
                    iterator.remove()
                }
            }
        }
    }
}

/**
 * Listens for posted notifications to trigger rules matching user-configured app & keyword.
 * Never persists or logs sensitive notification content.
 */
class FlowPilotNotificationListener : NotificationListenerService() {

    private var lastWatchdogCheckMs = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        checkWatchdog()
        sbn ?: return
        val pkg = sbn.packageName ?: return
        // Ignore own notifications to prevent loops
        if (pkg == packageName) return

        val key = sbn.key ?: "${pkg}_${sbn.id}_${sbn.postTime}"
        val postTime = sbn.postTime

        if (!deduplicator.shouldProcess(key, postTime)) {
            return
        }

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val combinedText = if (bigText.isNotBlank()) "$text $bigText" else text

        val event = TransientNotificationEvent(
            packageName = pkg,
            postTime = postTime,
            key = key,
            title = title,
            text = combinedText,
        )

        eventQueue.add(event)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        checkWatchdog(force = true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    private fun checkWatchdog(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastWatchdogCheckMs > 60_000L) {
            lastWatchdogCheckMs = now
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AutomationService.reconcileEnabled(applicationContext)
                } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        private val eventQueue = ConcurrentLinkedQueue<TransientNotificationEvent>()
        val deduplicator = NotificationDeduplicator()

        fun drainEvents(): List<TransientNotificationEvent> = buildList {
            while (true) add(eventQueue.poll() ?: break)
        }
    }
}
