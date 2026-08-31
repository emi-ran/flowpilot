@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.actions.SoundExecutor
import com.flowpilot.app.actions.ActionParameters
import com.flowpilot.app.actions.VibrationExecutor
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.ActionPicker
import com.flowpilot.app.ui.components.AppPicker
import com.flowpilot.app.ui.components.SelectionRow
import com.flowpilot.app.ui.components.TriggerPicker

@Composable
fun CreateScreen(vm: AppViewModel, done: () -> Unit) {
    BackHandler(onBack = done)
    val context = LocalContext.current
    var event by remember { mutableStateOf(TriggerEvent.APP_OPENED) }
    val now = java.time.LocalTime.now()
    var scheduledMinute by remember { mutableIntStateOf(now.hour * 60 + now.minute) }
    var scheduledDays by remember { mutableStateOf(emptySet<Int>()) }
    var batteryLevel by remember { mutableIntStateOf(50) }
    var notificationTitle by remember { mutableStateOf("FlowPilot") }
    var notificationBody by remember { mutableStateOf("Automation ran") }
    var vibrationPattern by remember { mutableStateOf(VibrationPattern.PULSE) }
    var vibrationDurationMs by remember { mutableIntStateOf(220) }
    var vibrationAmplitude by remember { mutableIntStateOf(180) }
    var mediaVolumePercent by remember { mutableIntStateOf(50) }
    var soundPreset by remember { mutableStateOf(SoundPreset.NOTIFICATION) }
    var soundUri by remember { mutableStateOf("") }
    var soundName by remember { mutableStateOf("") }
    var soundDurationMs by remember { mutableIntStateOf(3_000) }
    var launchPackage by remember { mutableStateOf("") }
    var launchAppName by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val previewVibration = remember(context) { VibrationExecutor(context) }
    val previewSound = remember(context) { SoundExecutor(context) }
    val sourceSoundDurationMs = remember(soundPreset, soundUri) { soundSourceDurationMs(context, soundPreset, soundUri) }
    DisposableEffect(previewSound) { onDispose { previewSound.stopPreview() } }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        soundPreset = SoundPreset.CUSTOM
        soundUri = uri.toString()
        soundName = uri.lastPathSegment?.substringAfterLast('/') ?: "Custom audio"
        soundSourceDurationMs(context, SoundPreset.CUSTOM, uri.toString())?.let { soundDurationMs = it.coerceIn(1_000, 60_000) }
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(emptyList<ActionType>()) }
    var editingActionIndex by remember { mutableStateOf<Int?>(null) }
    var pkg by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    var showLaunchApps by remember { mutableStateOf(false) }
    var showTriggers by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    if (showApps) AppPicker({ p, n -> pkg = p; appName = n; showApps = false }) { showApps = false }
    if (showLaunchApps) AppPicker({ p, n -> launchPackage = p; launchAppName = n; showLaunchApps = false }) { showLaunchApps = false }
    if (showTriggers) TriggerPicker(event, { event = it; showTriggers = false }) { showTriggers = false }
    if (showActions) {
        val currentSelected = editingActionIndex?.let { if (it < actions.size) actions[it] else null }
        ActionPicker(
            selected = currentSelected,
            unavailable = actions.toSet(),
            select = { chosen ->
                val idx = editingActionIndex
                if (idx != null && idx < actions.size) {
                    actions = actions.mapIndexed { i, a -> if (i == idx) chosen else a }
                } else {
                    actions = actions + chosen
                }
                showActions = false
                editingActionIndex = null
            },
            onDismiss = {
                showActions = false
                editingActionIndex = null
            }
        )
    }
    if (showTimePicker) {
        val pickerState = rememberTimePickerState(scheduledMinute / 60, scheduledMinute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton({ scheduledMinute = pickerState.hour * 60 + pickerState.minute; showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton({ showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(pickerState) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Create automation", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("WHEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            SelectionRow("Trigger", event.label) { showTriggers = true }
            Spacer(Modifier.height(10.dp))
            if (event == TriggerEvent.TIME_SCHEDULE) {
                ScheduleSettings(scheduledMinute, scheduledDays, { showTimePicker = true }) { scheduledDays = it }
            } else if (event == TriggerEvent.BATTERY_BELOW || event == TriggerEvent.BATTERY_ABOVE) {
                BatteryThresholdSettings(batteryLevel) { batteryLevel = it }
            } else if (event == TriggerEvent.APP_OPENED || event == TriggerEvent.APP_CLOSED) {
                SelectionRow(if (pkg.isEmpty()) "App" else appName, if (pkg.isEmpty()) "Choose an app" else pkg) { showApps = true }
            }

            Text(
                "DO (${actions.size} action${if (actions.size > 1) "s" else ""})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { index, act ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f).clickable {
                                    editingActionIndex = index
                                    showActions = true
                                }
                            ) {
                                Text("Action ${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(2.dp))
                                Text(act.label, style = MaterialTheme.typography.titleMedium)
                            }
                            if (actions.size > 1) {
                                IconButton(
                                    onClick = {
                                        actions = actions.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Close, "Remove action", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    editingActionIndex = null
                    showActions = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add action")
            }

            if (ActionType.SHOW_NOTIFICATION in actions) {
                NotificationSettings(
                    title = notificationTitle,
                    body = notificationBody,
                    setTitle = { notificationTitle = it },
                    setBody = { notificationBody = it },
                )
            }
            if (ActionType.VIBRATE in actions) {
                VibrationSettings(
                    vibrationPattern,
                    vibrationDurationMs,
                    vibrationAmplitude,
                    { vibrationPattern = it },
                    { vibrationDurationMs = it },
                    { vibrationAmplitude = it },
                    { pattern, duration, amplitude ->
                        previewVibration.execute(
                            ActionType.VIBRATE,
                            ActionParameters(vibrationPattern = pattern, vibrationDurationMs = duration, vibrationAmplitude = amplitude),
                        )
                    },
                )
            }
            if (ActionType.PLAY_SOUND in actions) {
                SoundSettings(soundPreset, soundName, soundDurationMs, sourceSoundDurationMs, { preset -> soundPreset = preset; if (preset != SoundPreset.CUSTOM) soundUri = "" }, { soundDurationMs = it }, { previewSound.execute(ActionType.PLAY_SOUND, ActionParameters(soundPreset = soundPreset, soundUri = soundUri, soundDurationMs = soundDurationMs)) }, previewSound::stopPreview, { soundPicker.launch(arrayOf("audio/*")) })
            }
            if (ActionType.SET_MEDIA_VOLUME in actions) {
                MediaVolumeSettings(mediaVolumePercent) { mediaVolumePercent = it }
            }
            if (ActionType.LAUNCH_APP in actions) {
                Text("Launch app", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                SelectionRow(
                    if (launchPackage.isEmpty()) "App" else launchAppName,
                    if (launchPackage.isEmpty()) "Choose an app to launch" else launchPackage,
                ) { showLaunchApps = true }
            }
            if (ActionType.OPEN_URL in actions) {
                Text("Open URL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("https://example.com") },
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                label = { Text("Name (optional)") },
                singleLine = true,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(done, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        vm.addRule(name, event, pkg, appName, actions, scheduledMinute, scheduledDays, batteryLevel, notificationTitle, notificationBody, vibrationPattern, vibrationDurationMs, vibrationAmplitude, mediaVolumePercent, soundPreset, soundUri, soundName, soundDurationMs, launchPackage, launchAppName, url)
                        done()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled =
                        (event != TriggerEvent.APP_OPENED && event != TriggerEvent.APP_CLOSED || pkg.isNotEmpty()) &&
                            (ActionType.LAUNCH_APP !in actions || launchPackage.isNotEmpty()) &&
                            (ActionType.OPEN_URL !in actions || isWebUrl(url)) &&
                            (ActionType.PLAY_SOUND !in actions || soundPreset != SoundPreset.CUSTOM || soundUri.isNotEmpty()) &&
                            actions.isNotEmpty(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

fun isWebUrl(value: String): Boolean {
    val uri = android.net.Uri.parse(value.trim())
    return uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()
}

@Composable
private fun BatteryThresholdSettings(level: Int, setLevel: (Int) -> Unit) {
    Text("Threshold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("$level%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
    Slider(
        value = level.toFloat(),
        onValueChange = { setLevel(it.toInt()) },
        valueRange = 1f..100f,
        steps = 0,
    )
}

@Composable
private fun NotificationSettings(
    title: String,
    body: String,
    setTitle: (String) -> Unit,
    setBody: (String) -> Unit,
) {
    Text("Notification", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    OutlinedTextField(value = title, onValueChange = setTitle, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Title") }, singleLine = true)
    OutlinedTextField(value = body, onValueChange = setBody, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Message") }, minLines = 2)
}

@Composable
private fun VibrationSettings(
    pattern: VibrationPattern,
    durationMs: Int,
    amplitude: Int,
    setPattern: (VibrationPattern) -> Unit,
    setDuration: (Int) -> Unit,
    setAmplitude: (Int) -> Unit,
    preview: (VibrationPattern, Int, Int) -> Unit,
) {
    Text("Vibration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        VibrationPattern.entries.forEach { option ->
            FilterChip(selected = pattern == option, onClick = { setPattern(option); preview(option, durationMs, amplitude) }, label = { Text(option.label) })
        }
    }
    Text("Duration  $durationMs ms", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    Slider(
        value = durationMs.toFloat(),
        onValueChange = { setDuration(it.toInt()) },
        onValueChangeFinished = { preview(pattern, durationMs, amplitude) },
        valueRange = 80f..800f,
        steps = 0,
    )
    val strength = when {
        amplitude < 100 -> "Soft"
        amplitude < 200 -> "Normal"
        else -> "Strong"
    }
    Text("Strength  $strength", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = amplitude.toFloat(),
        onValueChange = { setAmplitude(it.toInt()) },
        onValueChangeFinished = { preview(pattern, durationMs, amplitude) },
        valueRange = 1f..255f,
        steps = 0,
    )
}

@Composable
private fun MediaVolumeSettings(percent: Int, setPercent: (Int) -> Unit) {
    Text("Media volume", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    Text("$percent%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
    Slider(
        value = percent.toFloat(),
        onValueChange = { setPercent(it.toInt()) },
        valueRange = 0f..100f,
        steps = 0,
    )
}

fun soundSourceDurationMs(context: android.content.Context, preset: SoundPreset, rawUri: String): Int? {
    val uri = when (preset) {
        SoundPreset.NOTIFICATION -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        SoundPreset.ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        SoundPreset.RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        SoundPreset.CUSTOM -> rawUri.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse)
    } ?: return null
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
        } finally {
            retriever.release()
        }
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun SoundSettings(
    preset: SoundPreset,
    customName: String,
    durationMs: Int,
    sourceDurationMs: Int?,
    setPreset: (SoundPreset) -> Unit,
    setDurationMs: (Int) -> Unit,
    preview: () -> Unit,
    stopPreview: () -> Unit,
    chooseFile: () -> Unit,
) {
    Text("Sound", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        SoundPreset.entries.forEach { option ->
            FilterChip(selected = preset == option, onClick = { setPreset(option) }, label = { Text(option.label) })
        }
    }
    if (preset == SoundPreset.CUSTOM) {
        Text(if (customName.isBlank()) "No audio file selected" else customName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        OutlinedButton(onClick = chooseFile, modifier = Modifier.padding(top = 8.dp)) { Text("Choose MP3 or WAV") }
    }
    sourceDurationMs?.let { Text("Source length  ${it / 1000}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
    Text("Play for  ${durationMs / 1000}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
    Slider(value = durationMs.toFloat(), onValueChange = { setDurationMs(it.toInt()) }, valueRange = 1_000f..60_000f, steps = 59)
    OutlinedButton(onClick = preview, modifier = Modifier.padding(top = 8.dp)) { Text("Preview") }
    TextButton(onClick = stopPreview) { Text("Stop preview") }
}

@Composable
private fun ScheduleSettings(
    scheduledMinute: Int,
    scheduledDays: Set<Int>,
    chooseTime: () -> Unit,
    setDays: (Set<Int>) -> Unit,
) {
    val hour = scheduledMinute / 60
    val minute = scheduledMinute % 60
    SelectionRow("Time", "%02d:%02d".format(hour, minute), chooseTime)
    Text("Repeat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = scheduledDays.isEmpty(), onClick = { setDays(emptySet()) }, label = { Text("Daily") })
        FilterChip(selected = scheduledDays == setOf(1, 2, 3, 4, 5), onClick = { setDays(setOf(1, 2, 3, 4, 5)) }, label = { Text("Weekdays") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = scheduledDays.isNotEmpty() && day in scheduledDays,
                onClick = {
                    val next = if (day in scheduledDays) scheduledDays - day else scheduledDays + day
                    setDays(next)
                },
                label = { Text(label) },
            )
        }
    }
}
