@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowpilot.app.R
import com.flowpilot.app.actions.VariableCatalog
import com.flowpilot.app.data.model.TriggerEvent
import java.util.Locale

/**
 * Searchable, grouped variable picker. Selecting a row inserts the token through [onInsert] and
 * closes the dialog; focus/scroll behavior and motion stay conservative on purpose.
 */
@Composable
fun VariablePickerDialog(
    triggers: Collection<TriggerEvent>,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(triggers) { VariableCatalog.availableFor(triggers) }
    val descriptions = groups.associate { group ->
        group.id to group.specs.associate { spec -> spec.name to stringResource(spec.descriptionRes) }
    }
    val filteredGroups = remember(groups, descriptions, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        groups.mapNotNull { group ->
            val matches = group.specs.filter { spec ->
                needle.isBlank() ||
                    spec.name.lowercase(Locale.ROOT).contains(needle) ||
                    spec.token.lowercase(Locale.ROOT).contains(needle) ||
                    spec.filterExample.lowercase(Locale.ROOT).contains(needle) ||
                    descriptions[group.id]?.get(spec.name)?.lowercase(Locale.ROOT)?.contains(needle) == true
            }
            if (matches.isEmpty()) null else group to matches
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.variable_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.variable_picker_close))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    label = { Text(stringResource(R.string.variable_picker_search_label)) },
                    placeholder = { Text(stringResource(R.string.variable_picker_search_placeholder)) },
                    singleLine = true,
                )
                if (filteredGroups.isEmpty()) {
                    Text(
                        stringResource(R.string.variable_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                    ) {
                        filteredGroups.forEach { (group, specs) ->
                            item(key = "group_${group.id}") {
                                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 2.dp)) {
                                    Text(
                                        stringResource(group.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        if (group.isAlwaysAvailable) {
                                            stringResource(R.string.variable_group_always)
                                        } else {
                                            stringResource(
                                                R.string.variable_group_only_when,
                                                stringResource(group.titleRes),
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(specs, key = { "${group.id}_${it.name}" }) { spec ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onInsert(spec.token)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        spec.token,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        descriptions[group.id]?.get(spec.name).orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        spec.filterExample,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
