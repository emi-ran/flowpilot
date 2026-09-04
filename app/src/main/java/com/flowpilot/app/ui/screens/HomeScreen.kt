@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.flowpilot.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.AutomationUI
import com.flowpilot.app.ui.components.CapabilityPill
import com.flowpilot.app.ui.components.FollowSwitch
import com.flowpilot.app.ui.components.triggerIcon
import com.flowpilot.app.ui.components.AppIconImage

@Composable
fun HomeScreen(
    vm: AppViewModel,
    detail: (Automation) -> Unit,
    create: () -> Unit,
    settings: () -> Unit,
    permissions: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val rules by vm.automations.collectAsState()
    val engine by vm.engineRunning.collectAsState()
    var selectedRuleIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedRuleIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedRuleIds = emptySet()
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete automations?") },
            text = { Text("Are you sure you want to delete ${selectedRuleIds.size} automation(s)? This action cannot be undone.") },
            confirmButton = {
                TextButton({
                    vm.deleteMany(selectedRuleIds)
                    selectedRuleIds = emptySet()
                    showDeleteConfirmDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showDeleteConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) "${selectedRuleIds.size} selected" else "Automations",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton({ selectedRuleIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    } else {
                        Icon(Icons.Default.Hub, null, modifier = Modifier.padding(start = 16.dp))
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        TextButton({
                            selectedRuleIds = if (selectedRuleIds.size == rules.size) emptySet() else rules.map { it.rule.id }.toSet()
                        }) {
                            Text(if (selectedRuleIds.size == rules.size) "Deselect all" else "Select all")
                        }
                        IconButton({ showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            // Only show FAB when there are rules in the list and not in multi-selection mode
            if (!isSelectionMode && rules.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = create,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New automation", fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Hero Status Card for Automation Engine
            val heroContainerColor by animateColorAsState(
                targetValue = if (engine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(250),
                label = "heroColor",
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(heroContainerColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (engine) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (engine) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (engine) "FlowPilot Active" else "Engine Paused",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val activeCount = rules.count { it.rule.enabled }
                        Text(
                            if (engine) "$activeCount of ${rules.size} automations active"
                            else "Automations will not trigger",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FollowSwitch(engine, { if (it) vm.startEngine() else vm.stopEngine() })
                }
            }

            if (rules.isEmpty()) {
                EmptyState(create)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(rules, key = { it.rule.id }) { item ->
                        val isSelected = item.rule.id in selectedRuleIds
                        RuleCard(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedRuleIds = if (isSelected) selectedRuleIds - item.rule.id else selectedRuleIds + item.rule.id
                                } else {
                                    detail(item.rule)
                                }
                            },
                            onLongClick = {
                                selectedRuleIds = if (isSelected) selectedRuleIds - item.rule.id else selectedRuleIds + item.rule.id
                            },
                            enabled = { vm.setEnabled(item.rule.id, it) },
                            onPermission = permissions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(create: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Bolt, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("No automations yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Text("Create a rule to make your phone react automatically", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(12.dp))
        Button(create, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Create your first rule")
        }
    }
}

@Composable
private fun RuleCard(
    item: AutomationUI,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: (Boolean) -> Unit,
    onPermission: () -> Unit,
) {
    val isRuleEnabled = item.rule.enabled
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isRuleEnabled -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
        },
        animationSpec = tween(150),
        label = "cardColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            isRuleEnabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "cardBorder",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            // Contextual Tinted Trigger Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isRuleEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if ((item.rule.triggerEvent == TriggerEvent.APP_OPENED || item.rule.triggerEvent == TriggerEvent.APP_CLOSED) && item.rule.appPackage.isNotEmpty()) {
                    AppIconImage(
                        packageName = item.rule.appPackage,
                        modifier = Modifier.size(28.dp),
                        fallbackIcon = triggerIcon(item.rule.triggerEvent),
                    )
                } else if (item.rule.triggerEvent == TriggerEvent.NOTIFICATION_RECEIVED && item.rule.notificationAppPackage.isNotEmpty()) {
                    AppIconImage(
                        packageName = item.rule.notificationAppPackage,
                        modifier = Modifier.size(28.dp),
                        fallbackIcon = triggerIcon(item.rule.triggerEvent),
                    )
                } else {
                    Icon(
                        imageVector = triggerIcon(item.rule.triggerEvent),
                        contentDescription = null,
                        tint = if (isRuleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRuleEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.rule.triggerEvent.label} → ${item.rule.actionSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val detail = when (item.rule.triggerEvent) {
                    TriggerEvent.BATTERY_BELOW,
                    TriggerEvent.BATTERY_ABOVE -> "Threshold: ${item.rule.batteryLevel}%"
                    TriggerEvent.WIFI_CONNECTED,
                    TriggerEvent.WIFI_DISCONNECTED -> if (item.rule.wifiSsid.isNotBlank()) "SSID: ${item.rule.wifiSsid}" else "Any Wi-Fi"
                    TriggerEvent.BLUETOOTH_CONNECTED,
                    TriggerEvent.BLUETOOTH_DISCONNECTED -> item.rule.bluetoothDeviceName.ifBlank { item.rule.bluetoothDeviceAddress }
                    TriggerEvent.NFC_TAG_SCANNED -> "Tag ID: ${item.rule.nfcTagId}"
                    TriggerEvent.NOTIFICATION_RECEIVED -> {
                        val app = item.rule.notificationAppName.ifBlank { item.rule.notificationAppPackage }
                        if (item.rule.notificationKeyword.isNotBlank()) "$app · \"${item.rule.notificationKeyword}\"" else app
                    }
                    TriggerEvent.CALL_RINGING,
                    TriggerEvent.CALL_ANSWERED,
                    TriggerEvent.CALL_OUTGOING,
                    TriggerEvent.CALL_ENDED -> "Any call"
                    TriggerEvent.DEVICE_FLIPPED_DOWN,
                    TriggerEvent.DEVICE_FLIPPED_UP -> if (item.rule.flipScreenOffDetection) "Screen on & off" else "Screen on only"
                    TriggerEvent.DEVICE_SHAKE -> "Shake device"
                    TriggerEvent.DEVICE_UNLOCKED -> "Unlock screen"
                    TriggerEvent.LIGHT_BELOW,
                    TriggerEvent.LIGHT_ABOVE -> "Threshold: ${item.rule.lightLux} lx"
                    else -> item.rule.appName.ifBlank { item.rule.appPackage }
                }

                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapabilityPill(item.capability.label, onClick = onPermission)
                    if (item.rule.effectiveCooldownMinutes > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "⏱ ${item.rule.effectiveCooldownMinutes}m cooldown",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (!isSelectionMode) {
                Spacer(Modifier.width(10.dp))
                FollowSwitch(isRuleEnabled, enabled)
            }
        }
    }
}
