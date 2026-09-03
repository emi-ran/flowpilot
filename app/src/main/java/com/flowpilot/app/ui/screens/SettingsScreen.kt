@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.FollowSwitch

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    permissions: () -> Unit,
    history: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val engine by vm.engineRunning.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Text("Manage your FlowPilot preferences.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                SettingRow("Dark theme", Icons.Default.DarkMode, true) {}
                SettingRow("Run engine on device startup", Icons.Default.RocketLaunch, engine) { if (it) vm.startEngine() else vm.stopEngine() }
                SettingRow("Advanced permissions", Icons.Default.AdminPanelSettings, null) { permissions() }
                SettingRow("Run history", Icons.Default.History, null) { history() }
                SettingRow("About", Icons.Default.Info, null) {}
            }
        }
    }
}

@Composable
fun SettingRow(label: String, icon: ImageVector, checked: Boolean?, action: (Boolean) -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable { action(!checked.orFalse()) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        if (checked != null) FollowSwitch(checked, action) else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Boolean?.orFalse() = this ?: false
