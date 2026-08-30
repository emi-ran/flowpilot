@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.actions.ShizukuPermissionBridge
import com.flowpilot.app.permission.ShizukuState
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.CapabilityPill

@Composable
fun PermissionsScreen(vm: AppViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val usage by vm.hasUsageAccess.collectAsState()
    val write by vm.hasWriteSecureSettings.collectAsState()
    val notif by vm.hasNotifications.collectAsState()
    val shizuku by vm.shizukuState.collectAsState()
    var showAdbDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshPermissions()
        ShizukuPermissionBridge.onResult = { vm.refreshPermissions() }
    }
    DisposableEffect(Unit) {
        onDispose { ShizukuPermissionBridge.onResult = null }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Setup FlowPilot", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("Some actions require additional system permissions.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))

            PermissionCard("Usage Access", "Required to detect when apps open. Open system Settings and allow FlowPilot.", usage) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermissionCard("Notifications", "Shows engine status while automation runs.", notif) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionCard("Secure settings", "Required for Battery Saver actions. Grant with ADB or Shizuku.", write) {
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

            Spacer(Modifier.weight(1f))
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
private fun PermissionCard(title: String, desc: String, granted: Boolean, pillText: String = "Permission required", action: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                CapabilityPill(if (granted) "Available" else pillText)
            }
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (!granted && action != null) Button(action, Modifier.align(Alignment.End), shape = RoundedCornerShape(14.dp)) { Text("Setup") }
        }
    }
}
