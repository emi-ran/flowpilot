@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.flowpilot.app.BuildConfig
import com.flowpilot.app.R
import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.backup.ImportStrategy
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.FollowSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    permissions: () -> Unit,
    history: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by vm.engineEnabled.collectAsState()
    val appLanguage by vm.appLanguage.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    val appTheme by vm.appTheme.collectAsState()
    var showThemePicker by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Backup & Restore states
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportRules by remember { mutableStateOf<List<Automation>>(emptyList()) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<(() -> Unit)?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = vm.exportBackup()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    val count = vm.automations.value.size
                    Toast.makeText(context, context.getString(R.string.backup_export_success, count), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val parseResult = BackupManager.parseImport(content)
                withContext(Dispatchers.Main) {
                    if (parseResult.isSuccess) {
                        val rules = parseResult.getOrNull() ?: emptyList()
                        if (rules.isNotEmpty()) {
                            pendingImportJson = content
                            pendingImportRules = rules
                            showImportDialog = true
                        } else {
                            Toast.makeText(context, context.getString(R.string.backup_import_empty), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val error = parseResult.exceptionOrNull()?.message ?: "Invalid format"
                        Toast.makeText(context, context.getString(R.string.backup_import_error, error), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_import_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val currentLanguageLabel = when (appLanguage.lowercase()) {
        "tr" -> stringResource(R.string.settings_language_tr)
        "en" -> stringResource(R.string.settings_language_en)
        else -> stringResource(R.string.settings_language_system)
    }

    val isDarkTheme = when (appTheme.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val currentThemeLabel = when (appTheme.lowercase()) {
        "light" -> stringResource(R.string.settings_theme_light)
        "dark" -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.settings_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // General Settings Card
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                SettingRowWithSubtitle(
                    label = stringResource(R.string.settings_language),
                    subtitle = currentLanguageLabel,
                    icon = Icons.Default.Language,
                    onClick = { showLanguagePicker = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRowWithSubtitle(
                    label = stringResource(R.string.settings_theme),
                    subtitle = currentThemeLabel,
                    icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    onClick = { showThemePicker = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_startup), Icons.Default.RocketLaunch, engine) {
                    if (it) vm.startEngine() else vm.stopEngine()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_permissions), Icons.Default.AdminPanelSettings, null) { permissions() }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_history), Icons.Default.History, null) { history() }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(stringResource(R.string.settings_about), Icons.Default.Info, null) {
                    showAboutDialog = true
                }
            }

            // Backup, Restore & Sharing Card
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                stringResource(R.string.settings_backup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                SettingRow(
                    label = stringResource(R.string.backup_btn_export),
                    icon = Icons.Default.FileDownload,
                    checked = null,
                ) {
                    pendingExport = { exportLauncher.launch(BackupManager.generateBackupFileName()) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(
                    label = stringResource(R.string.backup_btn_import),
                    icon = Icons.Default.FileUpload,
                    checked = null,
                ) {
                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                SettingRow(
                    label = stringResource(R.string.backup_btn_share_all),
                    icon = Icons.Default.Share,
                    checked = null,
                ) {
                    pendingExport = { vm.shareBackup() }
                }
            }
        }
    }

    pendingExport?.let { export ->
        BackupDisclosureDialog(
            onConfirm = { pendingExport = null; export() },
            onDismiss = { pendingExport = null },
        )
    }

    if (showImportDialog && pendingImportJson != null) {
        ImportStrategyDialog(
            count = pendingImportRules.size,
            onConfirm = { strategy ->
                val json = pendingImportJson ?: return@ImportStrategyDialog
                vm.importAutomations(json, strategy) { result ->
                    if (result.isSuccess) {
                        val importedCount = result.getOrNull() ?: 0
                        Toast.makeText(context, context.getString(R.string.backup_import_success, importedCount), Toast.LENGTH_SHORT).show()
                    } else {
                        val err = result.exceptionOrNull()?.message ?: ""
                        Toast.makeText(context, context.getString(R.string.backup_import_error, err), Toast.LENGTH_LONG).show()
                    }
                }
                showImportDialog = false
                pendingImportJson = null
                pendingImportRules = emptyList()
            },
            onDismiss = {
                showImportDialog = false
                pendingImportJson = null
                pendingImportRules = emptyList()
            },
        )
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

    if (showThemePicker) {
        ThemePickerDialog(
            currentTheme = appTheme,
            onSelectTheme = { selected ->
                vm.setAppTheme(selected)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
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
private fun ThemePickerDialog(
    currentTheme: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val themes = listOf(
        "system" to stringResource(R.string.settings_theme_system),
        "dark" to stringResource(R.string.settings_theme_dark),
        "light" to stringResource(R.string.settings_theme_light),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_select_theme), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { (code, label) ->
                    val isSelected = (currentTheme.isBlank() && code == "system") ||
                        currentTheme.equals(code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTheme(code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectTheme(code) },
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

@Composable
private fun ImportStrategyDialog(
    count: Int,
    onConfirm: (ImportStrategy) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedStrategy by remember { mutableStateOf(ImportStrategy.MERGE) }
    var replaceAcknowledged by remember(selectedStrategy) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.backup_import_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.backup_import_omissions))
                Text(
                    stringResource(R.string.backup_import_dialog_desc, count),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedStrategy == ImportStrategy.MERGE) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStrategy = ImportStrategy.MERGE },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedStrategy == ImportStrategy.MERGE,
                            onClick = { selectedStrategy = ImportStrategy.MERGE },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(R.string.backup_import_strategy_merge),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.backup_import_strategy_merge_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedStrategy == ImportStrategy.REPLACE_ALL) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStrategy = ImportStrategy.REPLACE_ALL },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedStrategy == ImportStrategy.REPLACE_ALL,
                            onClick = { selectedStrategy = ImportStrategy.REPLACE_ALL },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(R.string.backup_import_strategy_replace),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedStrategy == ImportStrategy.REPLACE_ALL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.backup_import_strategy_replace_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (selectedStrategy == ImportStrategy.REPLACE_ALL) {
                    Text(stringResource(R.string.backup_replace_warning), color = MaterialTheme.colorScheme.error)
                    Row(
                        modifier = Modifier.toggleable(
                            value = replaceAcknowledged,
                            role = androidx.compose.ui.semantics.Role.Checkbox,
                            onValueChange = { replaceAcknowledged = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = replaceAcknowledged, onCheckedChange = null)
                        Text(stringResource(R.string.backup_replace_acknowledge))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedStrategy != ImportStrategy.REPLACE_ALL || replaceAcknowledged,
                onClick = {
                    if (selectedStrategy != ImportStrategy.REPLACE_ALL || replaceAcknowledged) {
                        onConfirm(selectedStrategy)
                    }
                },
                colors = if (selectedStrategy == ImportStrategy.REPLACE_ALL) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(stringResource(R.string.backup_btn_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.about_badge_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.about_badge_license),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_app_motto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(16.dp))

                AboutHighlightCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.about_feat_offline_title),
                    desc = stringResource(R.string.about_feat_offline_desc),
                )
                Spacer(Modifier.height(8.dp))
                AboutHighlightCard(
                    icon = Icons.Default.ElectricBolt,
                    title = stringResource(R.string.about_feat_battery_title),
                    desc = stringResource(R.string.about_feat_battery_desc),
                )
                Spacer(Modifier.height(8.dp))
                AboutHighlightCard(
                    icon = Icons.Default.AdminPanelSettings,
                    title = stringResource(R.string.about_feat_shizuku_title),
                    desc = stringResource(R.string.about_feat_shizuku_desc),
                )
                Spacer(Modifier.height(8.dp))
                AboutHighlightCard(
                    icon = Icons.Default.Widgets,
                    title = stringResource(R.string.about_feat_extensible_title),
                    desc = stringResource(R.string.about_feat_extensible_desc),
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/emi-ran/flowpilot"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_btn_github))
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/emi-ran/flowpilot/issues"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_btn_issues))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_close))
            }
        },
    )
}

@Composable
private fun AboutHighlightCard(icon: ImageVector, title: String, desc: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

