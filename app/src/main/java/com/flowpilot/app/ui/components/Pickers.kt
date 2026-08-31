@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.components

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.flowpilot.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Item model for visual presentation in category-based picker dialogs. */
data class PickerItem<T>(
    val value: T,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val keywords: List<String> = emptyList(),
)

/** Visual category group with icon, label, description, and list of options. */
data class PickerCategoryGroup<T>(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val items: List<PickerItem<T>>,
)

/** Full-screen modal picker for triggers, grouped by category with search and icons. */
@Composable
fun TriggerPicker(selected: TriggerEvent, select: (TriggerEvent) -> Unit, onDismiss: () -> Unit) {
    val groups = remember {
        listOf(
            PickerCategoryGroup(
                id = TriggerCategory.APP.name,
                label = TriggerCategory.APP.label,
                icon = Icons.Default.Apps,
                iconTint = Color(0xFF8AB4F8),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.APP_OPENED,
                        title = TriggerEvent.APP_OPENED.label,
                        subtitle = "When selected foreground app starts",
                        icon = Icons.Default.Apps,
                        iconTint = Color(0xFF8AB4F8),
                        keywords = listOf("launch", "open", "start", "foreground", "package", "application"),
                    ),
                    PickerItem(
                        value = TriggerEvent.APP_CLOSED,
                        title = TriggerEvent.APP_CLOSED.label,
                        subtitle = "When foreground app closes or leaves focus",
                        icon = Icons.Default.Close,
                        iconTint = Color(0xFF90CAF9),
                        keywords = listOf("exit", "close", "quit", "leave", "background", "stop"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.POWER.name,
                label = TriggerCategory.POWER.label,
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFFFBC65E),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.CHARGER_CONNECTED,
                        title = TriggerEvent.CHARGER_CONNECTED.label,
                        subtitle = "When AC or wireless power source is plugged in",
                        icon = Icons.Default.Power,
                        iconTint = Color(0xFFFBC65E),
                        keywords = listOf("charge", "plug", "cable", "ac", "usb", "power on", "charging"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CHARGER_DISCONNECTED,
                        title = TriggerEvent.CHARGER_DISCONNECTED.label,
                        subtitle = "When device is unplugged from power",
                        icon = Icons.Default.PowerOff,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("unplug", "discharge", "cable", "power off", "disconnected"),
                    ),
                    PickerItem(
                        value = TriggerEvent.BATTERY_BELOW,
                        title = TriggerEvent.BATTERY_BELOW.label,
                        subtitle = "When charge percentage drops past threshold",
                        icon = Icons.Default.BatteryAlert,
                        iconTint = Color(0xFFFFB4AB),
                        keywords = listOf("battery", "level", "drop", "low", "percent", "threshold"),
                    ),
                    PickerItem(
                        value = TriggerEvent.BATTERY_ABOVE,
                        title = TriggerEvent.BATTERY_ABOVE.label,
                        subtitle = "When charge percentage rises above threshold",
                        icon = Icons.Default.BatteryChargingFull,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("battery", "level", "high", "full", "percent", "threshold", "charged"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.DISPLAY.name,
                label = TriggerCategory.DISPLAY.label,
                icon = Icons.Default.Smartphone,
                iconTint = Color(0xFF4DD0E1),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.SCREEN_ON,
                        title = TriggerEvent.SCREEN_ON.label,
                        subtitle = "When screen turns on or device wakes up",
                        icon = Icons.Default.StayCurrentPortrait,
                        iconTint = Color(0xFF4DD0E1),
                        keywords = listOf("screen", "display", "wake", "unlock", "on", "light"),
                    ),
                    PickerItem(
                        value = TriggerEvent.SCREEN_OFF,
                        title = TriggerEvent.SCREEN_OFF.label,
                        subtitle = "When screen locks or turns off",
                        icon = Icons.Default.StayCurrentLandscape,
                        iconTint = Color(0xFF80DEEA),
                        keywords = listOf("screen", "display", "sleep", "lock", "off", "dim"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.TIME.name,
                label = TriggerCategory.TIME.label,
                icon = Icons.Default.Schedule,
                iconTint = Color(0xFFCE93D8),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.TIME_SCHEDULE,
                        title = TriggerEvent.TIME_SCHEDULE.label,
                        subtitle = "At specific time of day and selected days",
                        icon = Icons.Default.Schedule,
                        iconTint = Color(0xFFCE93D8),
                        keywords = listOf("time", "clock", "alarm", "schedule", "daily", "days", "cron", "minute"),
                    ),
                ),
            ),
        )
    }

    ModernChoiceDialog(
        title = "Choose trigger",
        searchPlaceholder = "Search triggers (e.g. battery, screen, time)...",
        groups = groups,
        isSelected = { it == selected },
        onSelect = { select(it) },
        onDismiss = onDismiss,
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
    val allGroups = remember {
        listOf(
            PickerCategoryGroup(
                id = ActionCategory.ALERTS.name,
                label = ActionCategory.ALERTS.label,
                icon = Icons.Default.Notifications,
                iconTint = Color(0xFFFFB74D),
                items = listOf(
                    PickerItem(
                        value = ActionType.SHOW_NOTIFICATION,
                        title = ActionType.SHOW_NOTIFICATION.label,
                        subtitle = "Post a heads-up or status notification",
                        icon = Icons.Default.Notifications,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("notify", "notification", "banner", "message", "alert", "push"),
                    ),
                    PickerItem(
                        value = ActionType.DND_ON,
                        title = ActionType.DND_ON.label,
                        subtitle = "Mute all interruptions and enable Do Not Disturb",
                        icon = Icons.Default.DoNotDisturb,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("dnd", "do not disturb", "mute", "silence", "quiet", "dnd on", "zen"),
                    ),
                    PickerItem(
                        value = ActionType.DND_OFF,
                        title = ActionType.DND_OFF.label,
                        subtitle = "Allow normal interruptions and disable Do Not Disturb",
                        icon = Icons.Default.DoNotDisturbOff,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("dnd", "do not disturb", "unmute", "normal", "dnd off", "zen off"),
                    ),
                    PickerItem(
                        value = ActionType.VIBRATE,
                        title = ActionType.VIBRATE.label,
                        subtitle = "Vibrate device with custom pattern and strength",
                        icon = Icons.Default.Vibration,
                        iconTint = Color(0xFFFFCC80),
                        keywords = listOf("vibration", "haptic", "buzz", "pulse", "shake", "vibrate"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.CLOCK.name,
                label = ActionCategory.CLOCK.label,
                icon = Icons.Default.Alarm,
                iconTint = Color(0xFFFFD54F),
                items = listOf(
                    PickerItem(
                        value = ActionType.CREATE_ALARM,
                        title = ActionType.CREATE_ALARM.label,
                        subtitle = "Create an alarm in the system Clock app",
                        icon = Icons.Default.Alarm,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("alarm", "clock", "wake", "alert", "time", "set alarm"),
                    ),
                    PickerItem(
                        value = ActionType.START_TIMER,
                        title = ActionType.START_TIMER.label,
                        subtitle = "Start a countdown timer in the system Clock app",
                        icon = Icons.Default.HourglassEmpty,
                        iconTint = Color(0xFFFFCA28),
                        keywords = listOf("timer", "countdown", "clock", "stopwatch", "seconds", "minutes", "start timer"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.AUDIO.name,
                label = ActionCategory.AUDIO.label,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconTint = Color(0xFF81D4FA),
                items = listOf(
                    PickerItem(
                        value = ActionType.PLAY_SOUND,
                        title = ActionType.PLAY_SOUND.label,
                        subtitle = "Play custom audio file or system tone",
                        icon = Icons.Default.MusicNote,
                        iconTint = Color(0xFF81D4FA),
                        keywords = listOf("audio", "sound", "ringtone", "alarm", "music", "tone", "play"),
                    ),
                    PickerItem(
                        value = ActionType.SET_MEDIA_VOLUME,
                        title = ActionType.SET_MEDIA_VOLUME.label,
                        subtitle = "Adjust media stream volume percentage",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = Color(0xFF4FC3F7),
                        keywords = listOf("volume", "sound", "media", "level", "audio", "loudness", "mute"),
                    ),
                    PickerItem(
                        value = ActionType.SPEAK_TEXT,
                        title = ActionType.SPEAK_TEXT.label,
                        subtitle = "Synthesize and speak voice text with TTS",
                        icon = Icons.Default.RecordVoiceOver,
                        iconTint = Color(0xFF29B6F6),
                        keywords = listOf("tts", "speech", "voice", "talk", "speak", "text to speech", "read"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.APPS_LINKS.name,
                label = ActionCategory.APPS_LINKS.label,
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                iconTint = Color(0xFF80CBC4),
                items = listOf(
                    PickerItem(
                        value = ActionType.LAUNCH_APP,
                        title = ActionType.LAUNCH_APP.label,
                        subtitle = "Start an installed app into the foreground",
                        icon = Icons.Default.Apps,
                        iconTint = Color(0xFF80CBC4),
                        keywords = listOf("launch", "open", "app", "application", "package", "start", "run"),
                    ),
                    PickerItem(
                        value = ActionType.OPEN_URL,
                        title = ActionType.OPEN_URL.label,
                        subtitle = "Open website or deep link in default browser",
                        icon = Icons.Default.Language,
                        iconTint = Color(0xFF4DB6AC),
                        keywords = listOf("url", "link", "web", "browser", "http", "https", "site", "open"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.DISPLAY.name,
                label = ActionCategory.DISPLAY.label,
                icon = Icons.Default.ScreenRotation,
                iconTint = Color(0xFF4DD0E1),
                items = listOf(
                    PickerItem(
                        value = ActionType.AUTO_ROTATE_ON,
                        title = ActionType.AUTO_ROTATE_ON.label,
                        subtitle = "Enable automatic screen rotation based on orientation",
                        icon = Icons.Default.ScreenRotation,
                        iconTint = Color(0xFF4DD0E1),
                        keywords = listOf("rotate", "rotation", "auto rotate", "orientation", "landscape", "portrait", "screen"),
                    ),
                    PickerItem(
                        value = ActionType.AUTO_ROTATE_OFF,
                        title = ActionType.AUTO_ROTATE_OFF.label,
                        subtitle = "Lock screen orientation to portrait / disable auto-rotate",
                        icon = Icons.Default.StayCurrentPortrait,
                        iconTint = Color(0xFF80DEEA),
                        keywords = listOf("rotate", "rotation", "lock rotation", "portrait lock", "orientation", "screen lock", "disable auto rotate"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.BATTERY.name,
                label = ActionCategory.BATTERY.label,
                icon = Icons.Default.BatterySaver,
                iconTint = Color(0xFFAED581),
                items = listOf(
                    PickerItem(
                        value = ActionType.BATTERY_SAVER_ON,
                        title = ActionType.BATTERY_SAVER_ON.label,
                        subtitle = "Enable Android low-power saver mode",
                        icon = Icons.Default.BatterySaver,
                        iconTint = Color(0xFFAED581),
                        keywords = listOf("battery saver", "power save", "low power", "save battery", "saver on"),
                    ),
                    PickerItem(
                        value = ActionType.BATTERY_SAVER_OFF,
                        title = ActionType.BATTERY_SAVER_OFF.label,
                        subtitle = "Disable battery saver mode",
                        icon = Icons.Default.BatteryFull,
                        iconTint = Color(0xFFC5E1A5),
                        keywords = listOf("battery saver", "normal power", "saver off", "disable battery saver"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.NFC.name,
                label = ActionCategory.NFC.label,
                icon = Icons.Default.Nfc,
                iconTint = Color(0xFFB39DDB),
                items = listOf(
                    PickerItem(
                        value = ActionType.NFC_ON,
                        title = ActionType.NFC_ON.label,
                        subtitle = "Turn NFC radio on (requires Shizuku)",
                        icon = Icons.Default.Nfc,
                        iconTint = Color(0xFFB39DDB),
                        keywords = listOf("nfc", "contactless", "beam", "tag", "nfc on", "enable"),
                    ),
                    PickerItem(
                        value = ActionType.NFC_OFF,
                        title = ActionType.NFC_OFF.label,
                        subtitle = "Turn NFC radio off (requires Shizuku)",
                        icon = Icons.Default.Nfc,
                        iconTint = Color(0xFF9575CD),
                        keywords = listOf("nfc", "contactless", "beam", "tag", "nfc off", "disable"),
                    ),
                ),
            ),
        )
    }

    val availableGroups = remember(unavailable, selected) {
        allGroups.mapNotNull { group ->
            val filteredItems = group.items.filter { item ->
                item.value == selected || item.value !in unavailable
            }
            if (filteredItems.isNotEmpty()) group.copy(items = filteredItems) else null
        }
    }

    ModernChoiceDialog(
        title = "Choose action",
        searchPlaceholder = "Search actions (e.g. sound, app, notify, nfc)...",
        groups = availableGroups,
        isSelected = { selected != null && it == selected },
        onSelect = { select(it) },
        onDismiss = onDismiss,
    )
}

/** Full-screen modal picker with search, category grouping, colored icons, and rich cards. */
@Composable
fun <T> ModernChoiceDialog(
    title: String,
    searchPlaceholder: String,
    groups: List<PickerCategoryGroup<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(groups, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val categoryMatches = group.label.lowercase().contains(query)
                val matchingItems = group.items.filter { item ->
                    categoryMatches ||
                        item.title.lowercase().contains(query) ||
                        item.subtitle.lowercase().contains(query) ||
                        item.keywords.any { it.lowercase().contains(query) }
                }
                if (matchingItems.isNotEmpty()) group.copy(items = matchingItems) else null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics { contentDescription = "Close dialog" },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = {
                        Text(
                            searchPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.semantics { contentDescription = "Clear search query" },
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                )

                Spacer(Modifier.height(8.dp))

                if (filteredGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No matches found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try searching for a different keyword or name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filteredGroups.forEach { group ->
                            item(key = "hdr-${group.id}") {
                                CategorySectionHeader(
                                    title = group.label,
                                    count = group.items.size,
                                    icon = group.icon,
                                    iconTint = group.iconTint ?: MaterialTheme.colorScheme.primary,
                                )
                            }
                            items(
                                items = group.items,
                                key = { "${group.id}-${it.title}" },
                            ) { item ->
                                val selected = isSelected(item.value)
                                PickerOptionCard(
                                    item = item,
                                    selected = selected,
                                    onClick = { onSelect(item.value) },
                                )
                            }
                            item(key = "spacer-${group.id}") {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Category header row with colored leading icon, name and item count pill. */
@Composable
private fun CategorySectionHeader(
    title: String,
    count: Int,
    icon: ImageVector,
    iconTint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = iconTint,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** Compact rounded card representing one picker option with leading icon, title, subtitle, check. */
@Composable
private fun <T> PickerOptionCard(
    item: PickerItem<T>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderStroke = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                this.role = Role.RadioButton
                this.selected = selected
                this.contentDescription = "${item.title}, ${item.subtitle}${if (selected) ", selected" else ""}"
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconBg = (item.iconTint ?: MaterialTheme.colorScheme.primary).copy(alpha = if (selected) 0.25f else 0.15f)
            val iconColor = item.iconTint ?: MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
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
