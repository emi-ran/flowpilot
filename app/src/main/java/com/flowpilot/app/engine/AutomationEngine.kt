package com.flowpilot.app.engine

import android.content.Context
import android.util.Log
import com.flowpilot.app.actions.ActionDispatcher
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    private val wifiTracker: WifiStateTracker = WifiStateTracker(context.applicationContext),
    private val bluetoothTracker: BluetoothDeviceTracker = BluetoothDeviceTracker(context.applicationContext),
    private val callTracker: CallStateTracker = CallStateTracker(context.applicationContext),
    private val flipTracker: DeviceFlipTracker = DeviceFlipTracker(context.applicationContext),
    private val shakeTracker: DeviceShakeTracker = DeviceShakeTracker(context.applicationContext),
    private val lightTracker: LightSensorTracker = LightSensorTracker(context.applicationContext),
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
        wifiTracker.start()
        bluetoothTracker.start()
        callTracker.start()
        flipTracker.start()
        shakeTracker.start()
        lightTracker.start()
        Log.i(TAG, "Starting FlowPilot Automation Engine")
        job = scope.launch {
            while (isActive) {
                try {
                    val liveState = getLiveSystemState()
                    updateFlipListeningPolicy(liveState)
                    updateShakeListeningPolicy(liveState)
                    updateLightListeningPolicy(liveState)
                    poll(liveState)
                    pollChargerEvents(liveState)
                    pollBatteryTransitions(liveState)
                    pollScreenEvents(liveState)
                    pollShakeEvents(liveState)
                    pollLightTransitions(liveState)
                    pollWifiTransitions(liveState)
                    pollBluetoothTransitions(liveState)
                    pollCallTransitions(liveState)
                    pollDeviceFlipEvents(liveState)
                    pollNfcTagEvents(liveState)
                    pollNotificationEvents(liveState)
                    pollSmsEvents(liveState)
                    pollSchedules(liveState)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during engine poll (${e.javaClass.simpleName})")
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
        wifiTracker.stop()
        bluetoothTracker.stop()
        callTracker.stop()
        flipTracker.stop()
        shakeTracker.stop()
        lightTracker.stop()
    }

    private fun getLiveSystemState(): LiveSystemState {
        val batteryIntent = appContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else null

        val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isChargerConnected = plugged != 0

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenOn = pm?.isInteractive

        val connectedWifi = wifiTracker.getCurrentConnectedSsid()

        return LiveSystemState(
            batteryPercent = batteryPercent,
            isChargerConnected = isChargerConnected,
            isScreenOn = isScreenOn,
            connectedWifiSsid = connectedWifi,
        )
    }

    suspend fun poll(liveState: LiveSystemState = getLiveSystemState()) {
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
            val result = RuleEvaluator.evaluate(rules, AppEvent.CLOSED, pkg, heldOpenLock = false, liveState = liveState)
            if (result.toExecute.isNotEmpty()) {
                Log.i(TAG, "Executing CLOSED rules for $pkg (${result.toExecute.size} rule(s))")
                executeAll(result.toExecute, trigger = TriggerEvent.APP_CLOSED, liveState = liveState)
            }
        }

        stepOutput.openedPackage?.let { pkg ->
            val result = RuleEvaluator.evaluate(rules, AppEvent.OPENED, pkg, heldOpenLock = false, liveState = liveState)
            if (result.toExecute.isNotEmpty()) {
                Log.i(TAG, "Executing OPENED rules for $pkg (${result.toExecute.size} rule(s))")
                executeAll(result.toExecute, trigger = TriggerEvent.APP_OPENED, liveState = liveState)
            }
        }
    }

    private suspend fun pollSchedules(liveState: LiveSystemState) {
        val now = LocalDateTime.now()
        val occurrence = now.toLocalDate().toEpochDay() * 1440 + now.hour * 60 + now.minute
        val previous = lastScheduleOccurrence
        lastScheduleOccurrence = occurrence
        if (previous == null || previous == occurrence) return

        val matches = ScheduleEvaluator.matchingRules(repository.automations.first(), now, liveState)
        if (matches.isNotEmpty()) {
            Log.i(TAG, "Executing scheduled rules (${matches.size} rule(s))")
            executeAll(matches, trigger = TriggerEvent.TIME_SCHEDULE, liveState = liveState)
        }
    }

    private suspend fun pollChargerEvents(liveState: LiveSystemState) {
        val events = chargerTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateCharger(rules, event, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing $event charger rules (${matches.size} rule(s))")
                val trigger = when (event) {
                    ChargerEvent.CONNECTED -> TriggerEvent.CHARGER_CONNECTED
                    ChargerEvent.DISCONNECTED -> TriggerEvent.CHARGER_DISCONNECTED
                }
                executeAll(matches, trigger = trigger, liveState = liveState.copy(isChargerConnected = (event == ChargerEvent.CONNECTED)))
            }
        }
    }

    private suspend fun pollBatteryTransitions(liveState: LiveSystemState) {
        val transitions = batteryTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateBattery(rules, transition, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing battery threshold rules for ${transition.previous}% -> ${transition.current}% (${matches.size} rule(s))")
                val effectiveState = liveState.copy(batteryPercent = transition.current)
                for (rule in matches) {
                    executeAll(listOf(rule), trigger = rule.triggerEvent, liveState = effectiveState)
                }
            }
        }
    }

    private suspend fun pollScreenEvents(liveState: LiveSystemState) {
        val events = screenTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateScreen(rules, event, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing $event screen rules (${matches.size} rule(s))")
                val trigger = when (event) {
                    ScreenEvent.ON -> TriggerEvent.SCREEN_ON
                    ScreenEvent.OFF -> TriggerEvent.SCREEN_OFF
                    ScreenEvent.UNLOCKED -> TriggerEvent.DEVICE_UNLOCKED
                }
                executeAll(matches, trigger = trigger, liveState = liveState.copy(isScreenOn = (event != ScreenEvent.OFF)))
            }
        }
    }

    private suspend fun pollWifiTransitions(liveState: LiveSystemState) {
        val transitions = wifiTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateWifi(rules, transition, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing Wi-Fi rules for ${transition.event} (${matches.size} rule(s))")
                val trigger = when (transition.event) {
                    WifiStateEvent.CONNECTED -> TriggerEvent.WIFI_CONNECTED
                    WifiStateEvent.DISCONNECTED -> TriggerEvent.WIFI_DISCONNECTED
                }
                val effectiveState = liveState.copy(
                    connectedWifiSsid = if (transition.event == WifiStateEvent.CONNECTED) transition.ssid else null
                )
                executeAll(matches, trigger = trigger, liveState = effectiveState)
            }
        }
    }

    private suspend fun pollNotificationEvents(liveState: LiveSystemState) {
        val events = FlowPilotNotificationListener.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateNotification(rules, event, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing notification rules for ${event.packageName} (${matches.size} rule(s))")
                executeAll(matches, trigger = TriggerEvent.NOTIFICATION_RECEIVED, liveState = liveState)
            }
        }
    }

    private suspend fun pollNfcTagEvents(liveState: LiveSystemState) {
        val events = NfcTagHandoff.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateNfcTag(rules, event, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing NFC tag scanned rules for tag ID [REDACTED_LEN_${event.tagId.length}] (${matches.size} rule(s))")
                executeAll(matches, trigger = TriggerEvent.NFC_TAG_SCANNED, liveState = liveState)
            }
        }
    }

    private suspend fun updateFlipListeningPolicy(liveState: LiveSystemState) {
        val rules = repository.automations.first()
        val flipRules = rules.filter {
            it.enabled && (it.triggerEvent == TriggerEvent.DEVICE_FLIPPED_DOWN || it.triggerEvent == TriggerEvent.DEVICE_FLIPPED_UP)
        }
        flipTracker.updateListeningPolicy(
            hasActiveRules = flipRules.isNotEmpty(),
            anyAllowScreenOff = flipRules.any { it.flipScreenOffDetection },
            isScreenOn = liveState.isScreenOn != false,
        )
    }

    private suspend fun pollDeviceFlipEvents(liveState: LiveSystemState) {
        val events = flipTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateFlip(rules, event, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing device flip rules for $event (${matches.size} rule(s))")
                val trigger = when (event) {
                    FlipEvent.FLIPPED_DOWN -> TriggerEvent.DEVICE_FLIPPED_DOWN
                    FlipEvent.FLIPPED_UP -> TriggerEvent.DEVICE_FLIPPED_UP
                }
                executeAll(matches, trigger = trigger, liveState = liveState)
            }
        }
    }

    private suspend fun updateShakeListeningPolicy(liveState: LiveSystemState) {
        val rules = repository.automations.first()
        val hasShake = rules.any { it.enabled && it.triggerEvent == TriggerEvent.DEVICE_SHAKE }
        shakeTracker.updateListeningPolicy(
            hasActiveRules = hasShake,
            isScreenOn = liveState.isScreenOn != false,
        )
    }

    private suspend fun updateLightListeningPolicy(liveState: LiveSystemState) {
        val rules = repository.automations.first()
        val hasLight = rules.any {
            it.enabled && (it.triggerEvent == TriggerEvent.LIGHT_BELOW || it.triggerEvent == TriggerEvent.LIGHT_ABOVE)
        }
        lightTracker.updateListeningPolicy(
            hasActiveRules = hasLight,
            isScreenOn = liveState.isScreenOn != false,
        )
    }

    private suspend fun pollShakeEvents(liveState: LiveSystemState) {
        val events = shakeTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (eventTime in events) {
            val matches = RuleEvaluator.evaluateShake(rules, liveState, nowMs = eventTime)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing shake rules (${matches.size} rule(s))")
                executeAll(matches, trigger = TriggerEvent.DEVICE_SHAKE, liveState = liveState)
            }
        }
    }

    private suspend fun pollLightTransitions(liveState: LiveSystemState) {
        val transitions = lightTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateLight(rules, transition, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing ambient light rules (${matches.size} rule(s)) for ${transition.previousLux} -> ${transition.currentLux} lx")
                for (rule in matches) {
                    executeAll(listOf(rule), trigger = rule.triggerEvent, liveState = liveState)
                }
            }
        }
    }

    private suspend fun pollBluetoothTransitions(liveState: LiveSystemState) {
        // Permission may be granted while service stays alive; begin listening without restarting engine.
        bluetoothTracker.start()
        val transitions = bluetoothTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateBluetooth(rules, transition, liveState)
            val trigger = when (transition.event) {
                BluetoothDeviceEvent.CONNECTED -> TriggerEvent.BLUETOOTH_CONNECTED
                BluetoothDeviceEvent.DISCONNECTED -> TriggerEvent.BLUETOOTH_DISCONNECTED
            }
            val unmatchedRules = rules.count { it.enabled && it.triggerEvent == trigger } - matches.size
            Log.i(BLUETOOTH_TAG, "Unmatched transition rules=$unmatchedRules")
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing $trigger Bluetooth rules (${matches.size} rule(s))")
                executeAll(matches, trigger = trigger, liveState = liveState)
            }
        }
    }

    private suspend fun pollCallTransitions(liveState: LiveSystemState) {
        callTracker.start()
        val transitions = callTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateCall(rules, transition, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing ${transition.triggerEvent.name} call rules (${matches.size} rule(s))")
                executeAll(matches, trigger = transition.triggerEvent, liveState = liveState)
            }
        }
    }

    private suspend fun pollSmsEvents(liveState: LiveSystemState) {
        val events = SmsEventTracker.drainEvents()
        if (events.isEmpty()) return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateSms(rules, event, liveState)
            if (matches.isNotEmpty()) {
                val masked = PhoneNumberUtils.mask(event.sender)
                Log.i(TAG, "Executing SMS received rules for $masked (${matches.size} rule(s))")
                executeAll(
                    rules = matches,
                    trigger = TriggerEvent.SMS_RECEIVED,
                    liveState = liveState,
                    smsSender = event.sender,
                    smsBody = event.body,
                )
            }
        }
    }

    private suspend fun executeAll(
        rules: List<com.flowpilot.app.data.model.Automation>,
        trigger: TriggerEvent? = null,
        liveState: LiveSystemState = LiveSystemState(),
        smsSender: String? = null,
        smsBody: String? = null,
    ) {
        val coords = if (rules.any { it.requiresLocation() }) {
            LocationFetcher.getCoordinates(appContext, isBackgroundExecution = true)
        } else {
            null
        }
        val templateContext = com.flowpilot.app.actions.WebhookTemplateContext(
            trigger = trigger?.name ?: "",
            timestamp = System.currentTimeMillis(),
            batteryPercent = liveState.batteryPercent,
            isCharging = liveState.isChargerConnected,
            wifiSsid = liveState.connectedWifiSsid,
            smsSender = smsSender,
            smsBody = smsBody,
            locationLat = coords?.first,
            locationLng = coords?.second,
        )
        for (rule in rules) {
            withContext(Dispatchers.IO) {
                var anySuccess = false
                val actionRecords = mutableListOf<ActionExecutionRecord>()
                val actions = rule.effectiveActions
                val delays = rule.effectiveActionDelays
                var currentAction: com.flowpilot.app.data.model.ActionType? = null

                try {
                    for (i in actions.indices) {
                        val action = actions[i]
                        currentAction = action
                        val delaySec = delays.getOrElse(i) { 0 }

                        if (delaySec > 0) {
                            delay(delaySec * 1000L)
                        }

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
                                alarmHour = rule.alarmHour,
                                alarmMinute = rule.alarmMinute,
                                alarmMessage = rule.alarmMessage,
                                timerDurationSeconds = rule.timerDurationSeconds,
                                timerMessage = rule.timerMessage,
                                webhookMethod = rule.webhookMethod,
                                webhookUrl = rule.webhookUrl,
                                webhookHeaders = rule.webhookHeaders,
                                webhookBody = rule.webhookBody,
                                webhookTimeoutSeconds = rule.webhookTimeoutSeconds,
                                webhookTemplateContext = templateContext,
                                phoneNumber = rule.phoneNumber,
                                screenBrightnessPercent = rule.screenBrightnessPercent,
                                forceStopPackage = rule.forceStopPackage,
                                smsRecipient = rule.smsRecipient,
                                smsMessage = rule.smsMessage,
                            ),
                        )
                        Log.i(TAG, "Rule action result: action=${action.name}, success=${result.success}")
                        if (result.success) {
                            anySuccess = true
                        }
                        actionRecords.add(
                            ActionExecutionRecord.create(
                                actionType = action,
                                success = result.success,
                                message = result.message,
                            )
                        )
                        currentAction = null
                    }
                } catch (ce: CancellationException) {
                    currentAction?.let { action ->
                        actionRecords.add(
                            ActionExecutionRecord.create(
                                actionType = action,
                                success = false,
                                message = "Execution cancelled",
                            )
                        )
                    }
                    throw ce
                } finally {
                    val historyEntry = ExecutionHistoryEntry.create(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        trigger = trigger?.name ?: rule.triggerEvent.name,
                        timestamp = System.currentTimeMillis(),
                        actions = actionRecords,
                    )
                    withContext(NonCancellable) {
                        repository.appendHistory(historyEntry)
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
        const val BLUETOOTH_TAG = "FlowPilotBluetooth"
        const val POLL_INTERVAL_MS = 500L
    }
}
