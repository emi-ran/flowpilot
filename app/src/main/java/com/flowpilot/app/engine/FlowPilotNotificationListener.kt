package com.flowpilot.app.engine

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.flowpilot.app.permission.CapabilityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.LinkedHashMap

/**
 * Transient notification event abstraction. Never persists raw text/title/body.
 */
data class TransientNotificationEvent(
    val packageName: String,
    val postTime: Long,
    val key: String,
    val title: String,
    /** Primary notification text; does not include [bigText]. */
    val text: String,
    val bigText: String = "",
    /** Resolved application label, falling back to [packageName]. */
    val appLabel: String = "",
)

/**
 * Pure deduplication helper for notifications to ensure unit-testability without Android framework classes.
 */
class NotificationDeduplicator(private val ttlMs: Long = 60_000L, private val maxEntries: Int = 200) {
    private val recentKeys = LinkedHashMap<String, Entry>(maxEntries)

    @Synchronized
    fun shouldProcess(key: String, postTime: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        trim(currentTime)
        val lastSeen = recentKeys[key]
        if (lastSeen != null && postTime <= lastSeen.postTime) {
            return false
        }
        recentKeys[key] = Entry(postTime = postTime, seenAt = currentTime)
        while (recentKeys.size > maxEntries) {
            recentKeys.entries.iterator().apply {
                next()
                remove()
            }
        }
        return true
    }

    @Synchronized
    fun clear() {
        recentKeys.clear()
    }

    private fun trim(currentTime: Long) {
        val oldestAllowed = currentTime - ttlMs
        val iterator = recentKeys.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.seenAt < oldestAllowed) {
                iterator.remove()
            }
        }
    }

    private data class Entry(val postTime: Long, val seenAt: Long)
}

/**
 * Throttles listener rebind requests. The system can leave an allowed listener unbound after
 * an APK update or an OEM process kill; retrying too often is wasteful, but never retrying
 * leaves notification rules silently dead until the user toggles access.
 */
internal class NotificationListenerRebindGate(private val retryIntervalMs: Long = 30_000L) {
    private var lastAttemptMs: Long? = null

    @Synchronized
    fun shouldAttempt(connected: Boolean, accessGranted: Boolean, nowMs: Long): Boolean {
        if (connected || !accessGranted) return false
        val last = lastAttemptMs
        if (last != null && nowMs - last < retryIntervalMs) return false
        lastAttemptMs = nowMs
        return true
    }
}

/**
 * Pure helper: FlowPilot release and debug builds can run side by side and each posts an
 * ongoing engine notification. A listener must ignore both so a rule with no app filter cannot
 * speak its own status notification (or the sibling build's) back to the user.
 */
internal fun isFlowPilotNotificationPackage(candidate: String, listenerPackage: String): Boolean {
    val base = listenerPackage.removeSuffix(".debug")
    return candidate == listenerPackage || candidate == base || candidate == "$base.debug"
}

/**
 * Listens for posted notifications to trigger rules matching user-configured app & keyword.
 * Never persists or logs sensitive notification content.
 */
class FlowPilotNotificationListener : NotificationListenerService() {

    private var lastWatchdogCheckMs = 0L
    private val intakeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        checkWatchdog()
        if (!isConnected) return
        sbn ?: return
        val pkg = sbn.packageName ?: return
        // Ignore own and sibling-build notifications to prevent trigger loops.
        if (isFlowPilotNotificationPackage(pkg, packageName)) return

        val key = sbn.key ?: "${pkg}_${sbn.id}_${sbn.postTime}"
        val postTime = sbn.postTime

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        intakeScope.launch {
            // Label lookup is a binder call; keep it off the notification callback thread.
            val event = TransientNotificationEvent(
                packageName = pkg,
                postTime = postTime,
                key = key,
                title = title,
                text = text,
                bigText = bigText,
                appLabel = resolveAppLabel(pkg),
            )
            try {
                if (AutomationService.enqueueNotificationIfEngineEnabled(applicationContext, event)) {
                    AutomationService.reconcileEnabled(applicationContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Throwable) {
        packageName
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        checkWatchdog(force = true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        intakeScope.coroutineContext.cancelChildren()
        intakeScope.launch {
            delay(1_000L)
            ensureBound(applicationContext)
        }
    }

    override fun onDestroy() {
        intakeScope.cancel()
        super.onDestroy()
    }

    private fun checkWatchdog(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastWatchdogCheckMs > 60_000L) {
            lastWatchdogCheckMs = now
            intakeScope.launch {
                try {
                    AutomationService.reconcileEnabled(applicationContext)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        private const val TAG = "FlowPilotNotification"

        @Volatile
        var isConnected: Boolean = false
            private set

        internal const val MAX_EVENT_AGE_MS = 2 * 60 * 1000L
        internal const val MAX_QUEUE_SIZE = 100

        private val queueLock = Any()
        private val eventQueue = ArrayDeque<TransientNotificationEvent>(MAX_QUEUE_SIZE)
        private val rebindGate = NotificationListenerRebindGate()
        val deduplicator = NotificationDeduplicator()

        /**
         * Asks the system to bind this listener when access is granted but the process is not
         * connected (observed on HyperOS after an in-place update). Safe to call repeatedly;
         * requests are throttled and ignored while the listener is live.
         */
        fun ensureBound(context: Context, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
            if (isConnected) return true
            val accessGranted = CapabilityManager(context).hasNotificationListenerAccess()
            if (!rebindGate.shouldAttempt(isConnected, accessGranted, nowMs)) return false
            return try {
                Log.i(TAG, "Listener not bound; requesting system rebind")
                NotificationListenerService.requestRebind(ComponentName(context, FlowPilotNotificationListener::class.java))
                true
            } catch (error: Throwable) {
                Log.w(TAG, "Listener rebind request failed", error)
                false
            }
        }

        fun enqueueIfEnabled(engineEnabled: Boolean, event: TransientNotificationEvent): Boolean = synchronized(queueLock) {
            if (!engineEnabled || eventQueue.size >= MAX_QUEUE_SIZE || !deduplicator.shouldProcess(event.key, event.postTime)) {
                return false
            }
            eventQueue.addLast(event)
            true
        }

        fun drainEvents(currentTime: Long = System.currentTimeMillis()): List<TransientNotificationEvent> = synchronized(queueLock) {
            buildList {
                while (eventQueue.isNotEmpty()) {
                    val event = eventQueue.removeFirst()
                    if (event.postTime in (currentTime - MAX_EVENT_AGE_MS)..currentTime) {
                        add(event)
                    }
                }
            }
        }

        fun clearTransientState() = synchronized(queueLock) {
            eventQueue.clear()
            deduplicator.clear()
        }
    }
}
