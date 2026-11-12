@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.actions.ActionDiagnosticReason
import com.flowpilot.app.actions.ActionTemplateDiagnostic
import com.flowpilot.app.actions.ActionTemplateField
import com.flowpilot.app.actions.TemplateValueState
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.ExecutionStatus
import com.flowpilot.app.data.model.resolvedResultCode
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.util.labelRes
import com.flowpilot.app.ui.util.localizedLabel
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
    var statusFilter by remember { mutableStateOf<ExecutionStatus?>(null) }
    var failuresOnly by remember { mutableStateOf(false) }
    var ruleFilter by remember { mutableStateOf<String?>(null) }
    var statusMenu by remember { mutableStateOf(false) }
    var ruleMenu by remember { mutableStateOf(false) }
    val rules = history.distinctBy { it.ruleId }
    val filteredHistory = history.filter {
        (ruleFilter == null || it.ruleId == ruleFilter) &&
            (!failuresOnly || it.status != ExecutionStatus.SUCCESS) &&
            (statusFilter == null || it.status == statusFilter)
    }
    val historyGroups = remember(filteredHistory) { groupRepeatedFailures(filteredHistory) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearHistory()
                        showClearDialog = false
                    },
                ) {
                    Text(stringResource(R.string.history_btn_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.history_clear_title),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (history.isNotEmpty()) {
            val anyFilterActive = failuresOnly || statusFilter != null || ruleFilter != null
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    val statusLabel = when {
                        failuresOnly -> stringResource(R.string.history_filter_errors)
                        statusFilter != null -> stringResource(statusFilter!!.labelRes)
                        else -> stringResource(R.string.history_filter_all_results)
                    }
                    HistoryFilterDropdown(
                        label = statusLabel,
                        active = failuresOnly || statusFilter != null,
                        contentDescription = stringResource(R.string.history_filter_status_cd),
                        onClick = { statusMenu = true },
                    )
                    HistoryFilterMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        HistoryFilterMenuItem(
                            label = stringResource(R.string.history_filter_all_results),
                            selected = !failuresOnly && statusFilter == null,
                        ) { statusFilter = null; failuresOnly = false; statusMenu = false }
                        HistoryFilterMenuItem(
                            label = stringResource(R.string.history_filter_errors),
                            selected = failuresOnly,
                        ) { statusFilter = null; failuresOnly = true; statusMenu = false }
                        ExecutionStatus.entries.forEach { status ->
                            HistoryFilterMenuItem(
                                label = stringResource(status.labelRes),
                                selected = statusFilter == status,
                            ) { statusFilter = status; failuresOnly = false; statusMenu = false }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    val selectedRule = rules.firstOrNull { it.ruleId == ruleFilter }
                    HistoryFilterDropdown(
                        label = selectedRule?.ruleName?.ifBlank { stringResource(R.string.history_untitled_rule) }
                            ?: stringResource(R.string.history_filter_all_rules),
                        active = ruleFilter != null,
                        contentDescription = stringResource(R.string.history_filter_rule_cd),
                        onClick = { ruleMenu = true },
                    )
                    HistoryFilterMenu(expanded = ruleMenu, onDismissRequest = { ruleMenu = false }) {
                        HistoryFilterMenuItem(
                            label = stringResource(R.string.history_filter_all_rules),
                            selected = ruleFilter == null,
                        ) { ruleFilter = null; ruleMenu = false }
                        rules.forEach { rule ->
                            HistoryFilterMenuItem(
                                label = rule.ruleName.ifBlank { stringResource(R.string.history_untitled_rule) },
                                selected = rule.ruleId == ruleFilter,
                            ) { ruleFilter = rule.ruleId; ruleMenu = false }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = anyFilterActive,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            statusFilter = null
                            failuresOnly = false
                            ruleFilter = null
                        },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.history_filter_clear),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

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
                        stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.history_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else if (filteredHistory.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_filter_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(historyGroups, key = { it.entries.first().id }) { group ->
                    if (group.entries.size == 1) HistoryEntryCard(group.entries.first())
                    else HistoryFailureGroupCard(group)
                }
            }
        }
    }
}

private data class FailureGroupKey(
    val ruleId: String,
    val actionType: com.flowpilot.app.data.model.ActionType,
    val reason: ActionDiagnosticReason,
)

private data class HistoryGroup(
    val key: FailureGroupKey?,
    val entries: List<ExecutionHistoryEntry>,
)

private fun groupRepeatedFailures(entries: List<ExecutionHistoryEntry>): List<HistoryGroup> {
    val groups = mutableListOf<MutableList<ExecutionHistoryEntry>>()
    val groupedIndices = mutableMapOf<FailureGroupKey, Int>()
    val keys = mutableListOf<FailureGroupKey?>()
    entries.forEach { entry ->
        // A record with multiple failed actions cannot be attributed to one cause safely.
        val failure = entry.actions.filterNot { it.success }.singleOrNull()
        val key = if (entry.status != ExecutionStatus.SUCCESS && entry.ruleId.isNotBlank() && failure?.diagnostic != null) {
            FailureGroupKey(entry.ruleId, failure.actionType, failure.diagnostic.reason)
        } else null
        val index = key?.let { groupedIndices[it] }
        if (index != null) {
            groups[index].add(entry)
        } else {
            if (key != null) groupedIndices[key] = groups.size
            groups.add(mutableListOf(entry))
            keys.add(key)
        }
    }
    return groups.mapIndexed { index, group -> HistoryGroup(keys[index], group) }
}

@Composable
private fun HistoryFailureGroupCard(group: HistoryGroup) {
    val groupKey = requireNotNull(group.key)
    var expanded by remember(group.entries.first().id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                group.entries.first().ruleName.ifBlank { stringResource(R.string.history_untitled_rule) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(groupKey.actionType.localizedLabel(), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(groupKey.reason.labelRes()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.history_group_occurrences, group.entries.size),
                style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.history_group_hide_records else R.string.history_group_show_records))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.entries.forEach { entry -> key(entry.id) { HistoryEntryCard(entry) } }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: ExecutionHistoryEntry) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
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
                        text = entry.ruleName.ifBlank { stringResource(R.string.history_untitled_rule) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$dateTimeStr · ${entry.trigger}" + (entry.triggerSourceIndex?.let {
                            " · " + stringResource(R.string.trigger_source_index, it)
                        } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(entry.status)
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.history_hide_details else R.string.history_show_details))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.history_section_actions), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.actions.isEmpty()) {
                        Text(stringResource(R.string.history_no_actions), style = MaterialTheme.typography.bodySmall)
                    } else {
                        entry.actions.forEachIndexed { index, action -> ActionOutcomeRow(action, index + 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ExecutionStatus) {
    val (bgColor, textColor) = when (status) {
        ExecutionStatus.SUCCESS -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ExecutionStatus.PARTIAL -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ExecutionStatus.FAILURE -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = stringResource(status.labelRes),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActionOutcomeRow(action: ActionExecutionRecord, index: Int) {
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
                text = stringResource(R.string.history_action_number, index, action.actionType.localizedLabel()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val resultText = action.localizedResultText()
            if (resultText.isNotBlank()) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val diagnostic = action.diagnostic
            if (diagnostic != null) {
                Text(
                    text = stringResource(diagnostic.reason.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!action.success && action.templateDiagnostics.isEmpty() &&
                (action.resolvedResultCode() == null || action.resolvedResultCode() == ActionResultCode.COMPLETED)
            ) {
                Text(
                    text = stringResource(R.string.history_diagnostic_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            diagnostic?.expectedLength?.let { length ->
                Text(stringResource(R.string.history_expected_length, length), style = MaterialTheme.typography.bodySmall)
            }
            diagnostic?.observedLength?.let { length ->
                Text(stringResource(R.string.history_observed_length, length), style = MaterialTheme.typography.bodySmall)
            }
            action.templateDiagnostics.forEach { TemplateDiagnosticDetails(it) }
        }
    }
}

@Composable
private fun ActionExecutionRecord.localizedResultText(): String = when (resolvedResultCode()) {
    ActionResultCode.COMPLETED -> stringResource(R.string.history_result_completed)
    ActionResultCode.STATE_ENABLED -> stringResource(R.string.history_result_enabled)
    ActionResultCode.STATE_DISABLED -> stringResource(R.string.history_result_disabled)
    ActionResultCode.NOTIFICATION_POSTED -> stringResource(R.string.history_result_notification_posted)
    ActionResultCode.SMS_SENT -> stringResource(R.string.history_result_sms_sent_safe)
    ActionResultCode.PERMISSION_REQUIRED -> stringResource(R.string.history_result_permission_required)
    ActionResultCode.EXECUTION_CANCELLED -> stringResource(R.string.history_result_execution_cancelled)
    null -> stringResource(if (success) R.string.history_result_completed else R.string.history_result_failed)
}

private fun ActionDiagnosticReason.labelRes(): Int = when (this) {
    ActionDiagnosticReason.MALFORMED_LINK -> R.string.history_diagnostic_malformed_link
    ActionDiagnosticReason.INVALID_LINK_COMPONENTS -> R.string.history_diagnostic_link_components
    ActionDiagnosticReason.UNSUPPORTED_LINK_HOST -> R.string.history_diagnostic_link_host
    ActionDiagnosticReason.INVALID_LINK_PATH -> R.string.history_diagnostic_link_path
    ActionDiagnosticReason.MISSING_VIDEO_ID -> R.string.history_diagnostic_missing_video_id
    ActionDiagnosticReason.MULTIPLE_VIDEO_IDS -> R.string.history_diagnostic_multiple_video_ids
    ActionDiagnosticReason.INVALID_VIDEO_ID_LENGTH -> R.string.history_diagnostic_video_id_length
    ActionDiagnosticReason.INVALID_VIDEO_ID_CHARACTERS -> R.string.history_diagnostic_video_id_characters
    ActionDiagnosticReason.SHIZUKU_NOT_RUNNING -> R.string.history_diagnostic_shizuku_not_running
    ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED -> R.string.history_diagnostic_shizuku_permission_required
    ActionDiagnosticReason.BLUETOOTH_PERMISSION_REQUIRED -> R.string.history_diagnostic_bluetooth_permission_required
    ActionDiagnosticReason.SETTINGS_PERMISSION_REQUIRED -> R.string.history_diagnostic_settings_permission_required
    ActionDiagnosticReason.DND_PERMISSION_REQUIRED -> R.string.history_diagnostic_dnd_permission_required
    ActionDiagnosticReason.SYSTEM_COMMAND_FAILED -> R.string.history_diagnostic_system_command_failed
    ActionDiagnosticReason.STATE_UNAVAILABLE -> R.string.history_diagnostic_state_unavailable
    ActionDiagnosticReason.STATE_MISMATCH -> R.string.history_diagnostic_state_mismatch
    ActionDiagnosticReason.TARGET_APP_UNAVAILABLE -> R.string.history_diagnostic_target_app_unavailable
    ActionDiagnosticReason.INVALID_INPUT -> R.string.history_diagnostic_invalid_input
    ActionDiagnosticReason.HANDLER_UNAVAILABLE -> R.string.history_diagnostic_handler_unavailable
    ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED -> R.string.history_diagnostic_notification_permission_required
    ActionDiagnosticReason.PHONE_PERMISSION_REQUIRED -> R.string.history_diagnostic_phone_permission_required
    ActionDiagnosticReason.SMS_PERMISSION_REQUIRED -> R.string.history_diagnostic_sms_permission_required
    ActionDiagnosticReason.MEDIA_SESSION_UNAVAILABLE -> R.string.history_diagnostic_media_session_unavailable
    ActionDiagnosticReason.NETWORK_TIMEOUT -> R.string.history_diagnostic_network_timeout
    ActionDiagnosticReason.NETWORK_FAILURE -> R.string.history_diagnostic_network_failure
}

@Composable
private fun TemplateDiagnosticDetails(diagnostic: ActionTemplateDiagnostic) {
    Text(stringResource(R.string.history_template_field, stringResource(diagnostic.field.labelRes())), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.history_template_notification_text, stringResource(diagnostic.notificationText.labelRes())), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.history_template_notification_big_text, stringResource(diagnostic.notificationBigText.labelRes())), style = MaterialTheme.typography.bodySmall)
    if (diagnostic.hasParseErrors) Text(stringResource(R.string.history_template_parse_error), style = MaterialTheme.typography.bodySmall)
    if (diagnostic.hasUnknownVariables) Text(stringResource(R.string.history_template_unknown_variable), style = MaterialTheme.typography.bodySmall)
    if (diagnostic.analysisFailed) Text(stringResource(R.string.history_template_analysis_failed), style = MaterialTheme.typography.bodySmall)
    if (diagnostic.renderedEmpty) Text(stringResource(R.string.history_template_rendered_empty), style = MaterialTheme.typography.bodySmall)
}

private fun TemplateValueState.labelRes(): Int = when (this) {
    TemplateValueState.MISSING -> R.string.history_template_value_missing
    TemplateValueState.EMPTY -> R.string.history_template_value_empty
    TemplateValueState.PRESENT -> R.string.history_template_value_present
}

private fun ActionTemplateField.labelRes(): Int = when (this) {
    ActionTemplateField.NOTIFICATION_TITLE -> R.string.history_template_field_notification_title
    ActionTemplateField.NOTIFICATION_BODY -> R.string.history_template_field_notification_body
    ActionTemplateField.MUSIC_QUERY -> R.string.history_template_field_music_query
    ActionTemplateField.MUSIC_LINK -> R.string.history_template_field_music_link
    ActionTemplateField.URL -> R.string.history_template_field_url
    ActionTemplateField.TTS_TEXT -> R.string.history_template_field_tts_text
    ActionTemplateField.ALARM_MESSAGE -> R.string.history_template_field_alarm_message
    ActionTemplateField.TIMER_MESSAGE -> R.string.history_template_field_timer_message
    ActionTemplateField.WEBHOOK_URL -> R.string.history_template_field_webhook_url
    ActionTemplateField.WEBHOOK_HEADERS -> R.string.history_template_field_webhook_headers
    ActionTemplateField.WEBHOOK_BODY -> R.string.history_template_field_webhook_body
    ActionTemplateField.PHONE_NUMBER -> R.string.history_template_field_phone_number
    ActionTemplateField.SMS_RECIPIENT -> R.string.history_template_field_sms_recipient
    ActionTemplateField.SMS_MESSAGE -> R.string.history_template_field_sms_message
}

/** Filter trigger that reads as a dropdown (chevron + selected state) instead of a plain button. */
@Composable
private fun HistoryFilterDropdown(
    label: String,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "historyFilterContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        label = "historyFilterContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outlineVariant,
        label = "historyFilterBorder",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HistoryFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
private fun HistoryFilterMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = MenuDefaults.itemColors(
            textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
