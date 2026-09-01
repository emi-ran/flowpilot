@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.ExecutionStatus
import com.flowpilot.app.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    vm: AppViewModel,
    back: () -> Unit,
) {
    val history by vm.executionHistory.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear history") },
            text = { Text("Are you sure you want to clear all automation run history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearHistory()
                        showClearDialog = false
                    },
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Run history", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear history",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (history.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No run history yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Runs triggered by the engine or tested manually will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(history, key = { it.id }) { entry ->
                    HistoryEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: ExecutionHistoryEntry) {
    val dateTimeStr = remember(entry.timestamp) {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", Locale.getDefault())
        Instant.ofEpochMilli(entry.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.ruleName.ifBlank { "Untitled rule" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$dateTimeStr · ${entry.trigger}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(entry.status)
            }

            if (entry.actions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Actions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                entry.actions.forEach { action ->
                    ActionOutcomeRow(action)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ExecutionStatus) {
    val (bgColor, textColor, text) = when (status) {
        ExecutionStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Success",
        )
        ExecutionStatus.PARTIAL -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Partial",
        )
        ExecutionStatus.FAILURE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Failed",
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActionOutcomeRow(action: ActionExecutionRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (action.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (action.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = action.actionLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (action.message.isNotBlank()) {
                Text(
                    text = action.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
