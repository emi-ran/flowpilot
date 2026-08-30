package com.flowpilot.app.engine

import android.content.Context
import android.util.Log
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
        Log.i(TAG, "Starting FlowPilot Automation Engine")
        job = scope.launch {
            // Warm up after a short delay so the service settles.
            delay(1000)
            while (isActive) {
                try {
                    poll()
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during engine poll: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping FlowPilot Automation Engine")
        running = false
        job?.cancel()
        job = null
    }

    private suspend fun poll() {
        val transitions = withContext(Dispatchers.IO) { tracker.queryNewTransitions() }
        val rules = repository.automations.first()

        if (transitions.isEmpty()) {
            return
        }

        for (transition in transitions) {
            val pkg = transition.packageName
            if (transition.isForeground) {
                if (lastForeground == pkg) continue

                // Previous app closed / left foreground
                if (lastForeground != null) {
                    val prev = lastForeground!!
                    val prevLock = synchronized(openLocks) { openLocks[prev] ?: false }
                    val result = RuleEvaluator.evaluate(rules, AppEvent.CLOSED, prev, prevLock)
                    if (result.toExecute.isNotEmpty()) {
                        Log.i(TAG, "Executing CLOSED rules for $prev (${result.toExecute.size} rule(s))")
                        executeAll(result.toExecute)
                    }
                    synchronized(openLocks) { openLocks.remove(prev) }
                }

                // Current app opened
                val newLock = synchronized(openLocks) { openLocks[pkg] ?: false }
                val result = RuleEvaluator.evaluate(rules, AppEvent.OPENED, pkg, newLock)
                if (result.toExecute.isNotEmpty()) {
                    Log.i(TAG, "Executing OPENED rules for $pkg (${result.toExecute.size} rule(s))")
                    executeAll(result.toExecute)
                    synchronized(openLocks) { openLocks[pkg] = true }
                }

                lastForeground = pkg
                Log.d(TAG, "Foreground changed to: $pkg")
            } else {
                // Background/paused event
                if (lastForeground == pkg) {
                    val prevLock = synchronized(openLocks) { openLocks[pkg] ?: false }
                    val result = RuleEvaluator.evaluate(rules, AppEvent.CLOSED, pkg, prevLock)
                    if (result.toExecute.isNotEmpty()) {
                        Log.i(TAG, "Executing CLOSED rules on background for $pkg (${result.toExecute.size} rule(s))")
                        executeAll(result.toExecute)
                    }
                    synchronized(openLocks) { openLocks.remove(pkg) }
                    lastForeground = null
                }
            }
        }
    }

    private suspend fun executeAll(rules: List<com.flowpilot.app.data.model.Automation>) {
        for (rule in rules) {
            withContext(Dispatchers.IO) {
                var anySuccess = false
                for (action in rule.effectiveActions) {
                    val result = dispatcher.execute(action)
                    Log.i(TAG, "Rule '${rule.name}' action ${action.name} result: success=${result.success}, msg=${result.message}")
                    if (result.success) {
                        anySuccess = true
                    }
                }
                if (anySuccess) {
                    repository.patchLastTriggeredAt(rule.id, System.currentTimeMillis())
                }
            }
        }
    }

    private companion object {
        const val TAG = "FlowPilotEngine"
        const val POLL_INTERVAL_MS = 1000L
    }
}
