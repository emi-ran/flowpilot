@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.components

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.flowpilot.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen modal picker for triggers, grouped by category. */
@Composable
fun TriggerPicker(selected: TriggerEvent, select: (TriggerEvent) -> Unit, onDismiss: () -> Unit) {
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

/** Full-screen modal picker for actions, excluding actions already configured on this rule. */
@Composable
fun ActionPicker(
    selected: ActionType?,
    unavailable: Set<ActionType>,
    select: (ActionType) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceDialog(
        title = "Choose action",
        onDismiss = onDismiss,
        grouped = ActionCategory.entries.map { cat ->
            cat.label to ActionType.entries.filter { it.category == cat && (it == selected || it !in unavailable) }
        },
        selectedLabel = { it.label },
        isSelected = { selected != null && it == selected },
        onSelect = { select(it) },
    )
}

/** Generic full-screen modal list with category headers. */
@Composable
fun <T> ChoiceDialog(
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

@Composable
fun SelectionRow(title: String, sub: String, click: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
        shape = RoundedCornerShape(18.dp),
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

data class AppDisplayItem(
    val packageName: String,
    val label: String,
    val appInfo: ApplicationInfo,
)

@Composable
fun AppPicker(select: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppDisplayItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            val launchable = installed
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { info ->
                    AppDisplayItem(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        appInfo = info,
                    )
                }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                apps = launchable
                isLoading = false
            }
        }
    }

    val filtered = remember(query, apps) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list
        else list.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Choose an app", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.Close, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Loading applications...",
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
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app) { select(app.packageName, app.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppDisplayItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(app.packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val icon = app.appInfo.loadIcon(context.packageManager)
                val bmp = icon.toBitmap().asImageBitmap()
                withContext(Dispatchers.Main) {
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
