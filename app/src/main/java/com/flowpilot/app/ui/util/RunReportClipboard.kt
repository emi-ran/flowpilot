package com.flowpilot.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import com.flowpilot.app.R
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.diagnostics.ReportEnvironment
import com.flowpilot.app.diagnostics.RunReportFormatter

/** Android glue for the pure [RunReportFormatter]: reads build facts and writes the clipboard. */
object RunReportClipboard {
    fun environment(context: Context): ReportEnvironment {
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        @Suppress("DEPRECATION")
        val versionCode = if (info == null) 0L else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return ReportEnvironment(
            appVersionName = info?.versionName ?: "unknown",
            appVersionCode = versionCode,
            buildType = if (debuggable) "debug" else "release",
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
        )
    }

    fun build(context: Context, entry: ExecutionHistoryEntry): String =
        RunReportFormatter.format(entry, environment(context))

    /**
     * The report is sanitized and meant to be pasted, so the clip is intentionally NOT flagged as
     * sensitive on API 33+ (that would hide it from the paste preview).
     */
    fun copy(context: Context, entry: ExecutionHistoryEntry) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("FlowPilot run report", build(context, entry)))
        // Android 13+ shows its own clipboard confirmation; avoid a duplicate toast there.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, R.string.history_report_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
