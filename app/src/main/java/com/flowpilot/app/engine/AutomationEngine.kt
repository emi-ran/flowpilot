package com.flowpilot.app.engine

import android.content.Context
import android.util.Log
import com.flowpilot.app.actions.ActionDispatcher
import com.flowpilot.app.actions.ActionParametersFactory
import com.flowpilot.app.actions.TemplateContext
import com.flowpilot.app.actions.TemplateRenderer
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.triggerViews
import com.flowpilot.app.data.model.hasTrigger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Locale

/**
 * Builds the transient [TemplateContext] for one engine event.
 *
 * Event-specific values live only in this in-memory object; they are never persisted in rule
 * fields, history, or logs. [forRule] adds per-candidate values (rule name, geofence name, app label).
 */
internal object EventTemplateContext {

    fun notification(event: TransientNotificationEvent, liveState: LiveSystemState): TemplateContext =
        base(
            trigger = TriggerEvent.NOTIFICATION_RECEIVED.name,
            timestamp = event.postTime,
            liveState = liveState,
            values = linkedMapOf(
                "notification.title" to event.title,
                "notification.text" to event.text,
                "notification.bigText" to event.bigText,
                "notification.package" to event.packageName,
                "notification.app" to event.appLabel.ifBlank { event.packageName },
            ),
        )

    fun sms(event: SmsReceivedEvent, liveState: LiveSystemState): TemplateContext {
        val otp = TemplateRenderer.extractOtp(event.body)
        return base(
            trigger = TriggerEvent.SMS_RECEIVED.name,
            timestamp = event.timestamp,
            liveState = liveState,
            values = buildMap<String, String> {
                put("sms.sender", event.sender)
                put("sms.body", event.body)
                if (otp != null) put("sms.otp", otp)
            },
        ).copy(smsSender = event.sender, smsBody = event.body, smsOtp = otp)
    }

