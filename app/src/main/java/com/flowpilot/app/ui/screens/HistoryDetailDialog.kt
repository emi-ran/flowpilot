@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowpilot.app.R
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.ExecutionStatus
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.isUnverifiedRequest
import com.flowpilot.app.data.model.resolvedHttpFailureStatus
import com.flowpilot.app.diagnostics.ReasonCode
import com.flowpilot.app.ui.util.RunReportClipboard
import com.flowpilot.app.ui.util.explanationRes
import com.flowpilot.app.ui.util.fixRes
import com.flowpilot.app.ui.util.labelRes
import com.flowpilot.app.ui.util.localizedLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen detail view for one run: status meaning, per-action attempt, result, reason and fix. */
@Composable
fun HistoryDetailDialog(entry: ExecutionHistoryEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_detail_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = { RunReportClipboard.copy(context, entry) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.history_btn_copy_report))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OverviewSection(entry)
                    StatusSection(entry)
                    Text(stringResource(R.string.history_detail_section_actions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (entry.actions.isEmpty()) {
                        Text(stringResource(R.string.history_no_actions), style = MaterialTheme.typography.bodyMedium)
                    }
                    entry.actions.forEachIndexed { i, action -> ActionDetailCard(action, i + 1) }
                    Text(
                        stringResource(R.string.history_detail_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", Locale.getDefault()))

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "$ms ms" else String.format(Locale.getDefault(), "%.1f s", ms / 1000.0)

@Composable
private fun OverviewSection(entry: ExecutionHistoryEntry) {
    val unknown = stringResource(R.string.history_detail_unknown)
    val triggerName = TriggerEvent.entries.firstOrNull { it.name == entry.trigger }
        ?.let { stringResource(it.labelRes) } ?: entry.trigger
    val triggerText = triggerName + (entry.triggerSourceIndex?.let { " · " + stringResource(R.string.trigger_source_index, it) } ?: "")
    val conditions = entry.conditionTypes.mapNotNull { name ->
        com.flowpilot.app.data.model.ConditionType.entries.firstOrNull { it.name == name }
    }.map { stringResource(it.labelRes) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.ruleName.ifBlank { stringResource(R.string.history_untitled_rule) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (entry.startedAt != null) {
                Text(stringResource(R.string.history_detail_started, formatTime(entry.startedAt)), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.history_detail_finished, formatTime(entry.timestamp)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.history_detail_duration, entry.durationMs?.let(::formatDuration) ?: unknown), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.history_detail_trigger, triggerText), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.history_detail_conditions, conditions.joinToString(", ").ifEmpty { stringResource(R.string.history_detail_no_conditions) }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusSection(entry: ExecutionHistoryEntry) {
    val unverified = entry.status == ExecutionStatus.SUCCESS && entry.actions.any { it.isUnverifiedRequest() }
    val title = if (unverified) stringResource(R.string.history_result_unverified) else stringResource(entry.status.labelRes)
    val body = when {
        unverified -> R.string.history_detail_status_unverified
        entry.status == ExecutionStatus.SUCCESS -> R.string.history_detail_status_success
        entry.status == ExecutionStatus.PARTIAL -> R.string.history_detail_status_partial
        else -> R.string.history_detail_status_failure
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_detail_section_status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActionDetailCard(action: ActionExecutionRecord, index: Int) {
    val reason: ReasonCode? = action.reason
    val showReason = reason != null && (!action.success || reason.isInformational)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.history_detail_action_header, index, action.actionType.localizedLabel()),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                val label = when {
                    action.isUnverifiedRequest() -> stringResource(R.string.history_result_unverified)
                    action.success -> stringResource(ExecutionStatus.SUCCESS.labelRes)
                    else -> stringResource(R.string.history_result_failed)
                }
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if (action.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            action.delaySeconds?.let {
                Text(
                    stringResource(R.string.history_detail_delay, it),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action.attempted?.let {
                Text(stringResource(R.string.history_detail_attempted, it), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.history_detail_observed, action.localizedResultText()), style = MaterialTheme.typography.bodySmall)
            action.resolvedHttpFailureStatus()?.let {
                Text(stringResource(R.string.history_http_status, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (showReason && reason != null) {
                Text(stringResource(R.string.history_detail_reason, stringResource(reason.explanationRes)), style = MaterialTheme.typography.bodySmall,
                    color = if (reason.isInformational) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.history_detail_fix, stringResource(reason.fixRes)), style = MaterialTheme.typography.bodySmall)
                val technical = listOfNotNull(reason.name, action.detail).joinToString(" · ")
                Text(stringResource(R.string.history_detail_technical, technical), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!action.success) {
                Text(stringResource(R.string.history_detail_no_reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action.diagnostic?.let { d ->
                Text(stringResource(d.reason.labelRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (action.reasonCode == null && action.attempted == null && action.delaySeconds == null) {
                Text(stringResource(R.string.history_detail_older_record), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
