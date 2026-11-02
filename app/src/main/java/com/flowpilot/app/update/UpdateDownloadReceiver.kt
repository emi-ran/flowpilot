package com.flowpilot.app.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flowpilot.app.R

/**
 * Receives the DownloadManager completion for the APK this app requested. It never installs an
 * arbitrary download: the expected download id is recorded before enqueueing and checked here.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0L) return
        val prefs = context.getSharedPreferences(UpdateDownloader.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(UpdateDownloader.KEY_DOWNLOAD_ID, -1L) != id) return

        val manager = context.getSystemService(DownloadManager::class.java) ?: return
        val status = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@use -1
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            prefs.edit().remove(UpdateDownloader.KEY_DOWNLOAD_ID).apply()
            return
        }
        val apkUri = manager.getUriForDownloadedFile(id)
        val versionLabel = prefs.getString(UpdateDownloader.KEY_VERSION_LABEL, null).orEmpty()
        prefs.edit().clear().apply()
        if (apkUri == null) return

        val installIntent = createInstallIntent(context, apkUri)
        if (!context.packageManager.canRequestPackageInstalls()) {
            postPermissionNotification(context, versionLabel)
            return
        }
        // Posting first keeps the install reachable if background activity launch is blocked.
        postInstallNotification(context, installIntent, versionLabel)
        runCatching { context.startActivity(installIntent) }
    }

    @Suppress("DEPRECATION")
    private fun createInstallIntent(context: Context, apkUri: Uri): Intent {
        val installer = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(apkUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (installer.resolveActivity(context.packageManager) != null) return installer
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, UpdateDownloader.APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun postInstallNotification(
        context: Context,
        installIntent: Intent,
        versionLabel: String,
    ) {
        val pending = PendingIntent.getActivity(
            context,
            INSTALL_REQUEST_CODE,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context,
            title = context.getString(R.string.update_install_notification_title),
            text = context.getString(R.string.update_install_notification_text, versionLabel),
            pendingIntent = pending,
        )
    }

    private fun createPermissionSettingsIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val primary = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (primary.resolveActivity(context.packageManager) != null) return primary
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun postPermissionNotification(context: Context, versionLabel: String) {
        val pending = PendingIntent.getActivity(
            context,
            PERMISSION_REQUEST_CODE,
            createPermissionSettingsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context,
            title = context.getString(R.string.update_permission_notification_title),
            text = context.getString(R.string.update_permission_notification_text, versionLabel),
            pendingIntent = pending,
        )
    }

    private fun post(context: Context, title: String, text: String, pendingIntent: PendingIntent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission can be denied; DownloadManager's own completed notification
            // remains available for installing the APK.
        }
    }

    private companion object {
        const val CHANNEL_ID = "flowpilot_update"
        const val NOTIFICATION_ID = 12001
        const val INSTALL_REQUEST_CODE = 12002
        const val PERMISSION_REQUEST_CODE = 12003
    }
}
