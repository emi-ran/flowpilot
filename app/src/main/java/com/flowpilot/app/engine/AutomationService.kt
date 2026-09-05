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
import com.flowpilot.app.data.AutomationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the automation engine in a foreground "special use" service so it survives
 * normal lifecycle changes and can keep polling the foreground app. The ongoing
 * notification explains why the service is running (required on Android 14+).
 */
class AutomationService : Service() {

    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleLock = Mutex()
    private var engine: AutomationEngine? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        loadFailure(this)
        createChannel()
        if (!startForegroundCompat()) {
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundCompat()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        // (Re)start the engine if it died.
        lifecycleScope.launch {
            try {
                lifecycleLock.withLock {
                    if (!AutomationRepository(applicationContext).isEngineEnabled.first()) {
                        engine?.stop()
                        stopSelfResult(startId)
                        return@withLock
                    }
                    ensureEngineRunning()
                    com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(this@AutomationService)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                engine?.stop()
                reportStartupFailure(this@AutomationService)
                Log.e("AutomationService", "Engine reconciliation failed (${e.javaClass.simpleName})")
                stopSelfResult(startId)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service and engine remain active even if the task/activity is swiped away from Recents
        if (!startForegroundCompat()) {
            stopSelf()
            return
        }
        // onStartCommand owns persisted-enabled reconciliation; never restart from stale task callback.
    }

    override fun onDestroy() {
        lifecycleScope.cancel()
        engine?.stop()
        engine = null
        if (activeService === this) {
            activeService = null
            mutableRunning.value = false
        }
        com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureEngineRunning() {
        val current = engine ?: AutomationEngine(
            this,
            onRunningChanged = { running ->
                if (activeService === this) {
                    mutableRunning.value = running
                    if (running) clearFailure(this)
                    com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(this)
                }
            },
            onFailure = { if (activeService === this) reportStartupFailure(this) },
        ).also { engine = it }
        current.start()
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
        ensureFailureChannel(this)
    }

    private fun startForegroundCompat(): Boolean {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasBackgroundLocationPerm = checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val types = if (hasBackgroundLocationPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIF_ID, notification, types)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            return true
        } catch (_: Throwable) {
            reportStartupFailure(this)
            Log.e("AutomationService", "startForegroundCompat failed")
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
        private val controlMutex = Mutex()
        private var activeService: AutomationService? = null
        private val mutableRunning = MutableStateFlow(false)
        val running = mutableRunning.asStateFlow()
        private val mutableFailure = MutableStateFlow(false)
        val failure = mutableFailure.asStateFlow()

        @Synchronized
        fun loadFailure(context: Context) {
            mutableFailure.value = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .contains(STARTUP_FAILURE_KEY)
        }

        @Synchronized
        private fun clearFailure(context: Context) {
            context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .edit().remove(STARTUP_FAILURE_KEY).apply()
            mutableFailure.value = false
        }

        suspend fun setEnabled(context: Context, enabled: Boolean) = control(context) {
            setEngineEnabled(enabled)
            enabled
        }

        suspend fun toggleEnabled(context: Context) = control(context) { toggleEngineEnabled() }

        suspend fun reconcileEnabled(context: Context) = control(context) { isEngineEnabled.first() }

        // Never acquire this lock from engineLifetime: controls only enqueue Android commands.
        private suspend fun control(
            context: Context,
            preference: suspend AutomationRepository.() -> Boolean,
        ): Boolean = controlMutex.withLock {
            withContext(NonCancellable) {
                try {
                    val enabled = AutomationRepository(context.applicationContext).preference()
                    if (enabled) start(context) else {
                        stop(context)
                        clearFailure(context)
                        com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(context)
                        true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    reportStartupFailure(context)
                    false
                }
            }
        }

        // Channel settings are immutable after creation. New ID upgrades older loud channel.
        private const val CHANNEL_ID = "engine_silent_v2"
        private const val FAILURE_CHANNEL_ID = "engine_startup_failure"
        private const val NOTIF_ID = 1001
        private const val FAILURE_NOTIF_ID = 1002
        private const val STATUS_PREFS = "automation_service_status"
        private const val STARTUP_FAILURE_KEY = "startup_failure"

        private fun ensureFailureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(
                    NotificationChannel(
                        FAILURE_CHANNEL_ID,
                        context.getString(R.string.notif_channel_engine_failure),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = context.getString(R.string.notif_channel_engine_failure_desc)
                    },
                )
            }
        }

        @Synchronized
        fun reportStartupFailure(context: Context) {
            mutableFailure.value = true
            try {
                context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(STARTUP_FAILURE_KEY, "startup failed").apply()
            } catch (_: Exception) {
                Log.w("AutomationService", "Failure status persistence unavailable")
            }
            com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(context)
            try {
                ensureFailureChannel(context)
                val openIntent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 1, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, FAILURE_CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(context)
                }
                val notification = builder
                    .setContentTitle(context.getString(R.string.notif_engine_failure_title))
                    .setContentText(context.getString(R.string.notif_engine_failure_text))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ERROR)
                    .build()
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(FAILURE_NOTIF_ID, notification)
            } catch (_: Exception) {
                Log.w("AutomationService", "Failure notification unavailable")
            }
        }

        private fun start(context: Context): Boolean {
            return try {
                val intent = Intent(context, AutomationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: Throwable) {
                reportStartupFailure(context)
                Log.e("AutomationService", "Failed to start AutomationService")
                false
            }
        }

        private fun stop(context: Context) {
            context.stopService(Intent(context, AutomationService::class.java))
        }
    }
}
