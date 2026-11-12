@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.flowpilot.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.data.model.*
import com.flowpilot.app.engine.NfcTagHandoff
import com.flowpilot.app.ui.util.localizedLabel

/** Each selected entry owns its picker state and isolated payload. */
@Composable
fun TriggerListEditor(items: List<TriggerDefinition>, onChange: (List<TriggerDefinition>) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var pickingType by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val index = selected.coerceIn(items.indices)
    val base = Automation(id = "", name = "", createdAt = 0)
    val view = base.withTrigger(items[index])
    fun update(updated: Automation) {
        onChange(items.mapIndexed { i, old -> if (i == index) updated.legacyTriggerDefinition() else old })
    }
    if (pickingType) TriggerPicker(view.triggerEvent, { event ->
        val definition = base.copy(triggerEvent = event).legacyTriggerDefinition()
        if (adding) { onChange(items + definition); selected = items.size }
        else onChange(items.mapIndexed { i, old -> if (i == index) definition else old })
        pickingType = false
    }) { pickingType = false }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.triggers_any_summary), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEachIndexed { i, definition ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = i == index, onClick = { selected = i },
                    label = { Text("${i + 1}. ${definition.event.localizedLabel()}") },
                    modifier = Modifier.weight(1f))
                IconButton(enabled = i > 0, onClick = {
                    val moved = items.toMutableList(); moved.add(i - 1, moved.removeAt(i)); onChange(moved); selected = i - 1
                }) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.trigger_move_up)) }
                IconButton(enabled = i < items.lastIndex, onClick = {
                    val moved = items.toMutableList(); moved.add(i + 1, moved.removeAt(i)); onChange(moved); selected = i + 1
                }) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.trigger_move_down)) }
                IconButton(enabled = items.size > 1, onClick = {
                    onChange(items.filterIndexed { n, _ -> n != i }); selected = (i - 1).coerceAtLeast(0)
                }) { Icon(Icons.Default.Delete, stringResource(R.string.trigger_remove)) }
            }
        }
        OutlinedButton(enabled = items.size < TriggerDefinitions.MAX_TRIGGERS,
            onClick = { adding = true; pickingType = true }) {
            Icon(Icons.Default.Add, null); Text(stringResource(R.string.trigger_add))
        }
        key(index) {
            TriggerDefinitionEditor(view, onChangeType = { adding = false; pickingType = true }, onUpdate = ::update)
        }
    }
}

