@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.ActionPicker
import com.flowpilot.app.ui.components.AppPicker
import com.flowpilot.app.ui.components.SelectionRow
import com.flowpilot.app.ui.components.TriggerPicker

@Composable
fun CreateScreen(vm: AppViewModel, done: () -> Unit) {
    var event by remember { mutableStateOf(TriggerEvent.APP_OPENED) }
    var actions by remember { mutableStateOf(listOf(ActionType.NFC_ON)) }
    var editingActionIndex by remember { mutableStateOf<Int?>(null) }
    var pkg by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    var showTriggers by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    if (showApps) AppPicker({ p, n -> pkg = p; appName = n; showApps = false }) { showApps = false }
    if (showTriggers) TriggerPicker(event, { event = it; showTriggers = false }) { showTriggers = false }
    if (showActions) {
        val currentSelected = editingActionIndex?.let { if (it < actions.size) actions[it] else null }
        ActionPicker(
            selected = currentSelected,
            select = { chosen ->
                val idx = editingActionIndex
                if (idx != null && idx < actions.size) {
                    actions = actions.mapIndexed { i, a -> if (i == idx) chosen else a }
                } else {
                    actions = actions + chosen
                }
                showActions = false
                editingActionIndex = null
            },
            onDismiss = {
                showActions = false
                editingActionIndex = null
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Create automation", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Text("WHEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            SelectionRow("Trigger", event.label) { showTriggers = true }
            Spacer(Modifier.height(10.dp))
            SelectionRow(if (pkg.isEmpty()) "App" else appName, if (pkg.isEmpty()) "Choose an app" else pkg) { showApps = true }

            Text(
                "DO (${actions.size} action${if (actions.size > 1) "s" else ""})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { index, act ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f).clickable {
                                    editingActionIndex = index
                                    showActions = true
                                }
                            ) {
                                Text("Action ${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(2.dp))
                                Text(act.label, style = MaterialTheme.typography.titleMedium)
                            }
                            if (actions.size > 1) {
                                IconButton(
                                    onClick = {
                                        actions = actions.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Close, "Remove action", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    editingActionIndex = null
                    showActions = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add action")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                label = { Text("Name (optional)") },
                singleLine = true,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(done, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        vm.addRule(name, event, pkg, appName, actions)
                        done()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = pkg.isNotEmpty() && actions.isNotEmpty(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
