package com.flowpilot.app

import android.app.Application
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.ui.util.applyAppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowPilotApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ShizukuShell.instance.init(this)
        val initialLanguage = AutomationRepository.getPersistedLanguage(this)
        applyAppLocale(this, initialLanguage)
        applicationScope.launch {
            AutomationRepository(this@FlowPilotApp).migrateLegacySecretsIfNeeded()
            AutomationRepository(this@FlowPilotApp).syncPersistedLanguage()
        }
    }
}
