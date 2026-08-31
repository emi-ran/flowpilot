package com.flowpilot.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.engine.AutomationService
import com.flowpilot.app.permission.CapabilityManager
import com.flowpilot.app.permission.CapabilityStatus
import com.flowpilot.app.permission.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AutomationUI(val rule: Automation, val capability: CapabilityStatus)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AutomationRepository(app)
    private val capabilities = CapabilityManager(app)

    val automations = MutableStateFlow<List<AutomationUI>>(emptyList())
    val hasUsageAccess = MutableStateFlow(false)
    val hasWriteSecureSettings = MutableStateFlow(false)
    val hasNotifications = MutableStateFlow(false)
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
                val statuses = rule.effectiveActions.map { capabilities.statusFor(it) }
                val aggregate = when {
                    statuses.any { it == CapabilityStatus.UNSUPPORTED } -> CapabilityStatus.UNSUPPORTED
                    statuses.any { it == CapabilityStatus.SHIZUKU_REQUIRED } -> CapabilityStatus.SHIZUKU_REQUIRED
                    statuses.any { it == CapabilityStatus.PERMISSION_REQUIRED } -> CapabilityStatus.PERMISSION_REQUIRED
                    else -> CapabilityStatus.AVAILABLE
                }
                AutomationUI(rule, aggregate)
            }
    }

    fun refreshPermissions() {
        val c = capabilities
        hasUsageAccess.value = c.hasUsageAccess()
        hasWriteSecureSettings.value = c.hasWriteSecureSettings()
        shizukuState.value = c.shizukuState()
        hasNotifications.value = if (Build.VERSION.SDK_INT >= 33) {
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
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
        scheduledMinute: Int = 0,
        scheduledDays: Set<Int> = emptySet(),
        batteryLevel: Int = 50,
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
    ) {
        viewModelScope.launch {
            repository.add(name ?: "", triggerEvent, appPackage, appName, actions, scheduledMinute, scheduledDays, batteryLevel, notificationTitle, notificationBody, vibrationPattern, vibrationDurationMs, vibrationAmplitude, mediaVolumePercent, soundPreset, soundUri, soundName, soundDurationMs, launchPackage, launchAppName, url)
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
}
