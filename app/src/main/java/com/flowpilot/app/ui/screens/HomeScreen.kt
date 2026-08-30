@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.flowpilot.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
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

@Composable
fun HomeScreen(
    vm: AppViewModel,
    detail: (Automation) -> Unit,
    create: () -> Unit,
    settings: () -> Unit,
    permissions: () -> Unit,
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

    Column(Modifier.fillMaxSize()) {
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

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("Make your phone react automatically", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Automation engine", style = MaterialTheme.typography.titleMedium)
                FollowSwitch(engine, { if (it) vm.startEngine() else vm.stopEngine() })
            }
            Spacer(Modifier.height(12.dp))

            if (rules.isEmpty()) {
                EmptyState(create)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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

            Button(
                onClick = create,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create automation")
            }
        }
    }
}

@Composable
private fun EmptyState(create: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Bolt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("No automations yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text("Create a rule to make your phone react automatically", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        OutlinedButton(create, shape = RoundedCornerShape(16.dp)) { Text("Create your first rule") }
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
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(150),
        label = "cardColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Transparent,
        animationSpec = tween(150),
        label = "cardBorder",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                borderColor,
                shape = CardDefaults.shape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(containerColor),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Icon(
                if (item.rule.triggerEvent == TriggerEvent.APP_OPENED) Icons.Default.Apps else Icons.Default.Close,
                null,
                Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(item.rule.name, style = MaterialTheme.typography.titleMedium)
                Text("${item.rule.triggerEvent.label} → ${item.rule.actionSummary}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(item.rule.appName.ifBlank { item.rule.appPackage }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                CapabilityPill(item.capability.label, Modifier.padding(top = 6.dp), onClick = onPermission)
            }
            if (!isSelectionMode) {
                FollowSwitch(item.rule.enabled, enabled)
            }
        }
    }
}
