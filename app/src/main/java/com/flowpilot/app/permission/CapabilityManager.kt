package com.flowpilot.app.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothManager
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
 * Reads real device state: Usage Access, WRITE_SECURE_SETTINGS, Shizuku, NFC hardware, Phone permissions.
 */
class CapabilityManager(private val context: Context) {

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

    /** Has the app been granted Notification Policy Access (Do Not Disturb access)? */
    fun hasNotificationPolicyAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    /** Has the app been granted Notification Listener access? */
    fun hasNotificationListenerAccess(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val myComponent = "${context.packageName}/${com.flowpilot.app.engine.FlowPilotNotificationListener::class.java.name}"
        return flat.split(":").any { it.trim().equals(myComponent, ignoreCase = true) || it.trim().startsWith("${context.packageName}/") }
    }

    /** Has the app been granted Location / Wi-Fi permissions and is Location service enabled to read SSID? */
    fun hasWifiPermissions(): Boolean {
        val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasWifi = context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm?.isLocationEnabled == true
        } else {
            lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        }
        return hasFine && hasWifi && locationEnabled
    }

    /** Has the app been granted permissions to perform Wi-Fi scanning (API 33+ NEARBY_WIFI_DEVICES + Location)? */
    fun hasWifiScanPermissions(): Boolean {
        val baseWifi = hasWifiPermissions()
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        return baseWifi && hasNearby
    }

    /** Android 12+ gates bonded-device access and ACL broadcast device data behind Nearby devices. */
    fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** A null adapter means Bluetooth is unsupported; do not claim Shizuku can fix missing hardware. */
    fun hasBluetoothAdapter(): Boolean = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter != null
    } catch (_: Throwable) {
        false
    }

    /** Does the device expose NFC hardware that can be toggled? */
    fun hasNfcHardware(): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC)

    /** Is NFC enabled in system settings right now? */
    fun isNfcEnabled(): Boolean {
        if (!hasNfcHardware()) return false
        val nfcAdapter = try {
            val manager = context.getSystemService(Context.NFC_SERVICE) as? android.nfc.NfcManager
            manager?.defaultAdapter
        } catch (_: Throwable) {
            null
        }
        return nfcAdapter?.isEnabled == true
    }

    /** Open system Settings for NFC */
    fun openNfcSettings() {
        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    /** Is Shizuku installed, running, and granted to this app? */
    fun shizukuState(): ShizukuState = when {
        !ShizukuShell.instance.isShizukuAvailable() -> ShizukuState.NOT_INSTALLED
        !ShizukuShell.instance.isShizukuRunning() -> ShizukuState.NOT_RUNNING
        !ShizukuShell.instance.hasPermission() -> ShizukuState.NOT_GRANTED
        else -> ShizukuState.READY
    }

    /** Has the app been granted READ_PHONE_STATE permission to detect call state transitions? */
    fun hasReadPhoneStatePermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Has the app been granted CALL_PHONE permission to initiate phone calls directly? */
    fun hasCallPhonePermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

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
                action.category == com.flowpilot.app.data.model.ActionCategory.NFC && !hasNfcHardware() -> CapabilityStatus.UNSUPPORTED
                action.category == com.flowpilot.app.data.model.ActionCategory.CONNECTIVITY && !hasBluetoothAdapter() -> CapabilityStatus.UNSUPPORTED
                action.category == com.flowpilot.app.data.model.ActionCategory.CONNECTIVITY && !hasBluetoothConnectPermission() -> CapabilityStatus.PERMISSION_REQUIRED
                shizukuState() == ShizukuState.READY -> CapabilityStatus.AVAILABLE
                else -> CapabilityStatus.SHIZUKU_REQUIRED
            }

        CapabilityRequirement.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        CapabilityRequirement.NOTIFICATION_POLICY ->
            if (hasNotificationPolicyAccess()) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        CapabilityRequirement.VIBRATION ->
            if ((context.getSystemService(android.os.Vibrator::class.java))?.hasVibrator() == true) {
                CapabilityStatus.AVAILABLE
            } else CapabilityStatus.UNSUPPORTED

        CapabilityRequirement.CALL_PHONE ->
            if (hasCallPhonePermission()) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED

        CapabilityRequirement.UNSUPPORTED -> CapabilityStatus.UNSUPPORTED
    }

    /** open system Settings for Usage Access */
    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** open system Settings for Notification Policy Access (Do Not Disturb access) */
    fun openNotificationPolicySettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** open system Settings for Notification Listener special access */
    fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
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

    /** open system Settings for Location */
    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
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
