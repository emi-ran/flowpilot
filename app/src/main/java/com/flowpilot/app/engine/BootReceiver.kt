package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowpilot.app.data.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts the automation engine after device reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (AutomationRepository(context.applicationContext).isEngineEnabled.first()) {
                        AutomationService.start(context.applicationContext)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
