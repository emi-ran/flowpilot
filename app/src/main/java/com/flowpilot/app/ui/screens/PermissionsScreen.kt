@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
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
    val ignoresBatteryOptimizations by vm.ignoresBatteryOptimizations.collectAsState()
    val shizuku by vm.shizukuState.collectAsState()
    var showAdbDialog by remember { mutableStateOf(false) }

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
                "Required to toggle NFC. Install, start, then grant access.",
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
