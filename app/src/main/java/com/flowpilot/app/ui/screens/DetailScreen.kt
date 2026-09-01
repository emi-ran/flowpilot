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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Tune
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.actions.ActionParameters
import com.flowpilot.app.actions.SoundExecutor
import com.flowpilot.app.actions.TtsExecutor
import com.flowpilot.app.actions.TtsManager
import com.flowpilot.app.actions.VibrationExecutor
import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.components.ActionPicker
import com.flowpilot.app.ui.components.AppPicker
import com.flowpilot.app.ui.components.SelectionRow
import com.flowpilot.app.ui.components.TriggerPicker
import com.flowpilot.app.ui.components.TtsSettings
import com.flowpilot.app.ui.components.bringIntoViewOnFocusOrChange
import com.flowpilot.app.ui.screens.AlarmSettings
import com.flowpilot.app.ui.screens.TimerSettings
import com.flowpilot.app.engine.NfcTagHandoff
import com.flowpilot.app.engine.NfcTagUtils
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(vm: AppViewModel, initialRule: Automation, back: () -> Unit) {
    BackHandler(onBack = back)
    val context = LocalContext.current
    var event by remember(initialRule.id) { mutableStateOf(initialRule.triggerEvent) }
    var scheduledMinute by remember(initialRule.id) { mutableIntStateOf(initialRule.scheduledMinute) }
    var scheduledDays by remember(initialRule.id) { mutableStateOf(initialRule.scheduledDays) }
    var batteryLevel by remember(initialRule.id) { mutableIntStateOf(initialRule.batteryLevel) }
    var wifiSsid by remember(initialRule.id) { mutableStateOf(initialRule.wifiSsid) }
    var bluetoothDeviceAddress by remember(initialRule.id) { mutableStateOf(initialRule.bluetoothDeviceAddress) }
    var bluetoothDeviceName by remember(initialRule.id) { mutableStateOf(initialRule.bluetoothDeviceName) }
    var nfcTagId by remember(initialRule.id) { mutableStateOf(initialRule.nfcTagId) }
    var notificationAppPackage by remember(initialRule.id) { mutableStateOf(initialRule.notificationAppPackage) }
    var notificationAppName by remember(initialRule.id) { mutableStateOf(initialRule.notificationAppName) }
    var notificationKeyword by remember(initialRule.id) { mutableStateOf(initialRule.notificationKeyword) }
    var conditions by remember(initialRule.id) { mutableStateOf(initialRule.conditions) }
    var showConditionPicker by remember { mutableStateOf(false) }
    var notificationTitle by remember(initialRule.id) { mutableStateOf(initialRule.notificationTitle) }
    var notificationBody by remember(initialRule.id) { mutableStateOf(initialRule.notificationBody) }
    var vibrationPattern by remember(initialRule.id) { mutableStateOf(initialRule.vibrationPattern) }
    var vibrationDurationMs by remember(initialRule.id) { mutableIntStateOf(initialRule.vibrationDurationMs) }
    var vibrationAmplitude by remember(initialRule.id) { mutableIntStateOf(initialRule.vibrationAmplitude) }
    var mediaVolumePercent by remember(initialRule.id) { mutableIntStateOf(initialRule.mediaVolumePercent) }
    var soundPreset by remember(initialRule.id) { mutableStateOf(initialRule.soundPreset) }
    var soundUri by remember(initialRule.id) { mutableStateOf(initialRule.soundUri) }
    var soundName by remember(initialRule.id) { mutableStateOf(initialRule.soundName) }
    var soundDurationMs by remember(initialRule.id) { mutableIntStateOf(initialRule.soundDurationMs) }
    var launchPackage by remember(initialRule.id) { mutableStateOf(initialRule.launchPackage) }
    var launchAppName by remember(initialRule.id) { mutableStateOf(initialRule.launchAppName) }
    var url by remember(initialRule.id) { mutableStateOf(initialRule.url) }
    var alarmHour by remember(initialRule.id) { mutableIntStateOf(initialRule.alarmHour) }
    var alarmMinute by remember(initialRule.id) { mutableIntStateOf(initialRule.alarmMinute) }
    var alarmMessage by remember(initialRule.id) { mutableStateOf(initialRule.alarmMessage) }
    var showAlarmTimePicker by remember { mutableStateOf(false) }
    var timerDurationSeconds by remember(initialRule.id) { mutableIntStateOf(initialRule.timerDurationSeconds) }
    var timerMessage by remember(initialRule.id) { mutableStateOf(initialRule.timerMessage) }
    var cooldownMinutes by remember(initialRule.id) { mutableIntStateOf(initialRule.cooldownMinutes) }
    var webhookMethod by remember(initialRule.id) { mutableStateOf(initialRule.webhookMethod) }
    var webhookUrl by remember(initialRule.id) { mutableStateOf(initialRule.webhookUrl) }
    var webhookHeaders by remember(initialRule.id) { mutableStateOf(initialRule.webhookHeaders) }
    var webhookBody by remember(initialRule.id) { mutableStateOf(initialRule.webhookBody) }
    var webhookTimeoutSeconds by remember(initialRule.id) { mutableIntStateOf(initialRule.webhookTimeoutSeconds) }
    var ttsText by remember(initialRule.id) { mutableStateOf(initialRule.ttsText) }
    var ttsVoiceName by remember(initialRule.id) { mutableStateOf(initialRule.ttsVoiceName) }
    var ttsSpeechRate by remember(initialRule.id) { mutableFloatStateOf(initialRule.ttsSpeechRate) }
    var ttsAudioFileName by remember(initialRule.id) { mutableStateOf(initialRule.ttsAudioFileName) }
    val ttsManager = remember(context) { TtsManager(context) }
    val previewTts = remember(context, ttsManager) { TtsExecutor(context, ttsManager) }
    DisposableEffect(previewTts) { onDispose { previewTts.stopPreview() } }
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
    var actions by remember(initialRule.id) { mutableStateOf(initialRule.effectiveActions.distinct()) }
    var actionDelays by remember(initialRule.id) { mutableStateOf(initialRule.effectiveActionDelays) }
    var editingActionIndex by remember { mutableStateOf<Int?>(null) }
    var pkg by remember(initialRule.id) { mutableStateOf(initialRule.appPackage) }
    var appName by remember(initialRule.id) { mutableStateOf(initialRule.appName) }
    var name by remember(initialRule.id) { mutableStateOf(initialRule.name) }
    var showApps by remember { mutableStateOf(false) }
    var showNotificationApps by remember { mutableStateOf(false) }
    var showLaunchApps by remember { mutableStateOf(false) }
    var showTriggers by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRunConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(event) {
        if (event == TriggerEvent.NFC_TAG_SCANNED) {
            NfcTagHandoff.clearLatestScannedTagId()
            NfcTagHandoff.latestScannedTagId.collect { scannedTagId ->
                scannedTagId?.let { nfcTagId = it }
            }
        }
    }

    if (showApps) AppPicker({ p, n -> pkg = p; appName = n; showApps = false }) { showApps = false }
    if (showNotificationApps) AppPicker({ p, n -> notificationAppPackage = p; notificationAppName = n; showNotificationApps = false }) { showNotificationApps = false }
    if (showLaunchApps) AppPicker({ p, n -> launchPackage = p; launchAppName = n; showLaunchApps = false }) { showLaunchApps = false }
    if (showTriggers) TriggerPicker(event, { event = it; showTriggers = false }) { showTriggers = false }
    if (showConditionPicker) {
        ConditionPickerDialog(
            onAdd = { cond ->
                conditions = conditions + cond
                showConditionPicker = false
            },
            onDismiss = { showConditionPicker = false },
        )
    }
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
                    actionDelays = actionDelays + 0
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
    if (showAlarmTimePicker) {
        val alarmPickerState = rememberTimePickerState(alarmHour, alarmMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showAlarmTimePicker = false },
            confirmButton = { TextButton({ alarmHour = alarmPickerState.hour; alarmMinute = alarmPickerState.minute; showAlarmTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton({ showAlarmTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(alarmPickerState) },
        )
    }

    if (showRunConfirm) {
        AlertDialog(
            onDismissRequest = { showRunConfirm = false },
            title = { Text("Run automation now?") },
            text = {
                Text(
                    "This will immediately run the saved actions for '${initialRule.name}'.\n\n" +
                        "• Trigger and conditions will be bypassed\n" +
                        "• Unsaved edits on this screen are not included\n" +
                        "• Automation enabled state and last trigger time will not change"
                )
            },
            confirmButton = {
                TextButton({
                    showRunConfirm = false
                    vm.runRuleNow(initialRule) { result ->
                        scope.launch {
                            val msg = if (result.failureCount == 0) {
                                "Executed ${result.successCount} action(s) successfully"
                            } else {
                                "Ran with errors: ${result.successCount} succeeded, ${result.failureCount} failed (${result.failureMessages.joinToString(", ")})"
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }) {
                    Text("Run now")
                }
            },
            dismissButton = {
                TextButton({ showRunConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete automation?") },
            text = { Text("Are you sure you want to delete '${initialRule.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton({
                    showDeleteConfirm = false
                    vm.delete(initialRule.id)
                    back()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(
                title = { Text("Edit automation", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton({ showRunConfirm = true }) {
                        Icon(Icons.Default.PlayArrow, "Run now", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton({ showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
            Text("WHEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            SelectionRow("Trigger", event.label) { showTriggers = true }
            Spacer(Modifier.height(10.dp))
            if (event == TriggerEvent.TIME_SCHEDULE) {
                val hour = scheduledMinute / 60
                val minute = scheduledMinute % 60
                SelectionRow("Time", "%02d:%02d".format(hour, minute)) { showTimePicker = true }
                Text("Repeat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = scheduledDays.isEmpty(), onClick = { scheduledDays = emptySet() }, label = { Text("Daily") })
                    FilterChip(selected = scheduledDays == setOf(1, 2, 3, 4, 5), onClick = { scheduledDays = setOf(1, 2, 3, 4, 5) }, label = { Text("Weekdays") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                        val day = index + 1
                        FilterChip(selected = scheduledDays.isNotEmpty() && day in scheduledDays, onClick = { scheduledDays = if (day in scheduledDays) scheduledDays - day else scheduledDays + day }, label = { Text(label) })
                    }
                }
            } else if (event == TriggerEvent.BATTERY_BELOW || event == TriggerEvent.BATTERY_ABOVE) {
                Text("Threshold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$batteryLevel%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                Slider(value = batteryLevel.toFloat(), onValueChange = { batteryLevel = it.toInt() }, valueRange = 1f..100f, steps = 0)
            } else if (event == TriggerEvent.APP_OPENED || event == TriggerEvent.APP_CLOSED) {
                SelectionRow(if (pkg.isEmpty()) "App" else appName, if (pkg.isEmpty()) "Choose an app" else pkg) { showApps = true }
            } else if (event == TriggerEvent.WIFI_CONNECTED || event == TriggerEvent.WIFI_DISCONNECTED) {
                WifiTriggerSettings(wifiSsid) { wifiSsid = it }
            } else if (event == TriggerEvent.BLUETOOTH_CONNECTED || event == TriggerEvent.BLUETOOTH_DISCONNECTED) {
                BluetoothTriggerSettings(bluetoothDeviceAddress, bluetoothDeviceName) { address, deviceName ->
                    bluetoothDeviceAddress = address
                    bluetoothDeviceName = deviceName
                }
            } else if (event == TriggerEvent.NFC_TAG_SCANNED) {
                NfcTagTriggerSettings(nfcTagId) { nfcTagId = it }
            } else if (event == TriggerEvent.NOTIFICATION_RECEIVED) {
                NotificationTriggerSettings(
                    appPackage = notificationAppPackage,
                    appName = notificationAppName,
                    keyword = notificationKeyword,
                    chooseApp = { showNotificationApps = true },
                    setKeyword = { notificationKeyword = it },
                )
            }

            Text(
                "CONDITIONS (optional, all must match)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )

            ConditionsSection(
                conditions = conditions,
                onAddCondition = { showConditionPicker = true },
                onRemoveCondition = { index ->
                    conditions = conditions.filterIndexed { i, _ -> i != index }
                },
                onUpdateCondition = { index, updated ->
                    conditions = conditions.mapIndexed { i, c -> if (i == index) updated else c }
                },
            )

            Text(
                "DO (${actions.size} action${if (actions.size > 1) "s" else ""})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { index, act ->
                    val delaySec = actionDelays.getOrElse(index) { 0 }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            actionDelays = actionDelays.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Default.Close, "Remove action", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            ActionDelaySetting(
                                delaySeconds = delaySec,
                                onDelayChange = { newDelay ->
                                    val safeDelay = newDelay.coerceIn(0, 300)
                                    val currentList = actionDelays.toMutableList()
                                    while (currentList.size <= index) currentList.add(0)
                                    currentList[index] = safeDelay
                                    actionDelays = currentList
                                },
                            )
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
                Text("Notification", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(
                    value = notificationTitle,
                    onValueChange = { notificationTitle = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .bringIntoViewOnFocusOrChange(notificationTitle),
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notificationBody,
                    onValueChange = { notificationBody = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .bringIntoViewOnFocusOrChange(notificationBody),
                    label = { Text("Message") },
                    minLines = 2,
                )
            }
            if (ActionType.VIBRATE in actions) {
                Text("Vibration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    VibrationPattern.entries.forEach { option ->
                        FilterChip(
                            selected = vibrationPattern == option,
                            onClick = {
                                vibrationPattern = option
                                previewVibration.execute(ActionType.VIBRATE, ActionParameters(vibrationPattern = option, vibrationDurationMs = vibrationDurationMs, vibrationAmplitude = vibrationAmplitude))
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text("Duration  $vibrationDurationMs ms", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                Slider(
                    value = vibrationDurationMs.toFloat(),
                    onValueChange = { vibrationDurationMs = it.toInt() },
                    onValueChangeFinished = { previewVibration.execute(ActionType.VIBRATE, ActionParameters(vibrationPattern = vibrationPattern, vibrationDurationMs = vibrationDurationMs, vibrationAmplitude = vibrationAmplitude)) },
                    valueRange = 80f..800f,
                    steps = 0,
                )
                val strength = when { vibrationAmplitude < 100 -> "Soft"; vibrationAmplitude < 200 -> "Normal"; else -> "Strong" }
                Text("Strength  $strength", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = vibrationAmplitude.toFloat(),
                    onValueChange = { vibrationAmplitude = it.toInt() },
                    onValueChangeFinished = { previewVibration.execute(ActionType.VIBRATE, ActionParameters(vibrationPattern = vibrationPattern, vibrationDurationMs = vibrationDurationMs, vibrationAmplitude = vibrationAmplitude)) },
                    valueRange = 1f..255f,
                    steps = 0,
                )
            }
            if (ActionType.PLAY_SOUND in actions) {
                SoundSettings(soundPreset, soundName, soundDurationMs, sourceSoundDurationMs, { preset -> soundPreset = preset; if (preset != SoundPreset.CUSTOM) soundUri = "" }, { soundDurationMs = it }, { previewSound.execute(ActionType.PLAY_SOUND, ActionParameters(soundPreset = soundPreset, soundUri = soundUri, soundDurationMs = soundDurationMs)) }, previewSound::stopPreview, { soundPicker.launch(arrayOf("audio/*")) })
            }
            if (ActionType.SET_MEDIA_VOLUME in actions) {
                Text("Media volume", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                Text("$mediaVolumePercent%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                Slider(value = mediaVolumePercent.toFloat(), onValueChange = { mediaVolumePercent = it.toInt() }, valueRange = 0f..100f, steps = 0)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .bringIntoViewOnFocusOrChange(url),
                    label = { Text("https://example.com") },
                    singleLine = true,
                )
            }
            if (ActionType.SPEAK_TEXT in actions) {
                TtsSettings(
                    text = ttsText,
                    voiceName = ttsVoiceName,
                    speechRate = ttsSpeechRate,
                    audioFileName = ttsAudioFileName,
                    ruleId = initialRule.id,
                    setText = { ttsText = it },
                    setVoiceName = { ttsVoiceName = it },
                    setSpeechRate = { ttsSpeechRate = it },
                    setAudioFileName = { ttsAudioFileName = it },
                    ttsManager = ttsManager,
                    ttsExecutor = previewTts,
                )
            }
            if (ActionType.CREATE_ALARM in actions) {
                AlarmSettings(
                    hour = alarmHour,
                    minute = alarmMinute,
                    message = alarmMessage,
                    chooseTime = { showAlarmTimePicker = true },
                    setMessage = { alarmMessage = it },
                )
            }
            if (ActionType.START_TIMER in actions) {
                TimerSettings(
                    durationSeconds = timerDurationSeconds,
                    message = timerMessage,
                    setDurationSeconds = { timerDurationSeconds = it },
                    setMessage = { timerMessage = it },
                )
            }
            if (ActionType.HTTP_WEBHOOK in actions) {
                WebhookSettings(
                    method = webhookMethod,
                    url = webhookUrl,
                    headers = webhookHeaders,
                    body = webhookBody,
                    timeoutSeconds = webhookTimeoutSeconds,
                    setMethod = { webhookMethod = it },
                    setUrl = { webhookUrl = it },
                    setHeaders = { webhookHeaders = it },
                    setBody = { webhookBody = it },
                    setTimeoutSeconds = { webhookTimeoutSeconds = it },
                )
            }

            RuleCooldownSettings(
                cooldownMinutes = cooldownMinutes,
                onCooldownChange = { cooldownMinutes = it },
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .bringIntoViewOnFocusOrChange(name),
                shape = RoundedCornerShape(16.dp),
                label = { Text("Name") },
                singleLine = true,
            )

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(back, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        val summary = actions.joinToString(" + ") { it.label }
                        val finalName = name.ifBlank {
                            when (event) {
                                TriggerEvent.TIME_SCHEDULE -> "Schedule %02d:%02d · %s".format(scheduledMinute / 60, scheduledMinute % 60, summary)
                                TriggerEvent.CHARGER_CONNECTED,
                                TriggerEvent.CHARGER_DISCONNECTED -> "${event.label} · $summary"
                                TriggerEvent.BATTERY_BELOW,
                                TriggerEvent.BATTERY_ABOVE -> "${event.label} ${batteryLevel}% · $summary"
                                TriggerEvent.WIFI_CONNECTED,
                                TriggerEvent.WIFI_DISCONNECTED -> "${event.label} ${wifiSsid.ifBlank { "Any Wi-Fi" }} · $summary"
                                TriggerEvent.BLUETOOTH_CONNECTED,
                                TriggerEvent.BLUETOOTH_DISCONNECTED -> "${event.label} ${bluetoothDeviceName.ifBlank { bluetoothDeviceAddress }} · $summary"
                                TriggerEvent.NFC_TAG_SCANNED -> "NFC Tag ($nfcTagId) · $summary"
                                TriggerEvent.NOTIFICATION_RECEIVED -> "Notification (${notificationAppName.ifBlank { notificationAppPackage }}) · $summary"
                                else -> "${appName.ifBlank { pkg }} · $summary"
                            }
                        }
                        vm.updateRule(
                            initialRule.copy(
                                name = finalName,
                                triggerEvent = event,
                                appPackage = pkg,
                                appName = appName,
                                scheduledMinute = scheduledMinute,
                                scheduledDays = scheduledDays,
                                batteryLevel = batteryLevel,
                                wifiSsid = wifiSsid,
                                bluetoothDeviceAddress = bluetoothDeviceAddress,
                                bluetoothDeviceName = bluetoothDeviceName,
                                nfcTagId = nfcTagId.trim(),
                                notificationAppPackage = notificationAppPackage,
                                notificationAppName = notificationAppName,
                                notificationKeyword = notificationKeyword,
                                conditions = conditions,
                                notificationTitle = notificationTitle,
                                notificationBody = notificationBody,
                                vibrationPattern = vibrationPattern,
                                vibrationDurationMs = vibrationDurationMs,
                                vibrationAmplitude = vibrationAmplitude,
                                mediaVolumePercent = mediaVolumePercent,
                                soundPreset = soundPreset,
                                soundUri = soundUri,
                                soundName = soundName,
                                soundDurationMs = soundDurationMs,
                                launchPackage = launchPackage,
                                launchAppName = launchAppName,
                                url = url,
                                ttsText = ttsText,
                                ttsVoiceName = ttsVoiceName,
                                ttsSpeechRate = ttsSpeechRate,
                                ttsAudioFileName = ttsAudioFileName,
                                alarmHour = alarmHour,
                                alarmMinute = alarmMinute,
                                alarmMessage = alarmMessage,
                                timerDurationSeconds = timerDurationSeconds,
                                timerMessage = timerMessage,
                                webhookMethod = webhookMethod,
                                webhookUrl = webhookUrl,
                                webhookHeaders = webhookHeaders,
                                webhookBody = webhookBody,
                                webhookTimeoutSeconds = webhookTimeoutSeconds,
                                action = actions.firstOrNull() ?: ActionType.NFC_ON,
                                actions = actions,
                                actionDelays = actionDelays,
                                cooldownMinutes = cooldownMinutes,
                            )
                        )
                        back()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled =
                        (event != TriggerEvent.APP_OPENED && event != TriggerEvent.APP_CLOSED || pkg.isNotEmpty()) &&
                            (event != TriggerEvent.NOTIFICATION_RECEIVED || notificationAppPackage.isNotEmpty()) &&
                            (event != TriggerEvent.BLUETOOTH_CONNECTED && event != TriggerEvent.BLUETOOTH_DISCONNECTED || bluetoothDeviceAddress.isNotEmpty()) &&
                            (event != TriggerEvent.NFC_TAG_SCANNED || NfcTagUtils.isValidTagId(nfcTagId)) &&
                            (ActionType.LAUNCH_APP !in actions || launchPackage.isNotEmpty()) &&
                            (ActionType.OPEN_URL !in actions || isWebUrl(url)) &&
                            (ActionType.HTTP_WEBHOOK !in actions || (isWebUrl(webhookUrl) && WebhookExecutor.validateHeaders(webhookHeaders) == null)) &&
                            (ActionType.PLAY_SOUND !in actions || soundPreset != SoundPreset.CUSTOM || soundUri.isNotEmpty()) &&
                            (ActionType.SPEAK_TEXT !in actions || (ttsAudioFileName.isNotEmpty() && ttsManager.getCacheFile(ttsAudioFileName)?.exists() == true && ttsAudioFileName == ttsManager.computeCacheFileName(initialRule.id, ttsText.trim(), ttsVoiceName, ttsSpeechRate))) &&
                            actions.isNotEmpty(),
                ) {
                    Text("Save changes")
                }
            }
        }
        }
    }
}