    fun call(
        transition: CallTransition,
        liveState: LiveSystemState,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext = base(
        trigger = transition.triggerEvent.name,
        timestamp = timestampMs,
        liveState = liveState,
        values = buildMap<String, String> {
            put("call.state", callState(transition.triggerEvent))
            // Known-empty on Android 12+ where the number may be withheld; strict TTS must not
            // confuse that with an unknown variable.
            put("call.number", transition.phoneNumber?.takeIf { it.isNotBlank() }.orEmpty())
        },
    )

    fun app(
        trigger: TriggerEvent,
        packageName: String,
        label: String?,
        liveState: LiveSystemState,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext = base(
        trigger = trigger.name,
        timestamp = timestampMs,
        liveState = liveState,
        values = mapOf(
            "app.package" to packageName,
            "app.name" to (label?.takeIf { it.isNotBlank() } ?: packageName),
        ),
    )

    fun wifi(
        transition: WifiTransition,
        liveState: LiveSystemState,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext {
        val trigger = when (transition.event) {
            WifiStateEvent.CONNECTED -> TriggerEvent.WIFI_CONNECTED
            WifiStateEvent.DISCONNECTED -> TriggerEvent.WIFI_DISCONNECTED
        }
        return base(trigger.name, timestampMs, liveState, mapOf("wifi.ssid" to transition.ssid))
            .copy(wifiSsid = transition.ssid)
    }

    fun bluetooth(
        transition: BluetoothDeviceTransition,
        liveState: LiveSystemState,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext {
        val trigger = when (transition.event) {
            BluetoothDeviceEvent.CONNECTED -> TriggerEvent.BLUETOOTH_CONNECTED
            BluetoothDeviceEvent.DISCONNECTED -> TriggerEvent.BLUETOOTH_DISCONNECTED
        }
        return base(
            trigger.name,
            timestampMs,
            liveState,
            mapOf(
                "bluetooth.name" to transition.name.orEmpty(),
                "bluetooth.address" to transition.address,
            ),
        )
    }

    fun media(event: MediaSessionEvent, liveState: LiveSystemState): TemplateContext =
        base(
            trigger = event.event.name,
            timestamp = System.currentTimeMillis(),
            liveState = liveState,
            values = mapOf("media.title" to event.title, "media.artist" to event.artist),
        )

    fun nfc(event: NfcTagScannedEvent, liveState: LiveSystemState): TemplateContext =
        base(
            trigger = TriggerEvent.NFC_TAG_SCANNED.name,
            timestamp = event.timestamp,
            liveState = liveState,
            values = mapOf("nfc.tag" to NfcTagUtils.normalizeTagId(event.tagId)),
        )

    fun shake(event: ShakeEvent, liveState: LiveSystemState): TemplateContext =
        base(
            trigger = TriggerEvent.DEVICE_SHAKE.name,
            timestamp = event.timestampMs,
            liveState = liveState,
            values = mapOf("shake.magnitude" to String.format(Locale.US, "%.1f", event.magnitude)),
        )

    fun light(
        transition: LightTransition,
        trigger: TriggerEvent,
        liveState: LiveSystemState,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext =
        base(
            trigger = trigger.name,
            timestamp = timestampMs,
            liveState = liveState,
            values = mapOf("light.lux" to formatLux(transition.currentLux)),
        )

    fun geofence(
        transition: GeofenceTransition,
        geofenceName: String,
        liveState: LiveSystemState,
    ): TemplateContext {
        val trigger = when (transition.event) {
            GeofenceEvent.ENTER -> TriggerEvent.GEOFENCE_ENTER
            GeofenceEvent.EXIT -> TriggerEvent.GEOFENCE_EXIT
        }
        val coordinates = transition.coordinates
        return base(
            trigger = trigger.name,
            timestamp = transition.timestamp,
            liveState = liveState,
            values = mapOf("geofence.name" to geofenceName),
        ).copy(locationLat = coordinates?.first, locationLng = coordinates?.second)
    }

    fun screen(event: ScreenEvent, liveState: LiveSystemState): TemplateContext {
        val trigger = when (event) {
            ScreenEvent.ON -> TriggerEvent.SCREEN_ON
            ScreenEvent.OFF -> TriggerEvent.SCREEN_OFF
            ScreenEvent.UNLOCKED -> TriggerEvent.DEVICE_UNLOCKED
        }
        return base(
            trigger.name,
            System.currentTimeMillis(),
            liveState,
            mapOf("screen.on" to (event != ScreenEvent.OFF).toString()),
        )
    }

    fun charger(event: ChargerEvent, liveState: LiveSystemState): TemplateContext {
        val trigger = when (event) {
            ChargerEvent.CONNECTED -> TriggerEvent.CHARGER_CONNECTED
            ChargerEvent.DISCONNECTED -> TriggerEvent.CHARGER_DISCONNECTED
        }
        return base(trigger.name, System.currentTimeMillis(), liveState)
            .copy(isCharging = event == ChargerEvent.CONNECTED)
    }

    /** Events without event-specific values; the trigger itself comes from [forRule]. */
    fun live(
        liveState: LiveSystemState,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext = base("", timestampMs, liveState)

    fun manual(
        liveState: LiveSystemState,
        locationLat: Double? = null,
        locationLng: Double? = null,
        timestampMs: Long = System.currentTimeMillis(),
    ): TemplateContext = base("MANUAL", timestampMs, liveState)
        .copy(locationLat = locationLat, locationLng = locationLng)

    /** Adds candidate-scoped values; a literal `${trigger}` always names the matched trigger. */
    fun forRule(
        base: TemplateContext,
        rule: com.flowpilot.app.data.model.Automation,
    ): TemplateContext {
        val extra = buildMap<String, String> {
            put("rule.name", rule.normalizedName)
            when (rule.triggerEvent) {
                TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> {
                    val packageName = base.values["app.package"].orEmpty()
                    put("app.name", rule.appName.ifBlank { packageName })
                }
                TriggerEvent.GEOFENCE_ENTER, TriggerEvent.GEOFENCE_EXIT ->
                    put("geofence.name", rule.geofenceName)
                else -> Unit
            }
        }
        return base.copy(
            trigger = rule.triggerEvent.name,
            triggerSourceIndex = rule.matchedTriggerIndex + 1,
            values = base.values + extra,
        )
    }

    private fun base(
        trigger: String,
        timestamp: Long,
        liveState: LiveSystemState,
        values: Map<String, String> = emptyMap(),
    ): TemplateContext = TemplateContext(
        trigger = trigger,
        timestamp = timestamp,
        batteryPercent = liveState.batteryPercent,
        isCharging = liveState.isChargerConnected,
        wifiSsid = liveState.connectedWifiSsid,
        values = values,
    )

    private fun callState(trigger: TriggerEvent): String = when (trigger) {
        TriggerEvent.CALL_RINGING -> "RINGING"
        TriggerEvent.CALL_ANSWERED -> "ANSWERED"
        TriggerEvent.CALL_OUTGOING -> "OUTGOING"
        else -> "ENDED"
    }

    private fun formatLux(value: Float): String =
        if (value.isFinite()) String.format(Locale.US, "%.1f", value) else ""
}

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
    private val geofenceTracker: GeofenceTracker = GeofenceTracker(context.applicationContext),
    private val onRunningChanged: (Boolean) -> Unit = {},
    private val onFailure: () -> Unit = {},
) {

    private val appContext = context.applicationContext
    private val repository = AutomationRepository(appContext)
    private val dispatcher = ActionDispatcher.get(appContext)
    private val mediaEvents = Channel<MediaSessionEvent>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mediaTracker = MediaSessionTracker(appContext) { mediaEvents.trySend(it) }
    private var mediaEventSequence = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    @Volatile
    private var state: ForegroundState = ForegroundState()
    private val scheduleWindow = ScheduleWindow()
    private val triggerDeduplicator = TriggerExecutionDeduplicator()

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        val previous = job
        job = scope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            // Across service instances, old actions/history and cleanup finish before new trackers start.
            engineLifetime.withLock {
                try {
                    if (repository.isEngineEnabled.first()) runLoop()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onFailure()
                    Log.e(TAG, "Engine failed (${e.javaClass.simpleName})")
                } finally {
                    val engineDisabled = runCatching { !repository.isEngineEnabled.first() }.getOrDefault(false)
                    stopTrackers(teardownGeofences = engineDisabled)
                    onRunningChanged(false)
                }
            }
        }
        job?.start()
    }

    private suspend fun runLoop() = coroutineScope {
        scheduleWindow.advance(LocalDateTime.now())
        tracker.reset()
        state = ForegroundState()
        reconcileListeners(repository.automations.first())
        onRunningChanged(true)
        Log.i(TAG, "Starting FlowPilot Automation Engine")
        // Policy stays responsive while action execution suspends (including long action delays).
        launch {
            repository.automations.collectLatest { rules ->
                while (isActive) {
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val liveState = LiveSystemState(isScreenOn = pm?.isInteractive)
                    reconcileListeners(rules)
                    updateFlipListeningPolicy(rules, liveState)
                    updateShakeListeningPolicy(rules, liveState)
                    updateLightListeningPolicy(rules, liveState)
                    geofenceTracker.updateListeningPolicy(rules)
                    // Geofence request IDs identify both parent rule and definition.
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        while (isActive) {
            try {
                val liveState = getLiveSystemState()
                poll(liveState)
                pollChargerEvents(liveState)
                pollBatteryTransitions(liveState)
                pollScreenEvents(liveState)
                pollShakeEvents(liveState)
                pollLightTransitions(liveState)
                pollWifiTransitions(liveState)
                pollBluetoothTransitions(liveState)
                pollCallTransitions(liveState)
                pollMediaEvents(liveState)
                pollDeviceFlipEvents(liveState)
                pollNfcTagEvents(liveState)
                pollNotificationEvents(liveState)
                pollSmsEvents(liveState)
                pollGeofenceEvents(liveState)
                pollSchedules(liveState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Exception during engine poll (${e.javaClass.simpleName})")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    @Synchronized
    fun stop() {
        Log.i(TAG, "Stopping FlowPilot Automation Engine")
        job?.cancel()
    }

    private fun stopTrackers(teardownGeofences: Boolean = false) {
        listOf<() -> Unit>(
            chargerTracker::stop, batteryTracker::stop, screenTracker::stop,
            wifiTracker::stop, bluetoothTracker::stop, callTracker::stop,
            flipTracker::stop, shakeTracker::stop, lightTracker::stop,
            mediaTracker::stop,
        ).forEach { stop ->
            try { stop() } catch (e: Exception) {
                Log.w(TAG, "Tracker cleanup failed (${e.javaClass.simpleName})")
            }
        }
        clearMediaEvents()
        try {
            geofenceTracker.stop(removeSystemGeofences = teardownGeofences)
        } catch (e: Exception) {
            Log.w(TAG, "Geofence tracker cleanup failed (${e.javaClass.simpleName})")
        }
    }


    private fun reconcileListeners(rules: List<com.flowpilot.app.data.model.Automation>) {
        val demand = TriggerListenerDemand.from(rules)
        if (demand.needsBattery) batteryTracker.start() else batteryTracker.stop()
        if (demand.needsCharger) chargerTracker.start() else chargerTracker.stop()
        if (demand.needsScreen) screenTracker.start() else screenTracker.stop()
        if (demand.needsWifi) wifiTracker.start() else wifiTracker.stop()
        if (demand.needs(TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED)) bluetoothTracker.start() else bluetoothTracker.stop()
        if (demand.needs(TriggerEvent.CALL_RINGING, TriggerEvent.CALL_ANSWERED, TriggerEvent.CALL_OUTGOING, TriggerEvent.CALL_ENDED)) callTracker.start() else callTracker.stop()
        if (demand.needsMedia) {
            if (!mediaTracker.start()) clearMediaEvents()
        } else {
            mediaTracker.stop()
            clearMediaEvents()
        }
        tracker.updatePackages(demand.appPackages)
        if (demand.needs(TriggerEvent.NOTIFICATION_RECEIVED)) FlowPilotNotificationListener.ensureBound(appContext)
    }
    private fun getLiveSystemState(): LiveSystemState {
        val batteryPercent = batteryTracker.currentLevel
        val isChargerConnected = batteryTracker.isChargerConnected

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
        val rules = repository.automations.first()
        if (rules.none { it.enabled && it.hasTrigger(TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED) }) {
            tracker.reset()
            state = ForegroundState()
            return
        }
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

        stepOutput.closedPackage?.let { pkg ->
            val result = RuleEvaluator.evaluate(rules, AppEvent.CLOSED, pkg, heldOpenLock = false, liveState = liveState)
            if (result.toExecute.isNotEmpty()) {
                Log.i(TAG, "Executing CLOSED rules for $pkg (${result.toExecute.size} rule(s))")
                val timestamp = domainTransitions.lastOrNull { it.packageName == pkg }?.timestamp
                executeAll(
                    result.toExecute,
                    liveState = liveState,
                    eventContext = EventTemplateContext.app(
                        trigger = TriggerEvent.APP_CLOSED,
                        packageName = pkg,
                        label = null,
                        liveState = liveState,
                        timestampMs = timestamp ?: System.currentTimeMillis(),
                    ),
                    eventId = timestamp?.toString(),
                )
            }
        }

        stepOutput.openedPackage?.let { pkg ->
            val result = RuleEvaluator.evaluate(rules, AppEvent.OPENED, pkg, heldOpenLock = false, liveState = liveState)
            if (result.toExecute.isNotEmpty()) {
                Log.i(TAG, "Executing OPENED rules for $pkg (${result.toExecute.size} rule(s))")
                val timestamp = domainTransitions.lastOrNull { it.packageName == pkg }?.timestamp
                executeAll(
                    result.toExecute,
                    liveState = liveState,
                    eventContext = EventTemplateContext.app(
                        trigger = TriggerEvent.APP_OPENED,
                        packageName = pkg,
                        label = null,
                        liveState = liveState,
                        timestampMs = timestamp ?: System.currentTimeMillis(),
                    ),
                    eventId = timestamp?.toString(),
                )
            }
        }
    }

    private suspend fun pollSchedules(liveState: LiveSystemState) {
        if (!TriggerListenerDemand.from(repository.automations.first()).needs(TriggerEvent.TIME_SCHEDULE)) {
            scheduleWindow.advance(LocalDateTime.now())
            return
        }
        for (minute in scheduleWindow.advance(LocalDateTime.now())) {
            currentCoroutineContext().ensureActive()
            val matches = ScheduleEvaluator.matchingRules(repository.automations.first(), minute, liveState)
            if (matches.isNotEmpty()) {
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.live(liveState),
                    eventId = minute.toString(),
                )
            }
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
                val effectiveState = liveState.copy(isChargerConnected = (event == ChargerEvent.CONNECTED))
                executeAll(
                    matches,
                    liveState = effectiveState,
                    eventContext = EventTemplateContext.charger(event, effectiveState),
                )
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
                executeAll(
                    matches,
                    liveState = effectiveState,
                    eventContext = EventTemplateContext.live(effectiveState),
                )
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
                val effectiveState = liveState.copy(isScreenOn = (event != ScreenEvent.OFF))
                executeAll(
                    matches,
                    liveState = effectiveState,
                    eventContext = EventTemplateContext.screen(event, effectiveState),
                )
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
                val effectiveState = liveState.copy(
                    connectedWifiSsid = if (transition.event == WifiStateEvent.CONNECTED) transition.ssid else null
                )
                executeAll(
                    matches,
                    liveState = effectiveState,
                    eventContext = EventTemplateContext.wifi(transition, effectiveState),
                )
            }
        }
    }

    private suspend fun pollNotificationEvents(liveState: LiveSystemState) {
        val events = FlowPilotNotificationListener.drainEvents()
        if (events.isEmpty()) return
        val authorization = AutomationService.authorizeEventExecution(appContext) ?: return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateNotification(rules, event, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing notification rules for ${event.packageName} (${matches.size} rule(s))")
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.notification(event, liveState),
                    eventAuthorization = authorization,
                    eventId = "${event.key}:${event.postTime}",
                )
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
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.nfc(event, liveState),
                    eventId = "${event.tagId}:${event.timestamp / 1000}",
                )
            }
        }
    }

    private fun updateFlipListeningPolicy(
        rules: List<com.flowpilot.app.data.model.Automation>,
        liveState: LiveSystemState,
    ) {
        val flipRules = rules.triggerViews().filter {
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
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.live(liveState),
                )
            }
        }
    }

    private fun updateShakeListeningPolicy(
        rules: List<com.flowpilot.app.data.model.Automation>,
        liveState: LiveSystemState,
    ) {
        val shakeThresholds = rules.triggerViews()
            .filter { it.enabled && it.triggerEvent == TriggerEvent.DEVICE_SHAKE }
            .map { it.shakeThreshold }
        val minThreshold = ShakeReducer.minimumThreshold(shakeThresholds)
        if (minThreshold != null) {
            // Every rule keeps its own force; the sensor only needs to catch the softest one.
            shakeTracker.updateThreshold(minThreshold)
        }
        shakeTracker.updateListeningPolicy(
            hasActiveRules = minThreshold != null,
            isScreenOn = liveState.isScreenOn != false,
        )
    }

    private fun updateLightListeningPolicy(
        rules: List<com.flowpilot.app.data.model.Automation>,
        liveState: LiveSystemState,
    ) {
        val hasLight = rules.triggerViews().any {
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
        for (event in events) {
            val matches = RuleEvaluator.evaluateShake(
                rules = rules,
                liveState = liveState,
                nowMs = event.timestampMs,
                shakeMagnitude = event.magnitude,
            )
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing shake rules (${matches.size} rule(s))")
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.shake(event, liveState),
                    eventId = event.timestampMs.toString(),
                )
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
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.light(transition, TriggerEvent.LIGHT_BELOW, liveState),
                )
            }
        }
    }

    private suspend fun pollBluetoothTransitions(liveState: LiveSystemState) {
        // Permission may be granted while service stays alive; begin listening without restarting engine.
        // Listener lifecycle is reconciled from enabled trigger demand.
        val transitions = bluetoothTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateBluetooth(rules, transition, liveState)
            val trigger = when (transition.event) {
                BluetoothDeviceEvent.CONNECTED -> TriggerEvent.BLUETOOTH_CONNECTED
                BluetoothDeviceEvent.DISCONNECTED -> TriggerEvent.BLUETOOTH_DISCONNECTED
            }
            val unmatchedRules = rules.count { it.enabled && it.hasTrigger(trigger) } - matches.size
            Log.i(BLUETOOTH_TAG, "Unmatched transition rules=$unmatchedRules")
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing $trigger Bluetooth rules (${matches.size} rule(s))")
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.bluetooth(transition, liveState),
                )
            }
        }
    }

    private suspend fun pollCallTransitions(liveState: LiveSystemState) {
        // Listener lifecycle is reconciled from enabled trigger demand.
        val transitions = callTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateCall(rules, transition, liveState)
            if (matches.isNotEmpty()) {
                Log.i(TAG, "Executing ${transition.triggerEvent.name} call rules (${matches.size} rule(s))")
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.call(transition, liveState),
                )
            }
        }
    }

    private fun clearMediaEvents() {
        while (mediaEvents.tryReceive().isSuccess) Unit
    }

    private suspend fun pollMediaEvents(liveState: LiveSystemState) {
        while (true) {
            val event = mediaEvents.tryReceive().getOrNull() ?: return
            val authorization = AutomationService.authorizeEventExecution(appContext) ?: return
            val rules = repository.automations.first()
            val matches = RuleEvaluator.evaluateMedia(rules, event, liveState)
            if (matches.isNotEmpty()) {
                executeAll(
                    matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.media(event, liveState),
                    eventAuthorization = authorization,
                    eventId = (++mediaEventSequence).toString(),
                )
            }
        }
    }

    private suspend fun pollSmsEvents(liveState: LiveSystemState) {
        val events = SmsEventTracker.drainEvents()
        if (events.isEmpty()) return
        val authorization = AutomationService.authorizeEventExecution(appContext) ?: return
        val rules = repository.automations.first()
        for (event in events) {
            val matches = RuleEvaluator.evaluateSms(rules, event, liveState)
            if (matches.isNotEmpty()) {
                val masked = PhoneNumberUtils.mask(event.sender)
                Log.i(TAG, "Executing SMS received rules for $masked (${matches.size} rule(s))")
                executeAll(
                    rules = matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.sms(event, liveState),
                    eventId = event.timestamp.toString(),
                    eventAuthorization = authorization,
                )
            }
        }
    }

    private suspend fun pollGeofenceEvents(liveState: LiveSystemState) {
        val transitions = geofenceTracker.drainTransitions()
        if (transitions.isEmpty()) return
        val rules = repository.automations.first()
        val matchedCallbacks = mutableSetOf<Triple<String, GeofenceEvent, Long>>()
        for (transition in transitions) {
            val matches = RuleEvaluator.evaluateGeofence(rules, transition, liveState).filter {
                matchedCallbacks.add(Triple(it.id, transition.event, transition.timestamp))
            }
            if (matches.isNotEmpty()) {
                val trigger = when (transition.event) {
                    GeofenceEvent.ENTER -> TriggerEvent.GEOFENCE_ENTER
                    GeofenceEvent.EXIT -> TriggerEvent.GEOFENCE_EXIT
                }
                Log.i(TAG, "Executing geofence rules for $trigger (${matches.size} rule(s))")
                executeAll(
                    rules = matches,
                    liveState = liveState,
                    eventContext = EventTemplateContext.geofence(
                        transition = transition,
                        geofenceName = matches.first().geofenceName,
                        liveState = liveState,
                    ),
                    geofenceTransition = transition,
                    eventId = "${transition.automationId}:${transition.event}:${transition.timestamp}",
                )
            }
        }
    }

    private suspend fun executeAll(
        rules: List<com.flowpilot.app.data.model.Automation>,
        liveState: LiveSystemState = LiveSystemState(),
        eventContext: TemplateContext = TemplateContext(),
        geofenceTransition: GeofenceTransition? = null,
        eventAuthorization: EventExecutionAuthorization.Token? = null,
        eventId: String? = null,
    ) {
        for (candidate in rules.distinctBy { it.id }) {
            if (!triggerDeduplicator.accept(candidate.id, candidate.executionRevision, candidate.triggerEvent, candidate.matchedTriggerIndex, eventId)) continue
            if (!reservePendingExecution(candidate.id)) continue
            try {
                val reservation = repository.reserveExecution(
                    id = candidate.id,
                    expectedRevision = candidate.executionRevision,
                ) ?: continue
                val rule = reservation.rule
                var anySuccess = false
                var cancelled = false
                var actionRecords = mutableListOf<ActionExecutionRecord>()
                try {
                    withContext(Dispatchers.IO) {
                        val templateContext = EventTemplateContext.forRule(eventContext, candidate)
                        actionRecords = mutableListOf()
                        val actions = rule.effectiveActions
                        val delays = rule.effectiveActionDelays
                        var currentAction: com.flowpilot.app.data.model.ActionType? = null

                        try {
                            for (i in actions.indices) {
                                currentCoroutineContext().ensureActive()
                                val action = actions[i]
                                currentAction = action
                                val delaySec = delays.getOrElse(i) { 0 }

                                if (delaySec > 0) {
                                    if (!repository.renewExecutionReservation(reservation)) {
                                        actionRecords.add(
                                            ActionExecutionRecord.create(
                                                actionType = action,
                                                success = false,
                                                message = "Execution revoked before dispatch",
                                                resultCode = com.flowpilot.app.actions.ActionResultCode.EXECUTION_CANCELLED,
                                            )
                                        )
                                        currentAction = null
                                        break
                                    }
                                    delay(delaySec * 1000L)
                                }

                                currentCoroutineContext().ensureActive()
                                val result = try {
                                    repository.dispatchIfAuthorized(reservation) {
                                        val needsLocation = rule.requiresLocation(action)
                                        val coords = resolveExecutionCoordinates(
                                            requiresLocation = needsLocation,
                                            event = geofenceTransition,
                                            freshLocationProvider = {
                                                LocationFetcher.getCoordinates(appContext, isBackgroundExecution = true)
                                            },
                                            eventLocationAllowed = needsLocation && LocationFetcher.isLocationAvailable(appContext, isBackgroundExecution = true),
                                        )
                                        val actionContext = if (coords != null) {
                                            templateContext.copy(locationLat = coords.first, locationLng = coords.second)
                                        } else {
                                            templateContext
                                        }
                                        val parameters = ActionParametersFactory.create(rule, actionContext)
                                        eventAuthorization?.let { token ->
                                            AutomationService.executeIfEventAuthorized(token) {
                                                dispatcher.executeAsync(action, parameters)
                                            }
                                        } ?: dispatcher.executeAsync(action, parameters)
                                    }
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (_: Throwable) {
                                    com.flowpilot.app.actions.ActionResult(
                                        success = false,
                                        message = "Execution failed",
                                    )
                                }
                                if (result == null) {
                                    actionRecords.add(
                                        ActionExecutionRecord.create(
                                            actionType = action,
                                            success = false,
                                            message = "Execution revoked before dispatch",
                                            resultCode = com.flowpilot.app.actions.ActionResultCode.EXECUTION_CANCELLED,
                                        )
                                    )
                                    currentAction = null
                                    break
                                }
                                Log.i(TAG, "Rule action result: action=${action.name}, success=${result.success}")
                                anySuccess = anySuccess || result.success
                                actionRecords.add(
                                    ActionExecutionRecord.create(action, result)
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
                                        resultCode = com.flowpilot.app.actions.ActionResultCode.EXECUTION_CANCELLED,
                                    )
                                )
                            }
                            throw ce
                        }
                    }
                } catch (ce: CancellationException) {
                    cancelled = true
                    throw ce
                } finally {
                    withContext(NonCancellable) {
                        repository.appendHistory(
                            ExecutionHistoryEntry.create(
                                ruleId = rule.id,
                                ruleName = rule.normalizedName,
                                trigger = candidate.triggerEvent.name,
                                triggerSourceIndex = candidate.matchedTriggerIndex + 1,
                                timestamp = System.currentTimeMillis(),
                                actions = actionRecords.ifEmpty {
                                    listOf(
                                        ActionExecutionRecord.create(
                                            actionType = rule.effectiveActions.first(),
                                            success = false,
                                            message = if (cancelled) "Execution cancelled" else "Execution failed before dispatch",
                                            resultCode = if (cancelled) {
                                                com.flowpilot.app.actions.ActionResultCode.EXECUTION_CANCELLED
                                            } else null,
                                        )
                                    )
                                },
                            )
                        )
                    }
                    if (!anySuccess) {
                        withContext(NonCancellable) {
                            repository.releaseExecutionReservation(reservation, successful = false)
                        }
                    } else {
                        withContext(NonCancellable) {
                            repository.releaseExecutionReservation(reservation, successful = true)
                        }
                    }
                }
            } finally {
                releasePendingExecution(candidate.id)
            }
        }
    }

    private suspend fun reservePendingExecution(id: String): Boolean = pendingExecutionMutex.withLock {
        pendingRuleIds.add(id)
    }

    private suspend fun releasePendingExecution(id: String) {
        pendingExecutionMutex.withLock { pendingRuleIds.remove(id) }
    }

    private companion object {
        val engineLifetime = Mutex()
        val pendingExecutionMutex = Mutex()
        val pendingRuleIds = mutableSetOf<String>()
        const val TAG = "FlowPilotEngine"
        const val BLUETOOTH_TAG = "FlowPilotBluetooth"
        const val POLL_INTERVAL_MS = 500L
    }
}
