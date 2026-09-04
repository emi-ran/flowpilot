@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.actions.ShizukuPermissionBridge
import com.flowpilot.app.permission.ShizukuState
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.CapabilityPill
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionsScreen(vm: AppViewModel, back: () -> Unit) {
    BackHandler(onBack = back)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usage by vm.hasUsageAccess.collectAsState()
    val writeSettings by vm.hasWriteSettings.collectAsState()
    val write by vm.hasWriteSecureSettings.collectAsState()
    val notif by vm.hasNotifications.collectAsState()
    val notifPolicy by vm.hasNotificationPolicy.collectAsState()
    val notifListener by vm.hasNotificationListener.collectAsState()
    val wifiPerms by vm.hasWifiPermissions.collectAsState()
    val bluetoothConnect by vm.hasBluetoothConnectPermission.collectAsState()
    val readPhoneState by vm.hasReadPhoneStatePermission.collectAsState()
    val callPhone by vm.hasCallPhonePermission.collectAsState()
    val sendSms by vm.hasSendSmsPermission.collectAsState()
    val receiveSms by vm.hasReceiveSmsPermission.collectAsState()
    val hasFineLocation by vm.hasFineLocation.collectAsState()
    val hasBackgroundLocation by vm.hasBackgroundLocation.collectAsState()
    val isLocationServiceEnabled by vm.isLocationServiceEnabled.collectAsState()
    val hasNfcHardware by vm.hasNfcHardware.collectAsState()
    val isNfcEnabled by vm.isNfcEnabled.collectAsState()
    val ignoresBatteryOptimizations by vm.ignoresBatteryOptimizations.collectAsState()
    val shizuku by vm.shizukuState.collectAsState()
    var showAdbDialog by remember { mutableStateOf(false) }
    var showBackgroundLocationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshPermissions()
        ShizukuPermissionBridge.onResult = { vm.refreshPermissions() }
    }
    DisposableEffect(Unit) {
        onDispose { ShizukuPermissionBridge.onResult = null }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val wifiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPermissions() }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPermissions() }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val callPhoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val receiveSmsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val sendSmsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Setup FlowPilot", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Text("Some actions require additional system permissions.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))

            PermissionCard("Usage Access", "Required to detect when apps open. Open system Settings and allow FlowPilot.", usage) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermissionCard("Notifications", "Shows engine status while automation runs.", notif) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionCard(
                "Phone call state access",
                "Required to detect incoming, answered, outgoing, and ended phone calls. FlowPilot never reads call log, never accesses contacts, and never records audio.",
                readPhoneState,
            ) {
                phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
            PermissionCard(
                "Make direct phone calls",
                "Required for the Call number automation action to place real telephone calls automatically without manual dialer confirmation.",
                callPhone,
            ) {
                callPhoneLauncher.launch(Manifest.permission.CALL_PHONE)
            }
            PermissionCard(
                "Receive SMS messages",
                "Required for incoming SMS triggers to detect incoming messages, filter senders, match verification codes, and trigger automations.",
                receiveSms,
            ) {
                receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
            PermissionCard(
                "Send SMS messages",
                "Required for the Send SMS action to send automated background SMS text messages directly without manual app confirmation.",
                sendSms,
            ) {
                sendSmsLauncher.launch(Manifest.permission.SEND_SMS)
            }
            PermissionCard(
                "Notification listener access",
                "Allows FlowPilot to trigger automations when notifications arrive from selected apps.",
                notifListener,
            ) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            PermissionCard(
                "Bluetooth paired-device access",
                "Required on Android 12+ to list bonded devices and receive Bluetooth device connect/disconnect events. FlowPilot never runs Bluetooth discovery or stores paired-device history.",
                bluetoothConnect,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            PermissionCard(
                "NFC hardware & status",
                if (!hasNfcHardware) "NFC hardware is not available on this device."
                else if (!isNfcEnabled) "NFC is turned off in system settings. Turn on NFC to scan tags and trigger rules."
                else "NFC hardware is active and ready for tag triggers.",
                granted = hasNfcHardware && isNfcEnabled,
                pillText = if (!hasNfcHardware) "Unsupported" else "NFC disabled",
                actionText = "Turn on",
            ) {
                if (hasNfcHardware) {
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
            PermissionCard(
                "Wi-Fi SSID & Location access",
                "Android requires ACCESS_FINE_LOCATION permission, Wi-Fi permissions, and device Location enabled to identify connected Wi-Fi SSID and scan nearby networks for Wi-Fi triggers and conditions.",
                wifiPerms,
            ) {
                val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true

                if (!hasFine || !hasNearby) {
                    val list = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_WIFI_STATE,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    wifiLauncher.launch(list.toTypedArray())
                } else {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
            PermissionCard(
                "Background Location ('Allow all the time')",
                if (!hasFineLocation) {
                    "FlowPilot needs foreground location permission first. Tap to grant location access."
                } else if (!isLocationServiceEnabled) {
                    "Device GPS / Location service is turned off. Tap to open system Location settings and turn on Location."
                } else if (!hasBackgroundLocation) {
                    "Required so FlowPilot can read your GPS coordinates (\${location.lat}, \${location.lng}) in the background (e.g. for incoming SMS triggers or automations when the screen is locked). Tap to select 'Allow all the time' (Her zaman izin ver)."
                } else {
                    "FlowPilot has full background GPS access. Automations can read \${location.lat} and \${location.lng} anytime, even when the screen is locked."
                },
                granted = hasBackgroundLocation && isLocationServiceEnabled,
                pillText = if (!hasFineLocation) "Location needed" else if (!isLocationServiceEnabled) "GPS disabled" else if (!hasBackgroundLocation) "Foreground only" else "Granted",
                actionText = if (!hasFineLocation) "Grant" else if (!isLocationServiceEnabled) "Turn on GPS" else if (!hasBackgroundLocation) "Allow all the time" else "Settings",
            ) {
                if (!hasFineLocation) {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                } else if (!isLocationServiceEnabled) {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        showBackgroundLocationDialog = true
                    } else {
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            }
            PermissionCard(
                "Do Not Disturb access",
                "Allows FlowPilot to turn Do Not Disturb on or off. Grant Notification Policy Access in Android settings.",
                notifPolicy,
            ) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            PermissionCard(
                "Modify system settings",
                "Allows FlowPilot to change system settings such as Auto-rotate. Grant in Android special access settings.",
                writeSettings,
            ) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            PermissionCard(
                "Battery restrictions",
                "Allow FlowPilot to run without battery restrictions. Required for reliable schedules while screen is off.",
                ignoresBatteryOptimizations,
                pillText = "Restriction active",
            ) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            PermissionCard(
                "HyperOS Autostart",
                "HyperOS does not expose this setting for apps to read. Open list to verify FlowPilot is enabled, then set Battery saver to No restrictions.",
                false,
                pillText = "Check in HyperOS",
                actionText = "Open list",
            ) {
                val intent = Intent("miui.intent.action.OP_AUTO_START")
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
            PermissionCard(
                "Secure settings",
                if (shizuku == ShizukuState.READY) "Battery Saver actions can run through Shizuku. ADB grant is optional direct access."
                else "Required for Battery Saver actions. Grant with ADB or Shizuku.",
                write || shizuku == ShizukuState.READY,
                pillText = if (shizuku == ShizukuState.READY && !write) "Available via Shizuku" else "Permission required",
            ) {
                if (shizuku == ShizukuState.READY) vm.grantSecureSettingsViaShizuku { vm.refreshPermissions() }
                else showAdbDialog = true
            }
            PermissionCard(
                "Shizuku",
                "Required to toggle NFC and Bluetooth. Install, start, then grant access.",
                shizuku == ShizukuState.READY,
                pillText = if (shizuku == ShizukuState.READY) "Available" else "Shizuku required",
            ) {
                when (shizuku) {
                    ShizukuState.NOT_INSTALLED -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                    ShizukuState.NOT_RUNNING -> {
                        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.xyz.manager")
                        if (launch != null) context.startActivity(launch)
                        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                    }
                    ShizukuState.NOT_GRANTED -> vm.requestShizukuPermission()
                    ShizukuState.READY -> {}
                }
            }

            Text("Tip: tap the status pill on an automation to jump here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
        }
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = { Text("Grant via ADB") },
            text = { Text("Connect the phone over USB with debugging enabled, then run:\n\nadb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS\n\nOr start Shizuku, tap Setup here and FlowPilot grants it for you.") },
            confirmButton = { TextButton({ showAdbDialog = false }) { Text("OK") } },
        )
    }

    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDialog = false },
            title = { Text("Allow all the time (Her zaman izin ver)") },
            text = {
                Text(
                    "To send your GPS location while the app is in the background or screen is off, Android requires the 'Allow all the time' (Her zaman izin ver) permission.\n\n" +
                        "1. Tap 'Open settings' below\n" +
                        "2. Tap 'Permissions' > 'Location'\n" +
                        "3. Select 'Allow all the time' (Her zaman izin ver)"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundLocationDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    pillText: String = "Permission required",
    actionText: String = "Setup",
    action: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                CapabilityPill(if (granted) "Available" else pillText)
            }
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (!granted && action != null) Button(action, Modifier.align(Alignment.End), shape = RoundedCornerShape(14.dp)) { Text(actionText) }
        }
    }
}
