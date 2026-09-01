package com.flowpilot.app

import android.app.Application
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.data.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowPilotApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ShizukuShell.instance.init(this)
        applicationScope.launch {
            AutomationRepository(this@FlowPilotApp).migrateLegacySecretsIfNeeded()
        }
    }
}
