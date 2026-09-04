@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.actions.ActionParameters
import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.ui.util.localizedLabel

fun actionIcon(action: ActionType): ImageVector = when (action) {
    ActionType.SHOW_NOTIFICATION -> Icons.Default.Notifications
    ActionType.VIBRATE -> Icons.Default.Vibration
    ActionType.PLAY_SOUND -> Icons.Default.MusicNote
    ActionType.SET_MEDIA_VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
    ActionType.LAUNCH_APP -> Icons.Default.Apps
    ActionType.OPEN_URL -> Icons.AutoMirrored.Filled.OpenInNew
    ActionType.CREATE_ALARM -> Icons.Default.Alarm
    ActionType.START_TIMER -> Icons.Default.HourglassEmpty
    ActionType.HTTP_WEBHOOK -> Icons.Default.Http
    ActionType.SPEAK_TEXT -> Icons.Default.RecordVoiceOver
    ActionType.OPEN_DIALER, ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> Icons.Default.Phone
    ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_OFF -> Icons.Default.Bluetooth
    ActionType.WIFI_ON, ActionType.WIFI_OFF -> Icons.Default.Wifi
    ActionType.MOBILE_DATA_ON, ActionType.MOBILE_DATA_OFF -> Icons.Default.SignalCellularAlt
    ActionType.AIRPLANE_MODE_ON, ActionType.AIRPLANE_MODE_OFF -> Icons.Default.AirplanemodeActive
    ActionType.TORCH_ON, ActionType.TORCH_OFF -> Icons.Default.FlashlightOn
    ActionType.NFC_ON, ActionType.NFC_OFF -> Icons.Default.Nfc
    ActionType.BATTERY_SAVER_ON, ActionType.BATTERY_SAVER_OFF -> Icons.Default.BatterySaver
    ActionType.AUTO_ROTATE_ON, ActionType.AUTO_ROTATE_OFF -> Icons.Default.ScreenRotation
    ActionType.DND_ON, ActionType.DND_OFF -> Icons.Default.DoNotDisturb
    ActionType.DARK_THEME_ON, ActionType.DARK_THEME_OFF -> Icons.Default.DarkMode
    ActionType.SOUND_PROFILE_NORMAL, ActionType.SOUND_PROFILE_VIBRATE, ActionType.SOUND_PROFILE_SILENT -> Icons.Default.Notifications
    ActionType.SET_SCREEN_BRIGHTNESS -> Icons.Default.BrightnessMedium
    ActionType.LOCK_SCREEN -> Icons.Default.Lock
    ActionType.FORCE_STOP_APP -> Icons.Default.Cancel
    ActionType.LOCATION_ON, ActionType.LOCATION_OFF -> Icons.Default.LocationOn
    ActionType.SEND_SMS -> Icons.AutoMirrored.Filled.Send
    ActionType.DRAFT_SMS -> Icons.Default.Drafts
}

