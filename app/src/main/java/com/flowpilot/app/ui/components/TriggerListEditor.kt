@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
package com.flowpilot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { i, definition ->
                TriggerSummaryCard(
                    index = i,
                    definition = definition,
                    selected = i == index,
                    canMoveUp = i > 0,
                    canMoveDown = i < items.lastIndex,
                    canRemove = items.size > 1,
                    onSelect = { selected = i },
                    onMoveUp = {
                        val moved = items.toMutableList(); moved.add(i - 1, moved.removeAt(i)); onChange(moved); selected = i - 1
                    },
                    onMoveDown = {
                        val moved = items.toMutableList(); moved.add(i + 1, moved.removeAt(i)); onChange(moved); selected = i + 1
                    },
                    onRemove = { pendingRemoval = i },
                )
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
    pendingRemoval?.let { removeIndex ->
        if (removeIndex in items.indices) {
            val removedLabel = items[removeIndex].event.localizedLabel()
            AlertDialog(
                onDismissRequest = { pendingRemoval = null },
                title = { Text(stringResource(R.string.trigger_remove)) },
                text = { Text(removedLabel) },
                confirmButton = {
                    TextButton(onClick = {
                        onChange(items.filterIndexed { n, _ -> n != removeIndex })
                        selected = (removeIndex - 1).coerceAtLeast(0)
                        pendingRemoval = null
                    }) { Text(stringResource(R.string.btn_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemoval = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
            )
        }
    }
}

/** Selectable summary of one trigger; destructive removal asks for confirmation. */
@Composable
private fun TriggerSummaryCard(
    index: Int,
    definition: TriggerDefinition,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = triggerAccent(definition.event)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        triggerIcon(definition.event),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.trigger_source_index, index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        definition.event.localizedLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 220, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                ) + fadeIn(animationSpec = tween(140)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                ) + fadeOut(animationSpec = tween(100)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TriggerSummaryAction(Icons.Default.ArrowUpward, stringResource(R.string.trigger_move_up), canMoveUp, onMoveUp)
                    TriggerSummaryAction(Icons.Default.ArrowDownward, stringResource(R.string.trigger_move_down), canMoveDown, onMoveDown)
                    TriggerSummaryAction(
                        Icons.Default.Delete,
                        stringResource(R.string.trigger_remove),
                        canRemove,
                        onRemove,
                        isDestructive = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerSummaryAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
        )
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
        onClearNotificationApp = { onUpdate(view.copy(notificationAppPackage = "", notificationAppName = "")) },
        onNotificationKeywordChange = { onUpdate(view.copy(notificationKeyword = it)) },
        notificationTitleFilter = view.notificationTitleFilter, notificationTitleMatchMode = view.notificationTitleMatchMode,
        notificationMessageFilter = view.notificationMessageFilter, notificationMessageMatchMode = view.notificationMessageMatchMode,
        onNotificationTitleFilterChange = { onUpdate(view.copy(notificationTitleFilter = it)) },
        onNotificationTitleMatchModeChange = { onUpdate(view.copy(notificationTitleMatchMode = it)) },
        onNotificationMessageFilterChange = { onUpdate(view.copy(notificationMessageFilter = it)) },
        onNotificationMessageMatchModeChange = { onUpdate(view.copy(notificationMessageMatchMode = it)) },
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