@Composable
private fun TriggerDefinitionEditor(view: Automation, onChangeType: () -> Unit, onUpdate: (Automation) -> Unit) {
    val context = LocalContext.current
    var appPicker by remember { mutableStateOf(false) }
    var notificationPicker by remember { mutableStateOf(false) }
    var mediaPicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    if (appPicker) AppPicker(select = { p, n -> onUpdate(view.copy(appPackage = p, appName = n)); appPicker = false },
        selectedPackage = view.appPackage, onDismiss = { appPicker = false })
    if (notificationPicker) AppPicker(select = { p, n -> onUpdate(view.copy(notificationAppPackage = p, notificationAppName = n)); notificationPicker = false },
        selectedPackage = view.notificationAppPackage, onDismiss = { notificationPicker = false })
    if (mediaPicker) AppPicker(select = { p, _ -> onUpdate(view.copy(mediaTriggerPackage = p)); mediaPicker = false },
        selectedPackage = view.mediaTriggerPackage, onDismiss = { mediaPicker = false })
    if (timePicker) {
        DisposableEffect(Unit) {
            val dialog = android.app.TimePickerDialog(context, { _, h, m ->
                onUpdate(view.copy(scheduledMinute = h * 60 + m)); timePicker = false
            }, view.scheduledMinute / 60, view.scheduledMinute % 60, true)
            dialog.setOnCancelListener { timePicker = false }; dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
    DisposableEffect(view.triggerEvent) {
        if (view.triggerEvent == TriggerEvent.NFC_TAG_SCANNED) NfcTagHandoff.clearLatestScannedTagId()
        NfcTagHandoff.requestCapture(view.triggerEvent == TriggerEvent.NFC_TAG_SCANNED)
        onDispose {
            NfcTagHandoff.clearLatestScannedTagId()
            NfcTagHandoff.requestCapture(false)
        }
    }
    val latestUpdate by rememberUpdatedState(onUpdate)
    val latestView by rememberUpdatedState(view)
    LaunchedEffect(view.triggerEvent) {
        if (view.triggerEvent == TriggerEvent.NFC_TAG_SCANNED) {
            NfcTagHandoff.latestScannedTagId.collect { id -> id?.let { latestUpdate(latestView.copy(nfcTagId = it)) } }
        }
    }
    TriggerCardItem(
        event = view.triggerEvent, onChangeTrigger = onChangeType,
        pkg = view.appPackage, appName = view.appName, onOpenAppPicker = { appPicker = true },
        scheduledMinute = view.scheduledMinute, scheduledDays = view.scheduledDays,
        onOpenTimePicker = { timePicker = true }, onDaysChange = { onUpdate(view.copy(scheduledDays = it)) },
        batteryLevel = view.batteryLevel, onBatteryLevelChange = { onUpdate(view.copy(batteryLevel = it)) },
        wifiSsid = view.wifiSsid, onWifiSsidChange = { onUpdate(view.copy(wifiSsid = it)) },
        bluetoothAddress = view.bluetoothDeviceAddress, bluetoothName = view.bluetoothDeviceName,
        onBluetoothChange = { a, n -> onUpdate(view.copy(bluetoothDeviceAddress = a, bluetoothDeviceName = n)) },
        nfcTagId = view.nfcTagId, onNfcTagChange = { onUpdate(view.copy(nfcTagId = it)) },
        notificationAppPackage = view.notificationAppPackage, notificationAppName = view.notificationAppName,
        notificationKeyword = view.notificationKeyword, onOpenNotificationAppPicker = { notificationPicker = true },
        onNotificationKeywordChange = { onUpdate(view.copy(notificationKeyword = it)) },
        mediaTriggerPackage = view.mediaTriggerPackage,
        onOpenMediaAppPicker = { mediaPicker = true },
        onClearMediaTriggerPackage = { onUpdate(view.copy(mediaTriggerPackage = "")) },
        flipScreenOffDetection = view.flipScreenOffDetection, onFlipScreenOffChange = { onUpdate(view.copy(flipScreenOffDetection = it)) },
        lightLux = view.lightLux, onLightLuxChange = { onUpdate(view.copy(lightLux = it)) },
        shakeThreshold = view.shakeThreshold, onShakeThresholdChange = { onUpdate(view.copy(shakeThreshold = it)) },
        smsSenderFilter = view.smsSenderFilter, onSmsSenderFilterChange = { onUpdate(view.copy(smsSenderFilter = it)) },
        smsMatchMode = view.smsMatchMode, onSmsMatchModeChange = { onUpdate(view.copy(smsMatchMode = it)) },
        smsKeyword = view.smsKeyword, onSmsKeywordChange = { onUpdate(view.copy(smsKeyword = it)) },
        geofenceName = view.geofenceName, onGeofenceNameChange = { onUpdate(view.copy(geofenceName = it)) },
        geofenceLatitude = view.geofenceLatitude, onGeofenceLatitudeChange = { onUpdate(view.copy(geofenceLatitude = it)) },
        geofenceLongitude = view.geofenceLongitude, onGeofenceLongitudeChange = { onUpdate(view.copy(geofenceLongitude = it)) },
        geofenceRadiusMeters = view.geofenceRadiusMeters, onGeofenceRadiusChange = { onUpdate(view.copy(geofenceRadiusMeters = it)) },
    )
}
