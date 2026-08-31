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
 * The automation engine: polls the foreground app transitions, reduces batch to final state,
 * evaluates enabled rules, dedupes, and executes matching actions.
 */
class AutomationEngine(
    context: Context,
    private val tracker: ForegroundAppTracker = ForegroundAppTracker(context.applicationContext),
) {

    private val appContext = context.applicationContext
    private val repository = AutomationRepository(appContext)
    private val dispatcher = ActionDispatcher.get(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    private var state: ForegroundState = ForegroundState()

    fun start() {
        if (job?.isActive == true) return
        running = true
        Log.i(TAG, "Starting FlowPilot Automation Engine")
        job = scope.launch {
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

    suspend fun poll() {
        val transitions = withContext(Dispatchers.IO) { tracker.queryNewTransitions() }
        if (transitions.isEmpty()) return

        val domainTransitions = transitions.map {
            ForegroundReducer.Transition(
                packageName = it.packageName,
                isForeground = it.isForeground,
                timestamp = it.timestamp,
            )
        }

        val stepOutput = synchronized(this) {
            val output = ForegroundReducer.reduceBatch(state, domainTransitions)
            state = output.state
            output
        }

        val rules = repository.automations.first()

        stepOutput.closedPackage?.let { pkg ->
            val result = RuleEvaluator.evaluate(rules, AppEvent.CLOSED, pkg, heldOpenLock = false)
            if (result.toExecute.isNotEmpty()) {
                Log.i(TAG, "Executing CLOSED rules for $pkg (${result.toExecute.size} rule(s))")
                executeAll(result.toExecute)
            }
        }

        stepOutput.openedPackage?.let { pkg ->
            val result = RuleEvaluator.evaluate(rules, AppEvent.OPENED, pkg, heldOpenLock = false)
            if (result.toExecute.isNotEmpty()) {
                Log.i(TAG, "Executing OPENED rules for $pkg (${result.toExecute.size} rule(s))")
                executeAll(result.toExecute)
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
        const val POLL_INTERVAL_MS = 500L
    }
}
