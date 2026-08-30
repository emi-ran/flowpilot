package com.flowpilot.app.engine

import android.content.Context
import com.flowpilot.app.actions.ActionDispatcher
import com.flowpilot.app.data.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The automation engine: polls the foreground app, emits open/close events,
 * evaluates enabled rules, dedupes, and executes matching actions.
 *
 * No fake functionality: actions are executed only through capability-aware
 * executors, and only when the rule actually matched an un-deduped event.
 */
class AutomationEngine(context: Context) {

    private val appContext = context.applicationContext
    private val tracker = ForegroundAppTracker(appContext)
    private val repository = AutomationRepository(appContext)
    private val dispatcher = ActionDispatcher.get(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    var running: Boolean = false
        private set

    // State across polls.
    @Volatile
    private var lastForeground: String? = null

    // Keep a lock per package: whether APP_OPENED rules already fired for the
    // current foreground residency of that package (maps package -> locked).
    private val openLocks = HashMap<String, Boolean>()

    fun start() {
        if (job?.isActive == true) return
        running = true
        job = scope.launch {
            // Warm up after a short delay so the service settles.
            delay(1000)
            while (isActive) {
                try {
                    poll()
                } catch (_: Exception) {
                    // Isolated: keep the loop alive.
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    private suspend fun poll() {
        val current = withContext(Dispatchers.IO) { tracker.currentForegroundPackage() }
            ?: return
        val rules = repository.automations.first()

        val previous = lastForeground
        if (previous == current) {
            return // No transition; nothing to evaluate.
        }

        // previous app (if any) is now closing / left foreground.
        if (previous != null) {
            val prevLock = synchronized(openLocks) { openLocks[previous] ?: false }
            val result = RuleEvaluator.evaluate(rules, AppEvent.CLOSED, previous, prevLock)
            executeAll(result.toExecute)
            // Clear the open-lock for the departed app so its OPENED rule can fire again next time.
            synchronized(openLocks) { openLocks.remove(previous) }
        }

        // The new app just opened.
        val newLock = synchronized(openLocks) { openLocks[current] ?: false }
        val result = RuleEvaluator.evaluate(rules, AppEvent.OPENED, current, newLock)
        executeAll(result.toExecute)
        // Once OPENED rules fire, hold the lock so they don't re-fire while app stays foreground.
        if (result.toExecute.isNotEmpty()) {
            synchronized(openLocks) { openLocks[current] = true }
        }

        lastForeground = current
    }

    private suspend fun executeAll(rules: List<com.flowpilot.app.data.model.Automation>) {
        for (rule in rules) {
            withContext(Dispatchers.IO) {
                val result = dispatcher.execute(rule.action)
                if (result.success) {
                    repository.patchLastTriggeredAt(rule.id, System.currentTimeMillis())
                }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1500L
    }
}
