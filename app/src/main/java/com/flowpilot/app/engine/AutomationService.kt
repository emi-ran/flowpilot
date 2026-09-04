package com.flowpilot.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.flowpilot.app.MainActivity
import com.flowpilot.app.R

/**
 * Holds the automation engine in a foreground "special use" service so it survives
 * normal lifecycle changes and can keep polling the foreground app. The ongoing
 * notification explains why the service is running (required on Android 14+).
 */
class AutomationService : Service() {

    private val lifecycleLock = Any()
    private var engine: AutomationEngine? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (!startForegroundCompat()) {
            stopSelf()
            return
        }
        synchronized(lifecycleLock) {
            ensureEngineRunning()
        }
        com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundCompat()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        // (Re)start the engine if it died.
        synchronized(lifecycleLock) {
            ensureEngineRunning()
        }
        com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service and engine remain active even if the task/activity is swiped away from Recents
        startForegroundCompat()
        synchronized(lifecycleLock) {
            ensureEngineRunning()
        }
    }

    override fun onDestroy() {
        synchronized(lifecycleLock) {
            engine?.stop()
            engine = null
        }
        com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureEngineRunning() {
        if (engine?.running != true) {
            engine = AutomationEngine(this).also { it.start() }
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_engine),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.notif_channel_engine_desc)
            setShowBadge(false)
            setSound(null, AudioAttributes.Builder().build())
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(): Boolean {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasLocationPerm = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val types = if (hasLocationPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIF_ID, notification, types)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            return true
        } catch (t: Throwable) {
            getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .edit().putString(STARTUP_FAILURE_KEY, t.javaClass.simpleName + ": " + (t.message ?: "unknown failure")).apply()
            Log.e("AutomationService", "startForegroundCompat failed: ${t.message}", t)
            return false
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.notif_engine_title))
            .setContentText(getString(R.string.notif_engine_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    companion object {
        // Channel settings are immutable after creation. New ID upgrades older loud channel.
        private const val CHANNEL_ID = "engine_silent_v2"
        private const val NOTIF_ID = 1001
        private const val STATUS_PREFS = "automation_service_status"
        private const val STARTUP_FAILURE_KEY = "startup_failure"

        fun start(context: Context): Boolean {
            return try {
                val intent = Intent(context, AutomationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(STARTUP_FAILURE_KEY).apply()
                true
            } catch (t: Throwable) {
                context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(STARTUP_FAILURE_KEY, t.javaClass.simpleName + ": " + (t.message ?: "unknown failure")).apply()
                Log.e("AutomationService", "Failed to start AutomationService: ${t.message}", t)
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AutomationService::class.java))
            } catch (t: Throwable) {
                Log.e("AutomationService", "Failed to stop AutomationService: ${t.message}", t)
            }
        }
    }
}
