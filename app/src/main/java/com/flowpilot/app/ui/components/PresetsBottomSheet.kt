@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpilot.app.R
import com.flowpilot.app.data.model.AutomationPreset
import com.flowpilot.app.data.model.AutomationPresets
import com.flowpilot.app.data.model.PresetCategory
import com.flowpilot.app.data.model.normalizedTriggers
import com.flowpilot.app.ui.util.localizedLabel

/**
 * Muted per-category accent. Used only for the icon tile, the category dot and its label so
 * cards stay neutral and the single filled action button remains the strongest element.
 */
private val PresetCategory.darkAccent: Color
    get() = when (this) {
        PresetCategory.ROUTINE -> Color(0xFF8FB4EC)
        PresetCategory.BATTERY -> Color(0xFF7FC59A)
        PresetCategory.GESTURES -> Color(0xFFE0B463)
        PresetCategory.CONNECTIVITY -> Color(0xFF6FC3C7)
        PresetCategory.SAFETY_LOCATION -> Color(0xFFE39494)
    }

private val PresetCategory.lightAccent: Color
    get() = when (this) {
        PresetCategory.ROUTINE -> Color(0xFF3F6FB5)
        PresetCategory.BATTERY -> Color(0xFF2E7D52)
        PresetCategory.GESTURES -> Color(0xFF8A6516)
        PresetCategory.CONNECTIVITY -> Color(0xFF1F7378)
        PresetCategory.SAFETY_LOCATION -> Color(0xFFB04A4A)
    }

@Composable
fun PresetsBottomSheet(
    onSelectPreset: (AutomationPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf<PresetCategory?>(null) }

    val filteredPresets = remember(selectedCategory) {
        if (selectedCategory == null) {
            AutomationPresets.all
        } else {
            AutomationPresets.all.filter { it.category == selectedCategory }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // Handle all insets here: the drag handle and the header must both clear the status
        // bar and camera cutout, which the default content insets do not cover for the handle.
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Top clearance comes from the drag handle above; only the bottom inset applies here.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.presets_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.presets_sheet_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Category filter pills: selected uses a filled surface, unselected a hairline border.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetFilterPill(
                    label = "${stringResource(R.string.all_filter)} (${AutomationPresets.all.size})",
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                )
                PresetCategory.entries.forEach { cat ->
                    val count = AutomationPresets.all.count { it.category == cat }
                    PresetFilterPill(
                        label = "${cat.localizedLabel()} ($count)",
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                    PresetCardItem(
                        preset = preset,
                        onUsePreset = {
                            onSelectPreset(preset)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            Color.Transparent
        },
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PresetCardItem(
    preset: AutomationPreset,
    onUsePreset: () -> Unit,
) {
    val accent = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        preset.category.darkAccent
    } else {
        preset.category.lightAccent
    }
    val triggers = preset.template.normalizedTriggers()
    val extraTriggers = triggers.size - 1
    val extraTriggersLabel = if (extraTriggers > 0) {
        stringResource(R.string.preset_more_triggers, extraTriggers)
    } else {
        null
    }
    val logicLabel = if (preset.template.conditionTree != null) {
        stringResource(R.string.preset_logic_and_or)
    } else {
        null
    }
    val metaLabel = listOfNotNull(
        triggers.first().event.localizedLabel() + extraTriggersLabel?.let { " $it" }.orEmpty(),
        logicLabel,
        stringResource(R.string.actions_count, preset.template.effectiveActions.size),
    ).joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUsePreset),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = triggerIcon(preset.template.triggerEvent),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = preset.category.localizedLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(preset.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(preset.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onUsePreset,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.btn_use_preset),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
