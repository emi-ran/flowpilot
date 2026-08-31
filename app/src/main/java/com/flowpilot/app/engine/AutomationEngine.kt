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
import java.time.LocalDateTime

/**
 * The automation engine: polls the foreground app transitions, reduces batch to final state,
 * evaluates enabled rules, dedupes, and executes matching actions.
 */
class AutomationEngine(
    context: Context,
    private val tracker: ForegroundAppTracker = ForegroundAppTracker(context.applicationContext),
    private val chargerTracker: ChargerStateTracker = ChargerStateTracker(context.applicationContext),
    private val batteryTracker: BatteryLevelTracker = BatteryLevelTracker(context.applicationContext),
    private val screenTracker: ScreenStateTracker = ScreenStateTracker(context.applicationContext),
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
    private var lastScheduleOccurrence: Long? = null

    fun start() {
        if (job?.isActive == true) return
        running = true
        chargerTracker.start()
        batteryTracker.start()
        screenTracker.start()
        Log.i(TAG, "Starting FlowPilot Automation Engine")
        job = scope.launch {
            while (isActive) {
                try {
                    poll()
                    pollChargerEvents()
                    pollBatteryTransitions()
                    pollScreenEvents()
                    pollSchedules()
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
        chargerTracker.stop()
        batteryTracker.stop()
        screenTracker.stop()
    }

    suspend fun poll() {
        val transitions = withContext(Dispatchers.IO) { tracker.queryNewTransitions() }
        if (transitions.isEmpty()) return

        Log.i(TAG, "Foreground transitions: ${transitions.joinToString { "${it.packageName}:${if (it.isForeground) "open" else "close"}" }}")

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

    private suspend fun pollSchedules() {
        val now = LocalDateTime.now()
        val occurrence = now.toLocalDate().toEpochDay() * 1440 + now.hour * 60 + now.minute
        val previous = lastScheduleOccurrence
        lastScheduleOccurrence = occurrence
        if (previous == null || previous == occurrence) return

        val matches = ScheduleEvaluator.matchingRules(repository.automations.first(), now)
        if (matches.isNotEmpty()) {
            Log.i(TAG, "Executing scheduled rules (${matches.size} rule(s))")
            executeAll(matches)
        }
    }

    private suspend fun pollChargerEvents() {
        val events = chargerTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        Log.i(TAG, "Loaded ${rules.size} rule(s) for foreground evaluation")
        for (event in events) {
            val matches = RuleEvaluator.evaluateCharger(rules, event)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing $event charger rules (${matches.size} rule(s))")
                executeAll(matches)
            }
        }
    }

    private suspend fun pollBatteryTransitions() {
        val transitions = batteryTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateBattery(rules, transition)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing battery threshold rules for ${transition.previous}% -> ${transition.current}% (${matches.size} rule(s))")
                executeAll(matches)
            }
        }
    }

    private suspend fun pollScreenEvents() {
        val events = screenTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateScreen(rules, event)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing $event screen rules (${matches.size} rule(s))")
                executeAll(matches)
            }
        }
    }

    private suspend fun executeAll(rules: List<com.flowpilot.app.data.model.Automation>) {
        for (rule in rules) {
            withContext(Dispatchers.IO) {
                var anySuccess = false
                for (action in rule.effectiveActions) {
                    val result = dispatcher.execute(
                        action,
                        com.flowpilot.app.actions.ActionParameters(
                            notificationTitle = rule.notificationTitle,
                            notificationBody = rule.notificationBody,
                            vibrationPattern = rule.vibrationPattern,
                            vibrationDurationMs = rule.vibrationDurationMs,
                            vibrationAmplitude = rule.vibrationAmplitude,
                            mediaVolumePercent = rule.mediaVolumePercent,
                            soundPreset = rule.soundPreset,
                            soundUri = rule.soundUri,
                            soundDurationMs = rule.soundDurationMs,
                            launchPackage = rule.launchPackage,
                            url = rule.url,
                            ttsText = rule.ttsText,
                            ttsVoiceName = rule.ttsVoiceName,
                            ttsSpeechRate = rule.ttsSpeechRate,
                            ttsAudioFileName = rule.ttsAudioFileName,
                        ),
                    )
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
