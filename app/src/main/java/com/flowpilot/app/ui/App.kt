@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.flowpilot.app.ui

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.flowpilot.app.actions.ShizukuPermissionBridge
import com.flowpilot.app.data.model.*
import com.flowpilot.app.permission.CapabilityManager
import com.flowpilot.app.permission.ShizukuState
import com.flowpilot.app.ui.components.*

private enum class Page { HOME, SETTINGS, CREATE, PERMISSIONS, DETAIL }

@Composable
fun FlowPilotRoot(vm: AppViewModel = viewModel()) {
    var page by remember { mutableStateOf(Page.HOME) }
    var selected by remember { mutableStateOf<Automation?>(null) }
    BackHandler(enabled = page != Page.HOME) { page = Page.HOME }
    // Refresh all permission states every time the app returns to foreground
    // (e.g. after granting Usage Access in system Settings).
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    Scaffold(
        bottomBar = { if (page == Page.HOME || page == Page.SETTINGS) BottomBar(page) { page = it } },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            Crossfade(
                targetState = page,
                animationSpec = tween(150),
                label = "PageNavigation",
            ) { currentPage ->
                when (currentPage) {
                    Page.HOME -> HomeScreen(vm, { selected = it; page = Page.DETAIL }, { page = Page.CREATE }, { page = Page.SETTINGS }, { page = Page.PERMISSIONS })
                    Page.SETTINGS -> SettingsScreen(vm) { page = Page.PERMISSIONS }
                    Page.CREATE -> CreateScreen(vm) { page = Page.HOME }
                    Page.PERMISSIONS -> PermissionsScreen(vm) { page = Page.SETTINGS }
                    Page.DETAIL -> selected?.let { DetailScreen(vm, it) { page = Page.HOME } }
                }
            }
        }
    }
}

@Composable private fun BottomBar(page: Page, go: (Page) -> Unit) {
    NavigationBar {
        NavigationBarItem(page == Page.HOME, { go(Page.HOME) }, icon = { Icon(Icons.Default.Bolt, null) }, label = { Text("Automations") })
        NavigationBarItem(page == Page.SETTINGS, { go(Page.SETTINGS) }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun HomeScreen(
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create automation")
            }
        }
    }
}

@Composable private fun EmptyState(create: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Bolt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("No automations yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text("Create a rule to make your phone react automatically", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        OutlinedButton(create, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text("Create your first rule") }
    }
}

@Composable private fun RuleCard(
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
                Text("${item.rule.triggerEvent.label} → ${item.rule.action.label}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(item.rule.appName.ifBlank { item.rule.appPackage }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                CapabilityPill(item.capability.label, Modifier.padding(top = 6.dp), onClick = onPermission)
            }
            if (!isSelectionMode) {
                FollowSwitch(item.rule.enabled, enabled)
            }
        }
    }
}

@Composable private fun SettingsScreen(vm: AppViewModel, permissions: () -> Unit) {
    val engine by vm.engineRunning.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("Manage your FlowPilot preferences.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                SettingRow("Dark theme", Icons.Default.DarkMode, true) {}
                SettingRow("Run on startup", Icons.Default.RocketLaunch, engine) { if (it) vm.startEngine() else vm.stopEngine() }
                SettingRow("Advanced permissions", Icons.Default.AdminPanelSettings, null) { permissions() }
                SettingRow("Backup / Restore", Icons.Default.CloudSync, null) {}
                SettingRow("About", Icons.Default.Info, null) {}
            }
        }
    }
}

@Composable private fun SettingRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean?, action: (Boolean) -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable { action(!checked.orFalse()) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        if (checked != null) FollowSwitch(checked, action) else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Boolean?.orFalse() = this ?: false

@Composable private fun CreateScreen(vm: AppViewModel, done: () -> Unit) {
    var event by remember { mutableStateOf(TriggerEvent.APP_OPENED) }
    var action by remember { mutableStateOf(ActionType.NFC_ON) }
    var pkg by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    var showTriggers by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    if (showApps) AppPicker({ p, n -> pkg = p; appName = n; showApps = false }) { showApps = false }
    if (showTriggers) TriggerPicker(event, { event = it; showTriggers = false }) { showTriggers = false }
    if (showActions) ActionPicker(action, { action = it; showActions = false }) { showActions = false }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Create automation", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("WHEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            SelectionRow("Trigger", event.label) { showTriggers = true }
            Spacer(Modifier.height(10.dp))
            SelectionRow(if (pkg.isEmpty()) "App" else appName, if (pkg.isEmpty()) "Choose an app" else pkg) { showApps = true }
            Text("DO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            SelectionRow("Action", action.label) { showActions = true }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                label = { Text("Name (optional)") },
                singleLine = true,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(done, Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button({ vm.addRule(name, event, pkg, appName, action); done() }, Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), enabled = pkg.isNotEmpty()) { Text("Save") }
            }
        }
    }
}

