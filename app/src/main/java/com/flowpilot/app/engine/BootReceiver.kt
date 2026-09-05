package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flowpilot.app.data.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts the automation engine after device reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (!AutomationService.reconcileEnabled(context.applicationContext)) {
                        Log.w("BootReceiver", "AutomationService startup blocked; failure saved in automation_service_status")
                    }
                } catch (t: Throwable) {
                    AutomationService.reportStartupFailure(context.applicationContext)
                    Log.e("BootReceiver", "Failed to start AutomationService on boot: ${t.message}", t)
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}
