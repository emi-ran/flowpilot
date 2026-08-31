package com.flowpilot.app.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.CapabilityRequirement

/** Runtime status of an action, what the UI shows as Available / Permission required / Shizuku required / Unsupported. */
enum class CapabilityStatus(val label: String) {
    AVAILABLE("Available"),
    PERMISSION_REQUIRED("Permission required"),
    SHIZUKU_REQUIRED("Shizuku required"),
    UNSUPPORTED("Unsupported on this device"),
}

/**
 * Central place deciding whether an action can run right now.
 * Reads real device state: Usage Access, WRITE_SECURE_SETTINGS, Shizuku, NFC hardware.
 */
class CapabilityManager(private val context: Context) {

    /** Is this device currently running under a foreground-user device policy controller? Not ours to use. */
    /** Has the user granted Usage Access to this app? */
    fun hasUsageAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Has the app been granted the (development-level) WRITE_SECURE_SETTINGS permission, e.g. via ADB pm grant? */
    fun hasWriteSecureSettings(): Boolean {
        val pm = context.packageManager
        return pm.checkPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS,
            context.packageName,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Has the app been granted WRITE_SETTINGS special access? */
    fun hasWriteSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    /** Does the device expose NFC hardware that can be toggled? */
    fun hasNfcHardware(): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC)

    /** Is Shizuku installed, running, and granted to this app? */
    fun shizukuState(): ShizukuState = when {
        !ShizukuShell.instance.isShizukuAvailable() -> ShizukuState.NOT_INSTALLED
        !ShizukuShell.instance.isShizukuRunning() -> ShizukuState.NOT_RUNNING
        !ShizukuShell.instance.hasPermission() -> ShizukuState.NOT_GRANTED
        else -> ShizukuState.READY
    }

    /**
     * Effective status for an action, given the current device state and the
     * optional override "granted via ADB" flag (recomputed from the permission itself).
     */
    fun statusFor(action: ActionType): CapabilityStatus = when (action.requirement) {
        CapabilityRequirement.NONE -> CapabilityStatus.AVAILABLE

        CapabilityRequirement.WRITE_SETTINGS ->
            if (hasWriteSettings()) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        CapabilityRequirement.WRITE_SECURE_SETTINGS ->
            when {
                hasWriteSecureSettings() -> CapabilityStatus.AVAILABLE
                shizukuState() == ShizukuState.READY -> CapabilityStatus.AVAILABLE
                else -> CapabilityStatus.PERMISSION_REQUIRED
            }

        CapabilityRequirement.SHIZUKU ->
            when {
                !hasNfcHardware() -> CapabilityStatus.UNSUPPORTED
                shizukuState() == ShizukuState.READY -> CapabilityStatus.AVAILABLE
                else -> CapabilityStatus.SHIZUKU_REQUIRED
            }

        CapabilityRequirement.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        CapabilityRequirement.VIBRATION ->
            if ((context.getSystemService(android.os.Vibrator::class.java))?.hasVibrator() == true) {
                CapabilityStatus.AVAILABLE
            } else CapabilityStatus.UNSUPPORTED

        CapabilityRequirement.UNSUPPORTED -> CapabilityStatus.UNSUPPORTED
    }

    /** open system Settings for Usage Access */
    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** open system Settings for this app's battery optimization allowlist */
    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** open system Settings for Modify system settings (WRITE_SETTINGS) */
    fun openWriteSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** open this app's system app-info page */
    fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** is this app exempt from battery optimization? */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NOT_GRANTED, READY }
