package com.flowpilot.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.engine.AutomationService
import com.flowpilot.app.permission.CapabilityManager
import com.flowpilot.app.permission.CapabilityStatus
import com.flowpilot.app.permission.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AutomationUI(val rule: Automation, val capability: CapabilityStatus)

data class ManualRunResult(
    val totalActions: Int,
    val successCount: Int,
    val failureCount: Int,
    val failureMessages: List<String>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AutomationRepository(app)
    private val capabilities = CapabilityManager(app)

    val automations = MutableStateFlow<List<AutomationUI>>(emptyList())
    val executionHistory: StateFlow<List<ExecutionHistoryEntry>> = repository.executionHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hasUsageAccess = MutableStateFlow(false)
    val hasWriteSettings = MutableStateFlow(false)
    val hasWriteSecureSettings = MutableStateFlow(false)
    val hasNotifications = MutableStateFlow(false)
    val hasNotificationPolicy = MutableStateFlow(false)
    val hasNotificationListener = MutableStateFlow(false)
    val hasWifiPermissions = MutableStateFlow(false)
    val hasBluetoothConnectPermission = MutableStateFlow(false)
    val hasReadPhoneStatePermission = MutableStateFlow(false)
    val hasCallPhonePermission = MutableStateFlow(false)
    val hasNfcHardware = MutableStateFlow(false)
    val isNfcEnabled = MutableStateFlow(false)
    val ignoresBatteryOptimizations = MutableStateFlow(false)
    val shizukuState = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val engineRunning = MutableStateFlow(false)

    private val app get() = getApplication<Application>()

    init {
        observeRules()
        observeEngineState()
        refreshPermissions()
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky {
                refreshPermissions()
            }
        } catch (_: Throwable) {}
    }

    private fun observeRules() {
        viewModelScope.launch {
            repository.automations.collect { rules -> remapRules(rules) }
        }
    }

    private fun observeEngineState() {
        viewModelScope.launch {
            repository.isEngineEnabled.collect { enabled ->
                (engineRunning as MutableStateFlow).value = enabled
                if (enabled) {
                    AutomationService.start(app)
                }
            }
        }
    }

    private fun remapRules(rules: List<Automation>) {
        automations.value = rules.sortedByDescending { it.createdAt }
            .map { rule ->
                val actionStatuses = rule.effectiveActions.map { capabilities.statusFor(it) }

                // Check trigger/condition prerequisites for Wi-Fi, Bluetooth, NFC, Notifications, Phone Calls
                val hasWifiTriggerOrCond = rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.WIFI_CONNECTED ||
                    rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.WIFI_DISCONNECTED ||
                    rule.conditions.any { it.type == com.flowpilot.app.data.model.ConditionType.WIFI_CONNECTED || it.type == com.flowpilot.app.data.model.ConditionType.WIFI_DISCONNECTED }

                val hasNotificationTrigger = rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.NOTIFICATION_RECEIVED
                val hasBluetoothTrigger = rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.BLUETOOTH_CONNECTED ||
                    rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.BLUETOOTH_DISCONNECTED
                val hasNfcTagTrigger = rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.NFC_TAG_SCANNED
                val hasCallTrigger = rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.CALL_RINGING ||
                    rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.CALL_ANSWERED ||
                    rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.CALL_OUTGOING ||
                    rule.triggerEvent == com.flowpilot.app.data.model.TriggerEvent.CALL_ENDED

                val triggerStatus = when {
                    hasWifiTriggerOrCond && !capabilities.hasWifiPermissions() -> CapabilityStatus.PERMISSION_REQUIRED
                    hasBluetoothTrigger && !capabilities.hasBluetoothAdapter() -> CapabilityStatus.UNSUPPORTED
                    hasBluetoothTrigger && !capabilities.hasBluetoothConnectPermission() -> CapabilityStatus.PERMISSION_REQUIRED
                    hasNotificationTrigger && !capabilities.hasNotificationListenerAccess() -> CapabilityStatus.PERMISSION_REQUIRED
                    hasNfcTagTrigger && !capabilities.hasNfcHardware() -> CapabilityStatus.UNSUPPORTED
                    hasNfcTagTrigger && !capabilities.isNfcEnabled() -> CapabilityStatus.PERMISSION_REQUIRED
                    hasCallTrigger && !capabilities.hasReadPhoneStatePermission() -> CapabilityStatus.PERMISSION_REQUIRED
                    else -> CapabilityStatus.AVAILABLE
                }

                val allStatuses = actionStatuses + triggerStatus

                val aggregate = when {
                    allStatuses.any { it == CapabilityStatus.UNSUPPORTED } -> CapabilityStatus.UNSUPPORTED
                    allStatuses.any { it == CapabilityStatus.SHIZUKU_REQUIRED } -> CapabilityStatus.SHIZUKU_REQUIRED
                    allStatuses.any { it == CapabilityStatus.PERMISSION_REQUIRED } -> CapabilityStatus.PERMISSION_REQUIRED
                    else -> CapabilityStatus.AVAILABLE
                }
                AutomationUI(rule, aggregate)
            }
    }

    fun refreshPermissions() {
        val c = capabilities
        hasUsageAccess.value = c.hasUsageAccess()
        hasWriteSettings.value = c.hasWriteSettings()
        hasWriteSecureSettings.value = c.hasWriteSecureSettings()
        shizukuState.value = c.shizukuState()
        hasNotifications.value = if (Build.VERSION.SDK_INT >= 33) {
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        hasNotificationPolicy.value = c.hasNotificationPolicyAccess()
        hasNotificationListener.value = c.hasNotificationListenerAccess()
        hasWifiPermissions.value = c.hasWifiPermissions()
        hasBluetoothConnectPermission.value = c.hasBluetoothConnectPermission()
        hasReadPhoneStatePermission.value = c.hasReadPhoneStatePermission()
        hasCallPhonePermission.value = c.hasCallPhonePermission()
        hasNfcHardware.value = c.hasNfcHardware()
        isNfcEnabled.value = c.isNfcEnabled()
        ignoresBatteryOptimizations.value = c.isIgnoringBatteryOptimizations()

        if (engineRunning.value) {
            try {
                AutomationService.start(app)
            } catch (_: Throwable) {}
        }

        // Re-evaluate capability pills on every rule card immediately.
        viewModelScope.launch { remapRules(repository.automations.first()) }
    }

    /** Show the Shizuku grant dialog (requires Shizuku running). */
    fun requestShizukuPermission() {
        ShizukuShell.instance.requestPermission()
    }

    /** When Shizuku is ready and WRITE_SECURE_SETTINGS is missing, grant it through Shizuku. */
    fun grantSecureSettingsViaShizuku(done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = ShizukuShell.instance.run("pm grant ${app.packageName} android.permission.WRITE_SECURE_SETTINGS").first == 0
            if (ok) refreshPermissions()
            done(ok)
        }
    }

    fun addRule(
        name: String?,
        triggerEvent: com.flowpilot.app.data.model.TriggerEvent,
        appPackage: String,
        appName: String,
        actions: List<com.flowpilot.app.data.model.ActionType>,
        actionDelays: List<Int> = emptyList(),
        cooldownMinutes: Int = 0,
        scheduledMinute: Int = 0,
        scheduledDays: Set<Int> = emptySet(),
        batteryLevel: Int = 50,
        wifiSsid: String = "",
        bluetoothDeviceAddress: String = "",
        bluetoothDeviceName: String = "",
        nfcTagId: String = "",
        notificationAppPackage: String = "",
        notificationAppName: String = "",
        notificationKeyword: String = "",
        conditions: List<com.flowpilot.app.data.model.RuleCondition> = emptyList(),
        notificationTitle: String = "FlowPilot",
        notificationBody: String = "Automation ran",
        vibrationPattern: com.flowpilot.app.data.model.VibrationPattern = com.flowpilot.app.data.model.VibrationPattern.PULSE,
        vibrationDurationMs: Int = 220,
        vibrationAmplitude: Int = 180,
        mediaVolumePercent: Int = 50,
        soundPreset: com.flowpilot.app.data.model.SoundPreset = com.flowpilot.app.data.model.SoundPreset.NOTIFICATION,
        soundUri: String = "",
        soundName: String = "",
        soundDurationMs: Int = 3_000,
        launchPackage: String = "",
        launchAppName: String = "",
        url: String = "",
        ttsText: String = "",
        ttsVoiceName: String = "",
        ttsSpeechRate: Float = 1.0f,
        ttsAudioFileName: String = "",
        alarmHour: Int = 7,
        alarmMinute: Int = 0,
        alarmMessage: String = "",
        timerDurationSeconds: Int = 300,
        timerMessage: String = "",
        webhookMethod: String = "POST",
        webhookUrl: String = "",
        webhookHeaders: String = "",
        webhookBody: String = "",
        webhookTimeoutSeconds: Int = 10,
        phoneNumber: String = "",
        ruleId: String = UUID.randomUUID().toString(),
    ) {
        viewModelScope.launch {
            repository.add(
                name = name ?: "",
                triggerEvent = triggerEvent,
                appPackage = appPackage,
                appName = appName,
                actions = actions,
                actionDelays = actionDelays,
                cooldownMinutes = cooldownMinutes,
                scheduledMinute = scheduledMinute,
                scheduledDays = scheduledDays,
                batteryLevel = batteryLevel,
                wifiSsid = wifiSsid,
                bluetoothDeviceAddress = bluetoothDeviceAddress,
                bluetoothDeviceName = bluetoothDeviceName,
                nfcTagId = nfcTagId,
                notificationAppPackage = notificationAppPackage,
                notificationAppName = notificationAppName,
                notificationKeyword = notificationKeyword,
                conditions = conditions,
                notificationTitle = notificationTitle,
                notificationBody = notificationBody,
                vibrationPattern = vibrationPattern,
                vibrationDurationMs = vibrationDurationMs,
                vibrationAmplitude = vibrationAmplitude,
                mediaVolumePercent = mediaVolumePercent,
                soundPreset = soundPreset,
                soundUri = soundUri,
                soundName = soundName,
                soundDurationMs = soundDurationMs,
                launchPackage = launchPackage,
                launchAppName = launchAppName,
                url = url,
                ttsText = ttsText,
                ttsVoiceName = ttsVoiceName,
                ttsSpeechRate = ttsSpeechRate,
                ttsAudioFileName = ttsAudioFileName,
                alarmHour = alarmHour,
                alarmMinute = alarmMinute,
                alarmMessage = alarmMessage,
                timerDurationSeconds = timerDurationSeconds,
                timerMessage = timerMessage,
                webhookMethod = webhookMethod,
                webhookUrl = webhookUrl,
                webhookHeaders = webhookHeaders,
                webhookBody = webhookBody,
                webhookTimeoutSeconds = webhookTimeoutSeconds,
                phoneNumber = phoneNumber,
                id = ruleId,
            )
            startEngine()
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
            refreshPermissions()
            if (enabled) startEngine()
        }
    }

    fun updateRule(rule: Automation) {
        viewModelScope.launch {
            repository.update(rule)
            refreshPermissions()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun deleteMany(ids: Set<String>) {
        viewModelScope.launch { repository.deleteMany(ids) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun startEngine() {
        viewModelScope.launch {
            repository.setEngineEnabled(true)
        }
        AutomationService.start(getApplication())
        engineRunning.value = true
    }

    fun stopEngine() {
        viewModelScope.launch {
            repository.setEngineEnabled(false)
        }
        AutomationService.stop(getApplication())
        (engineRunning as MutableStateFlow).value = false
    }

    fun updateEngineRunning(value: Boolean) {
        (engineRunning as MutableStateFlow).value = value
    }

    /**
     * Executes only the provided rule's actions manually.
     * Bypasses triggers and conditions, does not alter lastTriggeredAt or enabled state.
     * Runs off the main dispatcher and invokes callback with overall result.
     */
    fun runRuleNow(rule: Automation, callback: (ManualRunResult) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val liveState = getLiveSystemState()
            val templateContext = com.flowpilot.app.actions.WebhookTemplateContext(
                trigger = "MANUAL",
                timestamp = System.currentTimeMillis(),
                batteryPercent = liveState.batteryPercent,
                isCharging = liveState.isChargerConnected,
                wifiSsid = liveState.connectedWifiSsid,
            )
            val actionParams = com.flowpilot.app.actions.ActionParameters(
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
            )
            val dispatcher = com.flowpilot.app.actions.ActionDispatcher.get(app)
            var successCount = 0
            var failureCount = 0
            val failureMessages = mutableListOf<String>()
            val actionRecords = mutableListOf<ActionExecutionRecord>()

            val actions = rule.effectiveActions
            val delays = rule.effectiveActionDelays

            var cancellation: CancellationException? = null
            try {
                for (i in actions.indices) {
                    val action = actions[i]
                    val delaySec = delays.getOrElse(i) { 0 }

                    if (delaySec > 0) {
                        try {
                            kotlinx.coroutines.delay(delaySec * 1000L)
                        } catch (ce: CancellationException) {
                            failureCount++
                            val msg = "Execution cancelled during ${delaySec}s delay"
                            failureMessages.add("${action.label}: $msg")
                            actionRecords.add(
                                ActionExecutionRecord.create(
                                    actionType = action,
                                    success = false,
                                    message = msg,
                                )
                            )
                            throw ce
                        }
                    }

                    val result = dispatcher.execute(action, actionParams)
                    if (result.success) {
                        successCount++
                    } else {
                        failureCount++
                        val redacted = com.flowpilot.app.actions.WebhookExecutor.redactSensitiveText(result.message)
                        failureMessages.add("${action.label}: $redacted")
                    }
                    actionRecords.add(
                        ActionExecutionRecord.create(
                            actionType = action,
                            success = result.success,
                            message = result.message,
                        )
                    )
                }
            } catch (ce: CancellationException) {
                cancellation = ce
            } finally {
                val historyEntry = ExecutionHistoryEntry.create(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    trigger = "MANUAL",
                    timestamp = System.currentTimeMillis(),
                    actions = actionRecords,
                )
                withContext(NonCancellable) {
                    repository.appendHistory(historyEntry)
                }
            }

            val summary = ManualRunResult(
                totalActions = rule.effectiveActions.size,
                successCount = successCount,
                failureCount = failureCount,
                failureMessages = failureMessages,
            )
            if (cancellation != null) {
                withContext(NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                    callback(summary)
                }
            } else {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    callback(summary)
                }
            }
            cancellation?.let { throw it }
        }
    }

    private fun getLiveSystemState(): com.flowpilot.app.engine.LiveSystemState {
        val batteryIntent = try {
            app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else null

        val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isChargerConnected = plugged != 0

        val pm = app.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenOn = pm?.isInteractive

        val wifiSsid = try {
            val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val wm = app.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            com.flowpilot.app.engine.WifiStateTracker.queryCurrentSsid(cm, wm)
        } catch (_: Throwable) {
            null
        }

        return com.flowpilot.app.engine.LiveSystemState(
            batteryPercent = batteryPercent,
            isChargerConnected = isChargerConnected,
            isScreenOn = isScreenOn,
            connectedWifiSsid = wifiSsid,
        )
    }
}
