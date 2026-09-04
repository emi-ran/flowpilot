package com.flowpilot.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowPilotWidgetToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FlowPilotWidgetProvider.ACTION_TOGGLE_ENGINE) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FlowPilotWidgetProvider().handleToggleEngine(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
