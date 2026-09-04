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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
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
    val appLanguage by vm.appLanguage.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }

    val currentLanguageLabel = when (appLanguage.lowercase()) {
        "tr" -> stringResource(R.string.settings_language_tr)
        "en" -> stringResource(R.string.settings_language_en)
        else -> stringResource(R.string.settings_language_system)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
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
            Text(
                stringResource(R.string.settings_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                SettingRowWithSubtitle(
                    label = stringResource(R.string.settings_language),
                    subtitle = currentLanguageLabel,
                    icon = Icons.Default.Language,
                    onClick = { showLanguagePicker = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_dark_theme), Icons.Default.DarkMode, true) {}
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_startup), Icons.Default.RocketLaunch, engine) {
                    if (it) vm.startEngine() else vm.stopEngine()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_permissions), Icons.Default.AdminPanelSettings, null) { permissions() }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_history), Icons.Default.History, null) { history() }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_about), Icons.Default.Info, null) {}
            }
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = appLanguage,
            onSelectLanguage = { selected ->
                vm.setAppLanguage(selected)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val languages = listOf(
        "system" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_en),
        "tr" to stringResource(R.string.settings_language_tr),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_select_language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                languages.forEach { (code, label) ->
                    val isSelected = currentLanguage.equals(code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLanguage(code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectLanguage(code) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
fun SettingRowWithSubtitle(
    label: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingRow(label: String, icon: ImageVector, checked: Boolean?, action: (Boolean) -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { action(!checked.orFalse()) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        if (checked != null) FollowSwitch(checked, action) else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Boolean?.orFalse() = this ?: false
