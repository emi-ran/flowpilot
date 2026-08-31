package com.flowpilot.app.actions

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.flowpilot.app.R
import com.flowpilot.app.data.model.ActionType
import java.util.concurrent.atomic.AtomicInteger

/** Posts user-configured automation notifications through a separate visible channel. */
class NotificationExecutor(
    private val context: Context,
    private val permissionChecker: (Context) -> Boolean = { ctx ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    },
    private val poster: (Int, Notification) -> Unit = { id, notification ->
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
    },
) : ActionExecutor {
    override val supportedTypes = setOf(ActionType.SHOW_NOTIFICATION)

    override fun execute(action: ActionType, notificationTitle: String, notificationBody: String): ActionResult {
        if (action != ActionType.SHOW_NOTIFICATION) return ActionResult(false, "Unsupported action for notification")
        if (!permissionChecker(context)) return ActionResult(false, "Notification permission not granted")
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Automation alerts", NotificationManager.IMPORTANCE_HIGH),
                )
            }
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(notificationTitle.ifBlank { "FlowPilot" })
                .setContentText(notificationBody.ifBlank { "Automation ran" })
                .setAutoCancel(true)
                .build()
            poster(nextId.incrementAndGet(), notification)
            ActionResult(true, "Notification posted")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }

    private companion object {
        // Android preserves an existing channel's importance, so this new ID upgrades prior default alerts.
        const val CHANNEL_ID = "automation_alerts_v2"
        val nextId = AtomicInteger(2000)
    }
}