@Composable
fun ActionCardItem(
    index: Int,
    action: ActionType,
    delaySeconds: Int,
    totalActions: Int,
    onDelayChange: (Int) -> Unit,
    onChangeAction: () -> Unit,
    onRemoveAction: () -> Unit,
    // Notification
    notificationTitle: String = "",
    notificationBody: String = "",
    onNotificationTitleChange: (String) -> Unit = {},
    onNotificationBodyChange: (String) -> Unit = {},
    // Vibration
    vibrationPattern: VibrationPattern = VibrationPattern.DOUBLE_TAP,
    vibrationDurationMs: Int = 300,
    vibrationAmplitude: Int = 180,
    onVibrationPatternChange: (VibrationPattern) -> Unit = {},
    onVibrationDurationChange: (Int) -> Unit = {},
    onVibrationAmplitudeChange: (Int) -> Unit = {},
    onPreviewVibration: (VibrationPattern, Int, Int) -> Unit = { _, _, _ -> },
    // Sound
    soundPreset: SoundPreset = SoundPreset.NOTIFICATION,
    soundName: String = "",
    soundDurationMs: Int = 5000,
    sourceSoundDurationMs: Int? = null,
    onSoundPresetChange: (SoundPreset) -> Unit = {},
    onSoundDurationChange: (Int) -> Unit = {},
    onPreviewSound: () -> Unit = {},
    onStopPreviewSound: () -> Unit = {},
    onChooseCustomSound: () -> Unit = {},
    // Volume
    mediaVolumePercent: Int = 70,
    onMediaVolumeChange: (Int) -> Unit = {},
    // Launch app
    launchAppName: String = "",
    launchPackage: String = "",
    onChooseLaunchApp: () -> Unit = {},
    // URL
    url: String = "",
    onUrlChange: (String) -> Unit = {},
    // Alarm
    alarmHour: Int = 8,
    alarmMinute: Int = 0,
    alarmMessage: String = "",
    onChooseAlarmTime: () -> Unit = {},
    onAlarmMessageChange: (String) -> Unit = {},
    // Timer
    timerDurationSeconds: Int = 300,
    timerMessage: String = "",
    onTimerDurationChange: (Int) -> Unit = {},
    onTimerMessageChange: (String) -> Unit = {},
    // Webhook
    webhookMethod: String = "POST",
    webhookUrl: String = "",
    webhookHeaders: String = "",
    webhookBody: String = "",
    webhookTimeoutSeconds: Int = 10,
    onWebhookMethodChange: (String) -> Unit = {},
    onWebhookUrlChange: (String) -> Unit = {},
    onWebhookHeadersChange: (String) -> Unit = {},
    onWebhookBodyChange: (String) -> Unit = {},
    onWebhookTimeoutChange: (Int) -> Unit = {},
    // TTS
    ttsContent: @Composable () -> Unit = {},
    // Phone
    phoneNumber: String = "",
    onPhoneNumberChange: (String) -> Unit = {},
    // Brightness
    screenBrightnessPercent: Int = 50,
    onScreenBrightnessChange: (Int) -> Unit = {},
    // Force stop app
    forceStopPackage: String = "",
    forceStopAppName: String = "",
    onOpenForceStopAppPicker: () -> Unit = {},
    // SMS
    smsRecipient: String = "",
    onSmsRecipientChange: (String) -> Unit = {},
    smsMessage: String = "",
    onSmsMessageChange: (String) -> Unit = {},
    // Reorder modifiers & callbacks
    cardModifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    var showDelaySlider by remember { mutableStateOf(delaySeconds > 0) }
    var showReorderMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(cardModifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header Row: Badge, Icon, Title, Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (action == ActionType.LAUNCH_APP && launchPackage.isNotEmpty()) {
                    AppIconImage(
                        packageName = launchPackage,
                        modifier = Modifier.size(20.dp),
                        fallbackIcon = actionIcon(action),
                    )
                } else {
                    Icon(
                        actionIcon(action),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onChangeAction),
                ) {
                    Text(
                        action.localizedLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (totalActions > 1) {
                    Box {
                        IconButton(
                            onClick = { showReorderMenu = true },
                            modifier = Modifier
                                .size(28.dp)
                                .then(dragModifier),
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.action_reorder_desc),
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showReorderMenu,
                            onDismissRequest = { showReorderMenu = false },
                        ) {
                            if (onMoveUp != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_move_up)) },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                    onClick = {
                                        showReorderMenu = false
                                        onMoveUp()
                                    }
                                )
                            }
                            if (onMoveDown != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_move_down)) },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                    onClick = {
                                        showReorderMenu = false
                                        onMoveDown()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onRemoveAction,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Delay row: compact chip instead of giant permanent slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { showDelaySlider = !showDelaySlider },
                    shape = RoundedCornerShape(8.dp),
                    color = if (delaySeconds > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (delaySeconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (delaySeconds == 0) stringResource(R.string.action_no_delay) else stringResource(R.string.action_wait_seconds, delaySeconds),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (delaySeconds > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showDelaySlider,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 250, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                ) + fadeIn(animationSpec = tween(150)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                ) + fadeOut(animationSpec = tween(100)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${delaySeconds}s", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                    Slider(
                        value = delaySeconds.toFloat(),
                        onValueChange = { onDelayChange(it.toInt().coerceIn(0, 300)) },
                        valueRange = 0f..300f,
                        steps = 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Action-specific embedded parameters
            when (action) {
                ActionType.SHOW_NOTIFICATION -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notificationTitle,
                        onValueChange = onNotificationTitleChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusOrChange(notificationTitle),
                        label = { Text(stringResource(R.string.action_notif_title_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notificationBody,
                        onValueChange = onNotificationBodyChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusOrChange(notificationBody),
                        label = { Text(stringResource(R.string.action_notif_body_label)) },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                    )
                }
                ActionType.VIBRATE -> {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VibrationPattern.entries.forEach { pattern ->
                            FilterChip(
                                selected = vibrationPattern == pattern,
                                onClick = { onVibrationPatternChange(pattern) },
                                label = { Text(pattern.localizedLabel()) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${vibrationDurationMs}ms", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                        Slider(
                            value = vibrationDurationMs.toFloat(),
                            onValueChange = { onVibrationDurationChange(it.toInt().coerceIn(80, 2000)) },
                            valueRange = 80f..2000f,
                            steps = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val strength = when {
                        vibrationAmplitude < 100 -> stringResource(R.string.vib_strength_soft)
                        vibrationAmplitude < 200 -> stringResource(R.string.vib_strength_normal)
                        else -> stringResource(R.string.vib_strength_strong)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strength, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                        Slider(
                            value = vibrationAmplitude.toFloat(),
                            onValueChange = { onVibrationAmplitudeChange(it.toInt().coerceIn(1, 255)) },
                            valueRange = 1f..255f,
                            steps = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(
                            onClick = { onPreviewVibration(vibrationPattern, vibrationDurationMs, vibrationAmplitude) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.action_preview_vibration))
                        }
                    }
                }
                ActionType.PLAY_SOUND -> {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SoundPreset.entries.forEach { option ->
                            FilterChip(
                                selected = soundPreset == option,
                                onClick = { onSoundPresetChange(option) },
                                label = { Text(option.localizedLabel()) },
                            )
                        }
                    }
                    if (soundPreset == SoundPreset.CUSTOM) {
                        Text(
                            if (soundName.isBlank()) stringResource(R.string.sound_no_file_selected) else soundName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedButton(
                            onClick = onChooseCustomSound,
                            modifier = Modifier.padding(top = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.sound_choose_file))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.sound_duration_label, soundDurationMs / 1000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
                        Slider(
                            value = soundDurationMs.toFloat(),
                            onValueChange = { onSoundDurationChange(it.toInt().coerceIn(1_000, 60_000)) },
                            valueRange = 1_000f..60_000f,
                            steps = 59,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(onClick = onPreviewSound, shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(R.string.sound_btn_play))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onStopPreviewSound) {
                            Text(stringResource(R.string.sound_btn_stop))
                        }
                    }
                }
                ActionType.SET_MEDIA_VOLUME -> {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$mediaVolumePercent%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp))
                        Slider(
                            value = mediaVolumePercent.toFloat(),
                            onValueChange = { onMediaVolumeChange(it.toInt()) },
                            valueRange = 0f..100f,
                            steps = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ActionType.LAUNCH_APP -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(
                        onClick = onChooseLaunchApp,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                packageName = launchPackage,
                                modifier = Modifier.size(28.dp),
                                fallbackIcon = Icons.Default.Apps,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (launchPackage.isEmpty()) stringResource(R.string.action_choose_app_prompt) else "$launchAppName ($launchPackage)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                ActionType.OPEN_URL -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocusOrChange(url),
                        label = { Text(stringResource(R.string.action_url_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
                ActionType.CREATE_ALARM -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(
                        onClick = onChooseAlarmTime,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.alarm_time_label, alarmHour, alarmMinute), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = alarmMessage,
                        onValueChange = onAlarmMessageChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(alarmMessage),
                        label = { Text(stringResource(R.string.alarm_label_optional)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
                ActionType.START_TIMER -> {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "1m", 5 to "5m", 10 to "10m", 15 to "15m", 30 to "30m").forEach { (m, label) ->
                            FilterChip(
                                selected = timerDurationSeconds == m * 60,
                                onClick = { onTimerDurationChange(m * 60) },
                                label = { Text(label) },
                            )
                        }
                    }
                    val timerMinutes = timerDurationSeconds / 60
                    val timerSecs = timerDurationSeconds % 60
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (timerSecs == 0) "${timerMinutes}m" else "${timerMinutes}m ${timerSecs}s",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(60.dp),
                        )
                        Slider(
                            value = timerDurationSeconds.toFloat(),
                            onValueChange = { onTimerDurationChange(it.toInt().coerceIn(10, 3600)) },
                            valueRange = 10f..3600f,
                            steps = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = timerMessage,
                        onValueChange = onTimerMessageChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(timerMessage),
                        label = { Text(stringResource(R.string.timer_label_optional)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
                ActionType.HTTP_WEBHOOK -> {
                    var showVariablesDialog by remember { mutableStateOf(false) }

                    if (showVariablesDialog) {
                        AlertDialog(
                            onDismissRequest = { showVariablesDialog = false },
                            title = { Text(stringResource(R.string.webhook_vars_title)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("\${trigger} - ${stringResource(R.string.webhook_var_trigger_desc)}", style = MaterialTheme.typography.bodySmall)
                                    Text("\${batteryPercent} - ${stringResource(R.string.webhook_var_battery_desc)}", style = MaterialTheme.typography.bodySmall)
                                    Text("\${isCharging} - ${stringResource(R.string.webhook_var_charging_desc)}", style = MaterialTheme.typography.bodySmall)
                                    Text("\${wifiSsid} - ${stringResource(R.string.webhook_var_wifi_desc)}", style = MaterialTheme.typography.bodySmall)
                                    Text("\${timestamp} - ${stringResource(R.string.webhook_var_timestamp_desc)}", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showVariablesDialog = false }) { Text(stringResource(R.string.btn_ok)) }
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("POST", "GET", "PUT", "PATCH", "DELETE").forEach { method ->
                            FilterChip(
                                selected = webhookMethod.equals(method, ignoreCase = true),
                                onClick = { onWebhookMethodChange(method) },
                                label = { Text(method) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = onWebhookUrlChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(webhookUrl),
                        label = { Text(stringResource(R.string.webhook_url_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    val headerError = WebhookExecutor.validateHeaders(webhookHeaders)
                    OutlinedTextField(
                        value = webhookHeaders,
                        onValueChange = onWebhookHeadersChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(webhookHeaders),
                        label = { Text(stringResource(R.string.webhook_headers_label)) },
                        shape = RoundedCornerShape(12.dp),
                        isError = headerError != null,
                        supportingText = headerError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        minLines = 2,
                    )
                    if (webhookMethod.uppercase() in listOf("POST", "PUT", "PATCH")) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = webhookBody,
                            onValueChange = onWebhookBodyChange,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(webhookBody),
                            label = { Text(stringResource(R.string.webhook_body_label)) },
                            placeholder = { Text("{\"event\": \"\${trigger}\"}") },
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.webhook_timeout_label, webhookTimeoutSeconds), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
                        Slider(
                            value = webhookTimeoutSeconds.toFloat(),
                            onValueChange = { onWebhookTimeoutChange(it.toInt().coerceIn(1, 60)) },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = { showVariablesDialog = true },
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(stringResource(R.string.webhook_view_vars), style = MaterialTheme.typography.labelSmall)
                    }
                }
                ActionType.SPEAK_TEXT -> {
                    Spacer(Modifier.height(8.dp))
                    ttsContent()
                }
                ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = onPhoneNumberChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(phoneNumber),
                        label = { Text(stringResource(R.string.phone_number_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    if (action == ActionType.CALL_NUMBER) {
                        Text(
                            stringResource(R.string.phone_direct_call_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                ActionType.SEND_SMS, ActionType.DRAFT_SMS -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smsRecipient,
                        onValueChange = onSmsRecipientChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(smsRecipient),
                        label = { Text(stringResource(R.string.sms_recipient_label)) },
                        placeholder = { Text("e.g. +90555... or \${sms.sender}") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SuggestionChip(
                            onClick = { onSmsRecipientChange("\${sms.sender}") },
                            label = { Text(stringResource(R.string.sms_reply_sender_chip), style = MaterialTheme.typography.bodySmall) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = smsMessage,
                        onValueChange = onSmsMessageChange,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocusOrChange(smsMessage),
                        label = { Text(stringResource(R.string.sms_message_text_label)) },
                        placeholder = { Text("e.g. Received code: \${sms.otp}") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4,
                    )

                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.sms_quick_variables),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(
                            "\${sms.sender}" to R.string.quick_var_sender,
                            "\${sms.body}" to R.string.quick_var_full_sms,
                            "\${sms.otp}" to R.string.quick_var_otp,
                            "\${location.lat},\${location.lng}" to R.string.quick_var_gps,
                            "\${battery.percent}%" to R.string.quick_var_battery,
                            "\${time}" to R.string.quick_var_time,
                        ).forEach { (variable, labelRes) ->
                            val label = stringResource(labelRes)
                            AssistChip(
                                onClick = {
                                    val newMsg = if (smsMessage.isBlank()) variable else "$smsMessage $variable"
                                    onSmsMessageChange(newMsg)
                                },
                                label = { Text("$label ($variable)", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }

                    if (action == ActionType.SEND_SMS) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.sms_direct_send_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
                ActionType.SET_SCREEN_BRIGHTNESS -> {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$screenBrightnessPercent%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(52.dp))
                        Slider(
                            value = screenBrightnessPercent.toFloat(),
                            onValueChange = { onScreenBrightnessChange(it.toInt()) },
                            valueRange = 0f..100f,
                            steps = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(
                            0 to stringResource(R.string.brightness_min),
                            25 to "25%",
                            50 to "50%",
                            75 to "75%",
                            100 to stringResource(R.string.brightness_max)
                        ).forEach { (pct, label) ->
                            FilterChip(
                                selected = screenBrightnessPercent == pct,
                                onClick = { onScreenBrightnessChange(pct) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                ActionType.FORCE_STOP_APP -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(
                        onClick = onOpenForceStopAppPicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (forceStopPackage.isNotEmpty()) {
                                AppIconImage(
                                    packageName = forceStopPackage,
                                    modifier = Modifier.size(28.dp),
                                    fallbackIcon = Icons.Default.Cancel,
                                )
                            } else {
                                Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (forceStopPackage.isEmpty()) stringResource(R.string.action_force_stop_prompt) else "$forceStopAppName ($forceStopPackage)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    // System toggles (Wi-Fi, Bluetooth, Mobile Data, Flashlight, Airplane Mode, NFC, Battery Saver, Auto-rotate, DND, Dark Theme, Sound Profile, Lock Screen, Location)
                    Text(
                        stringResource(R.string.action_system_service_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
