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
                if (enabled && capabilities.hasUsageAccess()) {
                    AutomationService.start(app)
                }
            }
        }
    }

    private fun remapRules(rules: List<Automation>) {
        automations.value = rules.sortedByDescending { it.createdAt }
            .map { AutomationUI(it, capabilities.statusFor(it.action)) }
    }

    fun refreshPermissions() {
        val c = capabilities
        hasUsageAccess.value = c.hasUsageAccess()
        hasWriteSecureSettings.value = c.hasWriteSecureSettings()
        shizukuState.value = c.shizukuState()
        hasNotifications.value = if (Build.VERSION.SDK_INT >= 33) {
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (engineRunning.value && hasUsageAccess.value) {
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

    fun addRule(name: String?, triggerEvent: com.flowpilot.app.data.model.TriggerEvent,
                appPackage: String, appName: String, action: com.flowpilot.app.data.model.ActionType) {
        viewModelScope.launch {
            repository.add(name ?: "", triggerEvent, appPackage, appName, action)
            if (capabilities.hasUsageAccess()) {
                startEngine()
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
            refreshPermissions()
            if (enabled && capabilities.hasUsageAccess()) {
                startEngine()
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun startEngine() {
        viewModelScope.launch {
            repository.setEngineEnabled(true)
        }
        AutomationService.start(getApplication())
        (engineRunning as MutableStateFlow).value = true
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
