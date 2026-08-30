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

    val automations: StateFlow<List<AutomationUI>> = MutableStateFlow(emptyList())

    val hasUsageAccess: StateFlow<Boolean> = MutableStateFlow(false)
    val hasWriteSecureSettings: StateFlow<Boolean> = MutableStateFlow(false)
    val shizukuState: StateFlow<ShizukuState> = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val engineRunning: StateFlow<Boolean> = MutableStateFlow(false)

    init {
        observeRules()
        refreshPermissions()
    }

    private val app get() = getApplication<Application>()

    /** Notification permission (Android 13+). */
    val hasNotifications: StateFlow<Boolean> = MutableStateFlow(
        if (Build.VERSION.SDK_INT >= 33) {
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    )

    private fun observeRules() {
        viewModelScope.launch {
            repository.automations.collect { rules -> remapRules(rules) }
        }
    }

    private fun remapRules(rules: List<Automation>) {
        (automations as MutableStateFlow).value = rules.sortedByDescending { it.createdAt }
            .map { AutomationUI(it, capabilities.statusFor(it.action)) }
    }

    fun refreshPermissions() {
        val c = capabilities
        (hasUsageAccess as MutableStateFlow).value = c.hasUsageAccess()
        (hasWriteSecureSettings as MutableStateFlow).value = c.hasWriteSecureSettings()
        (shizukuState as MutableStateFlow).value = c.shizukuState()
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
        viewModelScope.launch { repository.add(name ?: "", triggerEvent, appPackage, appName, action) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) ; refreshPermissions() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun startEngine() {
        AutomationService.start(getApplication())
        (engineRunning as MutableStateFlow).value = true
    }

    fun stopEngine() {
        AutomationService.stop(getApplication())
        (engineRunning as MutableStateFlow).value = false
    }

    fun updateEngineRunning(value: Boolean) {
        (engineRunning as MutableStateFlow).value = value
    }
}