/** Full-screen modal picker for triggers, grouped by category. */
@Composable private fun TriggerPicker(selected: TriggerEvent, select: (TriggerEvent) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = "Choose trigger",
        onDismiss = onDismiss,
        grouped = TriggerCategory.entries.map { cat ->
            cat.label to TriggerEvent.entries.filter { it.category == cat }
        },
        selectedLabel = { it.label },
        isSelected = { it == selected },
        onSelect = { select(it) },
    )
}

/** Full-screen modal picker for actions, grouped by category (NFC / Battery / System). */
@Composable private fun ActionPicker(selected: ActionType, select: (ActionType) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = "Choose action",
        onDismiss = onDismiss,
        grouped = ActionCategory.entries.map { cat ->
            cat.label to ActionType.entries.filter { it.category == cat }
        },
        selectedLabel = { it.label },
        isSelected = { it == selected },
        onSelect = { select(it) },
    )
}

/** Generic full-screen modal list with category headers. */
@Composable private fun <T> ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    grouped: List<Pair<String, List<T>>>,
    selectedLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.Close, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    grouped.forEach { (cat, items) ->
                        item(key = "h-$cat") {
                            Text(cat.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
                        }
                        items(items, key = { selectedLabel(it) }) { option ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 14.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedLabel(option), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (isSelected(option)) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SelectionRow(title: String, sub: String, click: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class AppDisplayItem(
    val packageName: String,
    val label: String,
    val appInfo: ApplicationInfo,
)

@Composable private fun AppPicker(select: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppDisplayItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            val launchable = installed
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { AppDisplayItem(it.packageName, pm.getApplicationLabel(it).toString(), it) }
                .sortedBy { it.label.lowercase() }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                apps = launchable
                isLoading = false
            }
        }
    }

    val filtered = remember(query, apps) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list
        else list.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                TopAppBar(
                    title = { Text("Choose app", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.Close, null) } },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    label = { Text("Search") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    enabled = !isLoading,
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Loading apps...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No apps found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app) { select(app.packageName, app.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AppRow(app: AppDisplayItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(app.packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(app.packageName) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val icon = app.appInfo.loadIcon(context.packageManager)
                val bmp = icon.toBitmap().asImageBitmap()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    bitmap = bmp
                }
            } catch (_: Throwable) {}
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(
                Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun DialogWindow(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
}

@Composable private fun PermissionsScreen(vm: AppViewModel, back: () -> Unit) {
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
                    ShizukuState.NOT_INSTALLED -> context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/download/")))
                    ShizukuState.NOT_RUNNING -> {
                        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.xyz.manager")
                        if (launch != null) context.startActivity(launch)
                        else context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/download/")))
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

@Composable private fun PermissionCard(title: String, desc: String, granted: Boolean, pillText: String = "Permission required", action: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                CapabilityPill(if (granted) "Available" else pillText)
            }
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (!granted && action != null) Button(action, Modifier.align(Alignment.End), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text("Setup") }
        }
    }
}

@Composable private fun DetailScreen(vm: AppViewModel, initialRule: Automation, back: () -> Unit) {
    var event by remember(initialRule.id) { mutableStateOf(initialRule.triggerEvent) }
    var action by remember(initialRule.id) { mutableStateOf(initialRule.action) }
    var pkg by remember(initialRule.id) { mutableStateOf(initialRule.appPackage) }
    var appName by remember(initialRule.id) { mutableStateOf(initialRule.appName) }
    var name by remember(initialRule.id) { mutableStateOf(initialRule.name) }
    var showApps by remember { mutableStateOf(false) }
    var showTriggers by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showApps) AppPicker({ p, n -> pkg = p; appName = n; showApps = false }) { showApps = false }
    if (showTriggers) TriggerPicker(event, { event = it; showTriggers = false }) { showTriggers = false }
    if (showActions) ActionPicker(action, { action = it; showActions = false }) { showActions = false }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete automation?") },
            text = { Text("Are you sure you want to delete '${initialRule.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton({
                    showDeleteConfirm = false
                    vm.delete(initialRule.id)
                    back()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Edit automation", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton({ showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("WHEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            SelectionRow("Trigger", event.label) { showTriggers = true }
            Spacer(Modifier.height(10.dp))
            SelectionRow(if (pkg.isEmpty()) "App" else appName, if (pkg.isEmpty()) "Choose an app" else pkg) { showApps = true }

            Text("DO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            SelectionRow("Action", action.label) { showActions = true }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                label = { Text("Name") },
                singleLine = true,
            )

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(back, Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        val finalName = name.ifBlank { "${appName.ifBlank { pkg }} · ${action.label}" }
                        vm.updateRule(
                            initialRule.copy(
                                name = finalName,
                                triggerEvent = event,
                                appPackage = pkg,
                                appName = appName,
                                action = action,
                            )
                        )
                        back()
                    },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    enabled = pkg.isNotEmpty(),
                ) {
                    Text("Save changes")
                }
            }
        }
    }
}