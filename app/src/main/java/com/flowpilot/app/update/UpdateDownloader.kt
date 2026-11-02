package com.flowpilot.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import com.flowpilot.app.R

sealed interface UpdateDownloadResult {
    data class Started(val fileName: String) : UpdateDownloadResult
    data object PermissionRequired : UpdateDownloadResult
    data object NoAsset : UpdateDownloadResult
    data class Failed(val reason: String) : UpdateDownloadResult
}

object UpdateDownloader {
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    internal const val PREFS_NAME = "update_download"
    internal const val KEY_DOWNLOAD_ID = "download_id"
    internal const val KEY_VERSION_LABEL = "version_label"

    fun start(context: Context, release: UpdateRelease): UpdateDownloadResult {
        val downloadUrl = release.downloadUrl ?: return UpdateDownloadResult.NoAsset
        if (!GitHubReleaseClient.isAllowedDownloadUrl(downloadUrl)) {
            return UpdateDownloadResult.Failed("Release asset URL is not trusted")
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            return UpdateDownloadResult.PermissionRequired
        }
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return UpdateDownloadResult.Failed("Download manager is unavailable")
        val fileName = safeFileName(release.versionLabel)
        val destination = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(fileName)
            ?.takeIf { it.exists() }
        destination?.delete()
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(context.getString(R.string.update_download_title, release.versionLabel))
            .setDescription(context.getString(R.string.update_download_description))
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = try {
            manager.enqueue(request)
        } catch (error: Exception) {
            return UpdateDownloadResult.Failed(error.message ?: "Download could not start")
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_VERSION_LABEL, release.versionLabel)
            .apply()
        return UpdateDownloadResult.Started(fileName)
    }

    fun openInstallPermissionSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(settingsIntent)
        } catch (_: Exception) {
            context.startActivity(fallback)
        }
    }

    fun openReleasePage(context: Context, release: UpdateRelease) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    internal fun safeFileName(versionLabel: String): String {
        val safe = versionLabel
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')
            .ifBlank { "update" }
        return "FlowPilot-$safe.apk"
    }
}
