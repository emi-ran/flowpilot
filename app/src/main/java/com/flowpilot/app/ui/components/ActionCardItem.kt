@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.actions.ActionParameters
import com.flowpilot.app.actions.MusicLinkValidator
import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.ui.util.localizedLabel
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

fun actionIcon(action: ActionType): ImageVector = when (action) {
    ActionType.SHOW_NOTIFICATION -> Icons.Default.Notifications
    ActionType.VIBRATE -> Icons.Default.Vibration
    ActionType.PLAY_SOUND -> Icons.Default.MusicNote
    ActionType.SET_MEDIA_VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
    ActionType.MEDIA_PLAY -> Icons.Default.PlayArrow
    ActionType.MEDIA_PAUSE -> Icons.Default.Pause
    ActionType.MEDIA_TOGGLE -> Icons.Default.PlayCircle
    ActionType.MEDIA_NEXT -> Icons.Default.SkipNext
    ActionType.MEDIA_PREVIOUS -> Icons.Default.SkipPrevious
    ActionType.MUSIC_SEARCH -> Icons.Default.Search
    ActionType.MUSIC_PLAY_LINK -> Icons.Default.MusicNote
    ActionType.LAUNCH_APP -> Icons.Default.Apps
    ActionType.OPEN_URL -> Icons.AutoMirrored.Filled.OpenInNew
    ActionType.CREATE_ALARM -> Icons.Default.Alarm
    ActionType.CREATE_CALENDAR_EVENT -> Icons.Default.Event
    ActionType.START_TIMER -> Icons.Default.HourglassEmpty
    ActionType.HTTP_WEBHOOK -> Icons.Default.Http
    ActionType.SPEAK_TEXT -> Icons.Default.RecordVoiceOver
    ActionType.OPEN_DIALER, ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> Icons.Default.Phone
    ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_OFF -> Icons.Default.Bluetooth
    ActionType.WIFI_ON, ActionType.WIFI_OFF -> Icons.Default.Wifi
    ActionType.HOTSPOT_ON -> Icons.Default.Wifi
    ActionType.HOTSPOT_OFF -> Icons.Default.WifiOff
    ActionType.MOBILE_DATA_ON, ActionType.MOBILE_DATA_OFF -> Icons.Default.SignalCellularAlt
    ActionType.AIRPLANE_MODE_ON, ActionType.AIRPLANE_MODE_OFF -> Icons.Default.AirplanemodeActive
    ActionType.TORCH_ON, ActionType.TORCH_OFF, ActionType.TORCH_TOGGLE -> Icons.Default.FlashlightOn
    ActionType.NFC_ON, ActionType.NFC_OFF -> Icons.Default.Nfc
    ActionType.BATTERY_SAVER_ON, ActionType.BATTERY_SAVER_OFF -> Icons.Default.BatterySaver
    ActionType.AUTO_ROTATE_ON, ActionType.AUTO_ROTATE_OFF -> Icons.Default.ScreenRotation
    ActionType.DND_ON, ActionType.DND_OFF -> Icons.Default.DoNotDisturb
    ActionType.DARK_THEME_ON, ActionType.DARK_THEME_OFF -> Icons.Default.DarkMode
    ActionType.SOUND_PROFILE_NORMAL, ActionType.SOUND_PROFILE_VIBRATE, ActionType.SOUND_PROFILE_SILENT -> Icons.Default.Notifications
    ActionType.SET_SCREEN_BRIGHTNESS -> Icons.Default.BrightnessMedium
    ActionType.AUTO_BRIGHTNESS_ON, ActionType.AUTO_BRIGHTNESS_OFF -> Icons.Default.BrightnessAuto
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
    triggerEvents: List<TriggerEvent> = emptyList(),
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
    mediaTargetPackage: String = "",
    onChooseMediaTarget: () -> Unit = {},
    onClearMediaTarget: () -> Unit = {},
    musicAppPackage: String = "",
    onMusicAppPackageChange: (String) -> Unit = {},
    musicQuery: String = "",
    onMusicQueryChange: (String) -> Unit = {},
    musicLink: String = "",
    onMusicLinkChange: (String) -> Unit = {},
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
    alarmDays: List<Int> = emptyList(),
    onChooseAlarmTime: () -> Unit = {},
    onAlarmMessageChange: (String) -> Unit = {},
    onAlarmDaysChange: (List<Int>) -> Unit = {},
    // Calendar event
    calendarSelection: CalendarSelectionState,
    calendarAccountName: String = "",
    calendarAccountType: String = "",
    calendarName: String = "",
    onCalendarSelect: (CalendarChoice) -> Unit = {},
    calendarTitle: String = "",
    calendarDescription: String = "",
    calendarLocation: String = "",
    calendarStartMillis: Long = 0L,
    calendarEndMillis: Long = 0L,
    calendarRepeat: String = "NONE",
    calendarReminderMinutes: Int = -1,
    onCalendarTitleChange: (String) -> Unit = {},
    onCalendarDescriptionChange: (String) -> Unit = {},
    onCalendarLocationChange: (String) -> Unit = {},
    onCalendarStartChange: (Long) -> Unit = {},
    onCalendarEndChange: (Long) -> Unit = {},
    onCalendarRepeatChange: (String) -> Unit = {},
    onCalendarReminderMinutesChange: (Int) -> Unit = {},
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
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
                            if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = if (delaySeconds > 0) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (delaySeconds == 0) stringResource(R.string.action_no_delay) else stringResource(R.string.action_wait_seconds, delaySeconds),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    TemplateTextField(
                        value = notificationTitle,
                        onValueChange = onNotificationTitleChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.action_notif_title_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    TemplateTextField(
                        value = notificationBody,
                        onValueChange = onNotificationBodyChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
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
                        SecondaryActionButton(
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
                        SecondaryActionButton(onClick = onPreviewSound, shape = RoundedCornerShape(10.dp)) {
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
                ActionType.MEDIA_PLAY, ActionType.MEDIA_PAUSE, ActionType.MEDIA_TOGGLE,
                ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.media_target_shared_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedCard(onClick = onChooseMediaTarget, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(packageName = mediaTargetPackage, modifier = Modifier.size(28.dp), fallbackIcon = Icons.Default.MusicNote)
                            Spacer(Modifier.width(10.dp))
                            Text(mediaTargetPackage.ifBlank { stringResource(R.string.media_target_system) },
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (mediaTargetPackage.isNotEmpty()) {
                        TextButton(onClick = onClearMediaTarget) { Text(stringResource(R.string.media_target_system)) }
                    }
                }
                ActionType.MUSIC_SEARCH, ActionType.MUSIC_PLAY_LINK -> {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.music_app_label), style = MaterialTheme.typography.labelMedium)
                    val installed = rememberMusicAppAvailability()
                    val spotifyAvailable = installed(MusicLinkValidator.SPOTIFY)
                    val youtubeAvailable = installed(MusicLinkValidator.YOUTUBE_MUSIC)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = musicAppPackage == MusicLinkValidator.SPOTIFY, onClick = { onMusicAppPackageChange(MusicLinkValidator.SPOTIFY) }, label = { Text(if (spotifyAvailable) "Spotify" else stringResource(R.string.music_app_unavailable_label, "Spotify")) })
                        FilterChip(selected = musicAppPackage == MusicLinkValidator.YOUTUBE_MUSIC, onClick = { onMusicAppPackageChange(MusicLinkValidator.YOUTUBE_MUSIC) }, label = { Text(if (youtubeAvailable) "YouTube Music" else stringResource(R.string.music_app_unavailable_label, "YouTube Music")) })
                    }
                    val unavailable = when (musicAppPackage) {
                        MusicLinkValidator.SPOTIFY -> !spotifyAvailable
                        MusicLinkValidator.YOUTUBE_MUSIC -> !youtubeAvailable
                        else -> false
                    }
                    AnimatedVisibility(visible = unavailable) {
                        Text(stringResource(R.string.music_app_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(stringResource(R.string.music_shared_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val linkAction = action == ActionType.MUSIC_PLAY_LINK
                    val invalidLink = linkAction && musicLink.isNotBlank() &&
                        !MusicLinkValidator.isValidTemplate(musicAppPackage, musicLink)
                    TemplateTextField(
                        value = if (linkAction) musicLink else musicQuery,
                        onValueChange = if (linkAction) onMusicLinkChange else onMusicQueryChange,
                        triggers = triggerEvents,
                        label = { Text(stringResource(if (linkAction) R.string.music_link_label else R.string.music_query_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = invalidLink,
                    )
                    val hint = when {
                        !linkAction -> R.string.music_search_hint
                        musicAppPackage == MusicLinkValidator.SPOTIFY -> R.string.music_link_spotify_hint
                        musicAppPackage == MusicLinkValidator.YOUTUBE_MUSIC -> R.string.music_link_youtube_hint
                        else -> R.string.music_link_select_app_hint
                    }
                    Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    if (invalidLink) {
                        Text(stringResource(if (MusicLinkValidator.isSupportedPackage(musicAppPackage)) R.string.music_link_invalid else R.string.music_link_select_app_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
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
                    TemplateTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.action_url_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
                ActionType.CREATE_CALENDAR_EVENT -> {
                    Spacer(Modifier.height(8.dp))
                    CalendarSelectionEditor(calendarSelection, calendarAccountName, calendarAccountType, calendarName, onCalendarSelect)
                    Column(Modifier.padding(top = 10.dp)) {
                        TemplateTextField(value = calendarTitle, onValueChange = onCalendarTitleChange, triggers = triggerEvents, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.calendar_title)) }, shape = RoundedCornerShape(12.dp), singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        TemplateTextField(value = calendarDescription, onValueChange = onCalendarDescriptionChange, triggers = triggerEvents, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.calendar_description)) }, shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(6.dp))
                        TemplateTextField(value = calendarLocation, onValueChange = onCalendarLocationChange, triggers = triggerEvents, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.calendar_location)) }, shape = RoundedCornerShape(12.dp), singleLine = true)
                    }
                    CalendarDateTimeField(R.string.calendar_start, calendarStartMillis, System.currentTimeMillis(), R.string.calendar_start_default, onCalendarStartChange)
                    CalendarDateTimeField(R.string.calendar_end, calendarEndMillis, (if (calendarStartMillis == 0L) System.currentTimeMillis() else calendarStartMillis) + 3_600_000L, R.string.calendar_end_default, onCalendarEndChange)
                    if (!com.flowpilot.app.actions.CalendarExecutor.validEditorTimes(calendarStartMillis, calendarEndMillis)) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.calendar_invalid_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                    CalendarRepeatSelector(calendarRepeat, onCalendarRepeatChange)
                    CalendarReminderSelector(calendarReminderMinutes, onCalendarReminderMinutesChange)
                    Text(stringResource(R.string.calendar_save_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
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
                    TemplateTextField(
                        value = alarmMessage,
                        onValueChange = onAlarmMessageChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.alarm_label_optional)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    val dayLabels = listOf(
                        Calendar.MONDAY to stringResource(R.string.alarm_day_monday),
                        Calendar.TUESDAY to stringResource(R.string.alarm_day_tuesday),
                        Calendar.WEDNESDAY to stringResource(R.string.alarm_day_wednesday),
                        Calendar.THURSDAY to stringResource(R.string.alarm_day_thursday),
                        Calendar.FRIDAY to stringResource(R.string.alarm_day_friday),
                        Calendar.SATURDAY to stringResource(R.string.alarm_day_saturday),
                        Calendar.SUNDAY to stringResource(R.string.alarm_day_sunday),
                    )
                    val weekdays = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                    val weekend = listOf(Calendar.SATURDAY, Calendar.SUNDAY)
                    val repeatSummary = when {
                        alarmDays.isEmpty() -> stringResource(R.string.alarm_repeat_once)
                        alarmDays.toSet() == dayLabels.map { it.first }.toSet() -> stringResource(R.string.alarm_repeat_every_day)
                        alarmDays.toSet() == weekdays.toSet() -> stringResource(R.string.alarm_repeat_weekdays)
                        alarmDays.toSet() == weekend.toSet() -> stringResource(R.string.alarm_repeat_weekend)
                        else -> dayLabels.filter { it.first in alarmDays }.joinToString(", ") { it.second }
                    }
                    var showRepeatPresets by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.alarm_repeat_days),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            repeatSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Box {
                            IconButton(onClick = { showRepeatPresets = true }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.alarm_repeat_presets),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(expanded = showRepeatPresets, onDismissRequest = { showRepeatPresets = false }) {
                                listOf(
                                    R.string.alarm_repeat_once to emptyList(),
                                    R.string.alarm_repeat_weekdays to weekdays,
                                    R.string.alarm_repeat_weekend to weekend,
                                    R.string.alarm_repeat_every_day to dayLabels.map { it.first },
                                ).forEach { (labelRes, days) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(labelRes)) },
                                        trailingIcon = if (alarmDays.toSet() == days.toSet()) {
                                            { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                        } else null,
                                        onClick = {
                                            onAlarmDaysChange(days)
                                            showRepeatPresets = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        dayLabels.forEach { (day, label) ->
                            AlarmDayToggle(
                                label = label,
                                selected = day in alarmDays,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onAlarmDaysChange(if (day in alarmDays) alarmDays - day else (alarmDays + day).sorted())
                                },
                            )
                        }
                    }
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
                    TemplateTextField(
                        value = timerMessage,
                        onValueChange = onTimerMessageChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.timer_label_optional)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
                ActionType.HTTP_WEBHOOK -> {
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
                    TemplateTextField(
                        value = webhookUrl,
                        onValueChange = onWebhookUrlChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.webhook_url_label)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    val headerError = WebhookExecutor.validateHeaders(webhookHeaders)
                    TemplateTextField(
                        value = webhookHeaders,
                        onValueChange = onWebhookHeadersChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.webhook_headers_label)) },
                        shape = RoundedCornerShape(12.dp),
                        isError = headerError != null,
                        supportingText = headerError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        minLines = 2,
                    )
                    if (webhookMethod.uppercase() in listOf("POST", "PUT", "PATCH")) {
                        Spacer(Modifier.height(6.dp))
                        TemplateTextField(
                            value = webhookBody,
                            onValueChange = onWebhookBodyChange,
                            triggers = triggerEvents,
                            modifier = Modifier.fillMaxWidth(),
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
                }
                ActionType.SPEAK_TEXT -> {
                    Spacer(Modifier.height(8.dp))
                    ttsContent()
                }
                ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> {
                    Spacer(Modifier.height(8.dp))
                    TemplateTextField(
                        value = phoneNumber,
                        onValueChange = onPhoneNumberChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
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
                    TemplateTextField(
                        value = smsRecipient,
                        onValueChange = onSmsRecipientChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sms_recipient_label)) },
                        placeholder = { Text("e.g. +90555... or \${sms.sender}") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                    )

                    Spacer(Modifier.height(10.dp))
                    TemplateTextField(
                        value = smsMessage,
                        onValueChange = onSmsMessageChange,
                        triggers = triggerEvents,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sms_message_text_label)) },
                        placeholder = { Text("e.g. Received code: \${sms.otp}") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4,
                    )

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
                else -> Unit
            }
        }
    }
}

@Composable
private fun CalendarRepeatSelector(selected: String, onSelect: (String) -> Unit) {
    Text(
        stringResource(R.string.calendar_repeat),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 6.dp)
            .selectableGroup(),
    ) {
        listOf(
            "NONE" to R.string.calendar_repeat_none,
            "DAILY" to R.string.calendar_repeat_daily,
            "WEEKLY" to R.string.calendar_repeat_weekly,
            "MONTHLY" to R.string.calendar_repeat_monthly,
            "YEARLY" to R.string.calendar_repeat_yearly,
        ).forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    AnimatedVisibility(
        visible = selected != "NONE",
        enter = expandVertically(
            animationSpec = tween(durationMillis = 220, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
        ) + fadeIn(animationSpec = tween(140)),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
        ) + fadeOut(animationSpec = tween(100)),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Autorenew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp).size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.calendar_repeat_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val REMINDER_MAX_MINUTES = 40_320
private const val REMINDER_MINUTE = 1
private const val REMINDER_HOUR = 60
private const val REMINDER_DAY = 1_440
private const val REMINDER_WEEK = 10_080

/** Picks the largest whole unit so an existing custom value reopens in a readable form. */
private fun reminderAmountFor(minutes: Int): Pair<Int, Int> = when {
    minutes > 0 && minutes % REMINDER_WEEK == 0 -> (minutes / REMINDER_WEEK) to REMINDER_WEEK
    minutes > 0 && minutes % REMINDER_DAY == 0 -> (minutes / REMINDER_DAY) to REMINDER_DAY
    minutes > 0 && minutes % REMINDER_HOUR == 0 -> (minutes / REMINDER_HOUR) to REMINDER_HOUR
    else -> minutes.coerceAtLeast(1) to REMINDER_MINUTE
}

@Composable
private fun reminderSummary(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.calendar_reminder_at_start)
    minutes % REMINDER_WEEK == 0 -> stringResource(R.string.calendar_reminder_weeks, minutes / REMINDER_WEEK)
    minutes % REMINDER_DAY == 0 -> stringResource(R.string.calendar_reminder_days, minutes / REMINDER_DAY)
    minutes % REMINDER_HOUR == 0 -> stringResource(R.string.calendar_reminder_hours, minutes / REMINDER_HOUR)
    else -> stringResource(R.string.calendar_reminder_minutes, minutes)
}

@Composable
private fun CalendarReminderSelector(selectedMinutes: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        -1 to stringResource(R.string.calendar_reminder_none),
        0 to stringResource(R.string.calendar_reminder_at_start),
        5 to stringResource(R.string.calendar_reminder_minutes, 5),
        10 to stringResource(R.string.calendar_reminder_minutes, 10),
        15 to stringResource(R.string.calendar_reminder_minutes, 15),
        30 to stringResource(R.string.calendar_reminder_minutes, 30),
        60 to stringResource(R.string.calendar_reminder_hour),
        1440 to stringResource(R.string.calendar_reminder_day),
        10080 to stringResource(R.string.calendar_reminder_week),
    )
    val presetSummary = options.firstOrNull { it.first == selectedMinutes }?.second
    val summary = presetSummary ?: if (selectedMinutes >= 0) {
        reminderSummary(selectedMinutes)
    } else {
        stringResource(R.string.calendar_reminder_none)
    }
    val isCustom = selectedMinutes >= 0 && presetSummary == null
    val reminderLabel = stringResource(R.string.calendar_reminder)
    val selectionDescription = stringResource(R.string.calendar_reminder_selected, reminderLabel, summary)
    val iconTint by animateColorAsState(
        targetValue = if (selectedMinutes >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "calendarReminderIcon",
    )
    val summaryColor by animateColorAsState(
        targetValue = if (selectedMinutes >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "calendarReminderSummary",
    )
    var expanded by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    Text(
        reminderLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
    Box(Modifier.padding(top = 6.dp)) {
        OutlinedCard(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = selectionDescription
                },
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (minutes, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { expanded = false; onSelect(minutes) },
                    trailingIcon = if (selectedMinutes == minutes) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.calendar_reminder_custom)) },
                onClick = {
                    expanded = false
                    showCustom = true
                },
                trailingIcon = if (isCustom) {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
            )
        }
    }
    if (showCustom) {
        CustomReminderDialog(
            initialMinutes = selectedMinutes,
            onDismiss = { showCustom = false },
            onConfirm = { minutes ->
                onSelect(minutes)
                showCustom = false
            },
        )
    }
}

@Composable
private fun CustomReminderDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initial = remember(initialMinutes) {
        if (initialMinutes > 0) reminderAmountFor(initialMinutes) else 1 to REMINDER_MINUTE
    }
    var amountText by remember(initialMinutes) { mutableStateOf(initial.first.toString()) }
    var unit by remember(initialMinutes) { mutableIntStateOf(initial.second) }
    val maxAmount = REMINDER_MAX_MINUTES / unit
    val total = amountText.toIntOrNull()?.takeIf { it in 1..maxAmount }?.times(unit)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_reminder_custom_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.calendar_reminder_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (total != null) onConfirm(total) }),
                    singleLine = true,
                    isError = total == null,
                    supportingText = {
                        Text(
                            if (total != null) reminderSummary(total)
                            else stringResource(R.string.calendar_reminder_invalid)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.selectableGroup(),
                ) {
                    listOf(
                        REMINDER_MINUTE to R.string.calendar_unit_minutes,
                        REMINDER_HOUR to R.string.calendar_unit_hours,
                        REMINDER_DAY to R.string.calendar_unit_days,
                        REMINDER_WEEK to R.string.calendar_unit_weeks,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = unit == value,
                            onClick = { unit = value },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.calendar_reminder_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = total != null, onClick = { if (total != null) onConfirm(total) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AlarmDayToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(160),
        label = "alarmDayContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "alarmDayContent",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(container)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun CalendarDateTimeField(label: Int, value: Long, defaultValue: Long, defaultLabel: Int, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val display = if (value == 0L) stringResource(defaultLabel) else {
        DateFormat.getDateFormat(context).format(java.util.Date(value)) + " " + DateFormat.getTimeFormat(context).format(java.util.Date(value))
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedCard(onClick = {
            val selected = Calendar.getInstance().apply { timeInMillis = if (value == 0L) defaultValue else value }
            DatePickerDialog(context, { _, year, month, day ->
                selected.set(year, month, day)
                TimePickerDialog(context, { _, hour, minute ->
                    selected.set(Calendar.HOUR_OF_DAY, hour)
                    selected.set(Calendar.MINUTE, minute)
                    selected.set(Calendar.SECOND, 0)
                    selected.set(Calendar.MILLISECOND, 0)
                    onChange(selected.timeInMillis)
                }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    tint = if (value == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        display,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != 0L) {
            TextButton(
                onClick = { onChange(0L) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.calendar_reset), style = MaterialTheme.typography.labelMedium) }
        }
    }
}
