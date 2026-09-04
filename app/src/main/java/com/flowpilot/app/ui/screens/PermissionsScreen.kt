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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
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
            title = { Text(stringResource(R.string.perms_title), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Text(stringResource(R.string.perms_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))

            PermissionCard(stringResource(R.string.perms_usage_title), stringResource(R.string.perms_usage_desc), usage) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermissionCard(stringResource(R.string.perms_notif_title), stringResource(R.string.perms_notif_desc), notif) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionCard(
                stringResource(R.string.perms_phone_state_title),
                stringResource(R.string.perms_phone_state_desc),
                readPhoneState,
            ) {
                phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
            PermissionCard(
                stringResource(R.string.perms_call_phone_title),
                stringResource(R.string.perms_call_phone_desc),
                callPhone,
            ) {
                callPhoneLauncher.launch(Manifest.permission.CALL_PHONE)
            }
            PermissionCard(
                stringResource(R.string.perms_receive_sms_title),
                stringResource(R.string.perms_receive_sms_desc),
                receiveSms,
            ) {
                receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
            PermissionCard(
                stringResource(R.string.perms_send_sms_title),
                stringResource(R.string.perms_send_sms_desc),
                sendSms,
            ) {
                sendSmsLauncher.launch(Manifest.permission.SEND_SMS)
            }
            PermissionCard(
                stringResource(R.string.perms_notif_listener_title),
                stringResource(R.string.perms_notif_listener_desc),
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
                stringResource(R.string.perms_bluetooth_title),
                stringResource(R.string.perms_bluetooth_desc),
                bluetoothConnect,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            PermissionCard(
                stringResource(R.string.perms_nfc_title),
                if (!hasNfcHardware) stringResource(R.string.perms_nfc_unsupported)
                else if (!isNfcEnabled) stringResource(R.string.perms_nfc_disabled)
                else stringResource(R.string.perms_nfc_ready),
                granted = hasNfcHardware && isNfcEnabled,
                pillText = if (!hasNfcHardware) stringResource(R.string.capability_unsupported) else stringResource(R.string.perms_nfc_pill_disabled),
                actionText = stringResource(R.string.perms_btn_turn_on),
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
                stringResource(R.string.perms_wifi_loc_title),
                stringResource(R.string.perms_wifi_loc_desc),
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
                stringResource(R.string.perms_bg_loc_title),
                if (!hasFineLocation) {
                    stringResource(R.string.perms_bg_loc_need_fg)
                } else if (!isLocationServiceEnabled) {
                    stringResource(R.string.perms_bg_loc_gps_off)
                } else if (!hasBackgroundLocation) {
                    stringResource(R.string.perms_bg_loc_need_all_time)
                } else {
                    stringResource(R.string.perms_bg_loc_ready)
                },
                granted = hasBackgroundLocation && isLocationServiceEnabled,
                pillText = if (!hasFineLocation) stringResource(R.string.perms_bg_loc_pill_needed) else if (!isLocationServiceEnabled) stringResource(R.string.perms_bg_loc_pill_gps_off) else if (!hasBackgroundLocation) stringResource(R.string.perms_bg_loc_pill_fg_only) else stringResource(R.string.perms_pill_granted),
                actionText = if (!hasFineLocation) stringResource(R.string.perms_btn_grant) else if (!isLocationServiceEnabled) stringResource(R.string.perms_bg_loc_action_turn_gps) else if (!hasBackgroundLocation) stringResource(R.string.perms_bg_loc_action_allow_all) else stringResource(R.string.perms_btn_settings),
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
                stringResource(R.string.perms_dnd_title),
                stringResource(R.string.perms_dnd_desc),
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
                stringResource(R.string.perms_modify_settings_title),
                stringResource(R.string.perms_modify_settings_desc),
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
                stringResource(R.string.perms_battery_opt_title),
                stringResource(R.string.perms_battery_opt_desc),
                ignoresBatteryOptimizations,
                pillText = stringResource(R.string.perms_battery_opt_pill),
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
                stringResource(R.string.perms_hyperos_title),
                stringResource(R.string.perms_hyperos_desc),
                false,
                pillText = stringResource(R.string.perms_hyperos_pill),
                actionText = stringResource(R.string.perms_btn_open_list),
            ) {
                val intent = Intent("miui.intent.action.OP_AUTO_START")
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
            PermissionCard(
                stringResource(R.string.perms_secure_settings_title),
                if (shizuku == ShizukuState.READY) stringResource(R.string.perms_secure_settings_ready)
                else stringResource(R.string.perms_secure_settings_needed),
                write || shizuku == ShizukuState.READY,
                pillText = if (shizuku == ShizukuState.READY && !write) stringResource(R.string.perms_secure_settings_pill_shizuku) else stringResource(R.string.capability_permission_required),
            ) {
                if (shizuku == ShizukuState.READY) vm.grantSecureSettingsViaShizuku { vm.refreshPermissions() }
                else showAdbDialog = true
            }
            PermissionCard(
                stringResource(R.string.perms_shizuku_title),
                stringResource(R.string.perms_shizuku_desc),
                shizuku == ShizukuState.READY,
                pillText = if (shizuku == ShizukuState.READY) stringResource(R.string.capability_available) else stringResource(R.string.capability_shizuku_required),
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

            Text(stringResource(R.string.perms_tip), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
        }
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = { Text(stringResource(R.string.perms_adb_dialog_title)) },
            text = { Text(stringResource(R.string.perms_adb_dialog_content, context.packageName)) },
            confirmButton = { TextButton({ showAdbDialog = false }) { Text(stringResource(R.string.btn_ok)) } },
        )
    }

    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDialog = false },
            title = { Text(stringResource(R.string.perms_bg_loc_dialog_title)) },
            text = {
                Text(stringResource(R.string.perms_bg_loc_dialog_content))
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
                    Text(stringResource(R.string.perms_btn_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
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
    pillText: String = stringResource(R.string.capability_permission_required),
    actionText: String = stringResource(R.string.perms_btn_setup),
    action: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                CapabilityPill(
                    text = if (granted) stringResource(R.string.capability_available) else pillText,
                    isSuccess = granted,
                )
            }
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (!granted && action != null) Button(action, Modifier.align(Alignment.End), shape = RoundedCornerShape(14.dp)) { Text(actionText) }
        }
    }
}
