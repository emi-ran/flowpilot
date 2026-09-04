package com.flowpilot.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.flowpilot.app.MainActivity
import com.flowpilot.app.R
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.engine.AutomationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FlowPilotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repository = AutomationRepository(context)
                val isEngineEnabled = repository.isEngineEnabled.first()
                val rules = repository.automations.first()
                val activeCount = rules.count { it.enabled }
                val totalCount = rules.size

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_flowpilot_control)

                    // Open app when tapping root or open button
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_btn_open, openAppPendingIntent)

                    // Toggle engine button
                    val toggleIntent = Intent(context, FlowPilotWidgetToggleReceiver::class.java).apply {
                        action = ACTION_TOGGLE_ENGINE
                    }
                    val togglePendingIntent = PendingIntent.getBroadcast(
                        context,
                        1,
                        toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_toggle, togglePendingIntent)

                    // Update UI state
                    if (isEngineEnabled) {
                        views.setImageViewResource(R.id.widget_status_dot, R.drawable.bg_widget_circle_active)
                        val statusText = if (totalCount > 0) {
                            context.getString(R.string.widget_active_count, activeCount, totalCount)
                        } else {
                            context.getString(R.string.widget_engine_active)
                        }
                        views.setTextViewText(R.id.widget_status_text, statusText)
                        views.setImageViewResource(R.id.widget_btn_toggle, R.drawable.ic_widget_pause)
                    } else {
                        views.setImageViewResource(R.id.widget_status_dot, R.drawable.bg_widget_circle_paused)
                        views.setTextViewText(R.id.widget_status_text, context.getString(R.string.widget_engine_paused))
                        views.setImageViewResource(R.id.widget_btn_toggle, R.drawable.ic_widget_play)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    suspend fun handleToggleEngine(context: Context) {
        val repository = AutomationRepository(context)
        val isEnabled = repository.isEngineEnabled.first()
        val newEnabled = !isEnabled
        repository.setEngineEnabled(newEnabled)

        if (newEnabled) {
            AutomationService.start(context)
        } else {
            AutomationService.stop(context)
        }

        updateAllWidgets(context)
    }

    companion object {
        const val ACTION_TOGGLE_ENGINE = "com.flowpilot.app.widget.ACTION_TOGGLE_ENGINE"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, FlowPilotWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, FlowPilotWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (_: Throwable) {}
        }
    }
}
