@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.ui.util.localizedLabel
import android.content.Intent
import android.provider.Settings
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Tune
import kotlinx.coroutines.launch
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.AutomationPreset
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.ActionType
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
import com.flowpilot.app.ui.components.ActionCardItem
import com.flowpilot.app.ui.components.ActionPicker
import com.flowpilot.app.ui.components.AppPicker
import com.flowpilot.app.ui.components.BluetoothDevicePickerField
import com.flowpilot.app.ui.components.ConditionPicker
import com.flowpilot.app.ui.components.SelectionRow
import com.flowpilot.app.ui.components.TriggerCardItem
import com.flowpilot.app.ui.components.TriggerPicker
import com.flowpilot.app.ui.components.TtsSettings
import com.flowpilot.app.ui.components.WifiSsidPickerField
import com.flowpilot.app.ui.components.bringIntoViewOnFocusOrChange
import com.flowpilot.app.ui.components.rememberActionsReorderState
import com.flowpilot.app.ui.components.actionDragTarget
import com.flowpilot.app.ui.components.actionDragHandle
import androidx.compose.material.icons.filled.DragHandle
import com.flowpilot.app.engine.NfcTagHandoff
import com.flowpilot.app.engine.NfcTagUtils

@Composable
fun CreateScreen(
    vm: AppViewModel,
    initialPreset: com.flowpilot.app.data.model.AutomationPreset? = null,
    done: () -> Unit,
) {
    BackHandler(onBack = done)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRunConfirm by remember { mutableStateOf(false) }
    var event by remember { mutableStateOf(TriggerEvent.APP_OPENED) }
    val now = java.time.LocalTime.now()
    var scheduledMinute by remember { mutableIntStateOf(now.hour * 60 + now.minute) }
    var scheduledDays by remember { mutableStateOf(emptySet<Int>()) }
    var batteryLevel by remember { mutableIntStateOf(50) }
    var wifiSsid by remember { mutableStateOf("") }
    var bluetoothDeviceAddress by remember { mutableStateOf("") }
    var bluetoothDeviceName by remember { mutableStateOf("") }
    var nfcTagId by remember { mutableStateOf("") }
    var notificationAppPackage by remember { mutableStateOf("") }
    var notificationAppName by remember { mutableStateOf("") }
    var notificationKeyword by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var flipScreenOffDetection by remember { mutableStateOf(false) }
    var conditions by remember { mutableStateOf(emptyList<RuleCondition>()) }
    var showConditionPicker by remember { mutableStateOf(false) }
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
    var alarmHour by remember { mutableIntStateOf(7) }
    var alarmMinute by remember { mutableIntStateOf(0) }
    var alarmMessage by remember { mutableStateOf("") }
    var showAlarmTimePicker by remember { mutableStateOf(false) }
    var timerDurationSeconds by remember { mutableIntStateOf(300) }
    var timerMessage by remember { mutableStateOf("") }
    var cooldownMinutes by remember { mutableIntStateOf(0) }
    var webhookMethod by remember { mutableStateOf("POST") }
    var webhookUrl by remember { mutableStateOf("") }
    var webhookHeaders by remember { mutableStateOf("") }
    var webhookBody by remember { mutableStateOf("") }
    var webhookTimeoutSeconds by remember { mutableIntStateOf(10) }
    val newRuleId = remember { java.util.UUID.randomUUID().toString() }
    var ttsText by remember { mutableStateOf("") }
    var ttsVoiceName by remember { mutableStateOf("") }
    var ttsSpeechRate by remember { mutableFloatStateOf(1.0f) }
    var ttsAudioFileName by remember { mutableStateOf("") }
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
    var lightLux by remember { mutableIntStateOf(50) }
    var screenBrightnessPercent by remember { mutableIntStateOf(50) }
    var forceStopPackage by remember { mutableStateOf("") }
    var forceStopAppName by remember { mutableStateOf("") }
    var showForceStopApps by remember { mutableStateOf(false) }
    var smsSenderFilter by remember { mutableStateOf("") }
    var smsMatchMode by remember { mutableStateOf(com.flowpilot.app.data.model.SmsMatchMode.CONTAINS) }
    var smsKeyword by remember { mutableStateOf("") }
    var smsRecipient by remember { mutableStateOf("") }
    var smsMessage by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(emptyList<ActionType>()) }
    var actionDelays by remember { mutableStateOf(emptyList<Int>()) }
    val onReorderActions: (Int, Int) -> Unit = { fromIndex, toIndex ->
        if (fromIndex != toIndex && fromIndex in actions.indices && toIndex in actions.indices) {
            val newActions = actions.toMutableList()
            val item = newActions.removeAt(fromIndex)
            newActions.add(toIndex, item)
            actions = newActions

            val newDelays = actionDelays.toMutableList()
            while (newDelays.size < actions.size) newDelays.add(0)
            val delayItem = newDelays.removeAt(fromIndex)
            newDelays.add(toIndex, delayItem)
            actionDelays = newDelays
        }
    }
    val reorderState = rememberActionsReorderState(
        actionsCount = { actions.size },
        onReorder = onReorderActions,
    )
    var editingActionIndex by remember { mutableStateOf<Int?>(null) }
    var pkg by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    var showNotificationApps by remember { mutableStateOf(false) }
    var showLaunchApps by remember { mutableStateOf(false) }
    var showTriggers by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

    LaunchedEffect(event) {
        if (event == TriggerEvent.NFC_TAG_SCANNED) {
            NfcTagHandoff.clearLatestScannedTagId()
            NfcTagHandoff.latestScannedTagId.collect { scannedTagId ->
                scannedTagId?.let { nfcTagId = it }
            }
        }
    }

    val applyPreset: (com.flowpilot.app.data.model.AutomationPreset) -> Unit = { preset ->
        val t = preset.template
        name = t.name
        event = t.triggerEvent
        pkg = t.appPackage
        appName = t.appName
        scheduledMinute = t.scheduledMinute
        scheduledDays = t.scheduledDays
        batteryLevel = t.batteryLevel
        wifiSsid = t.wifiSsid
        bluetoothDeviceAddress = t.bluetoothDeviceAddress
        bluetoothDeviceName = t.bluetoothDeviceName
        nfcTagId = t.nfcTagId
        notificationAppPackage = t.notificationAppPackage
        notificationAppName = t.notificationAppName
        notificationKeyword = t.notificationKeyword
        phoneNumber = t.phoneNumber
        flipScreenOffDetection = t.flipScreenOffDetection
        lightLux = t.lightLux
        smsSenderFilter = t.smsSenderFilter
        smsMatchMode = t.smsMatchMode
        smsKeyword = t.smsKeyword
        smsRecipient = t.smsRecipient
        smsMessage = t.smsMessage
        conditions = t.conditions
        actions = t.effectiveActions
        actionDelays = t.effectiveActionDelays
        cooldownMinutes = t.cooldownMinutes
        notificationTitle = t.notificationTitle
        notificationBody = t.notificationBody
        vibrationPattern = t.vibrationPattern
        vibrationDurationMs = t.vibrationDurationMs
        vibrationAmplitude = t.vibrationAmplitude
        mediaVolumePercent = t.mediaVolumePercent
        soundPreset = t.soundPreset
        soundUri = t.soundUri
        soundName = t.soundName
        soundDurationMs = t.soundDurationMs
        launchPackage = t.launchPackage
        launchAppName = t.launchAppName
        url = t.url
        alarmHour = t.alarmHour
        alarmMinute = t.alarmMinute
        alarmMessage = t.alarmMessage
        timerDurationSeconds = t.timerDurationSeconds
        timerMessage = t.timerMessage
        webhookMethod = t.webhookMethod
        webhookUrl = t.webhookUrl
        webhookHeaders = t.webhookHeaders
        webhookBody = t.webhookBody
        webhookTimeoutSeconds = t.webhookTimeoutSeconds
        ttsText = t.ttsText
        ttsVoiceName = t.ttsVoiceName
        ttsSpeechRate = t.ttsSpeechRate
        ttsAudioFileName = t.ttsAudioFileName
        screenBrightnessPercent = t.screenBrightnessPercent
        forceStopPackage = t.forceStopPackage
        forceStopAppName = t.forceStopAppName

        val localizedPresetTitle = context.getString(preset.titleRes)
        name = localizedPresetTitle

        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.preset_applied_snackbar, localizedPresetTitle))
        }
    }

    LaunchedEffect(initialPreset) {
        initialPreset?.let { applyPreset(it) }
    }

    if (showPresets) {
        com.flowpilot.app.ui.components.PresetsBottomSheet(
            onSelectPreset = { preset ->
                applyPreset(preset)
            },
            onDismiss = { showPresets = false },
        )
    }

    if (showApps) AppPicker(select = { p, n -> pkg = p; appName = n; showApps = false }, selectedPackage = pkg, onDismiss = { showApps = false })
    if (showNotificationApps) AppPicker(select = { p, n -> notificationAppPackage = p; notificationAppName = n; showNotificationApps = false }, selectedPackage = notificationAppPackage, onDismiss = { showNotificationApps = false })
    if (showLaunchApps) AppPicker(select = { p, n -> launchPackage = p; launchAppName = n; showLaunchApps = false }, selectedPackage = launchPackage, onDismiss = { showLaunchApps = false })
    if (showForceStopApps) AppPicker(select = { p, n -> forceStopPackage = p; forceStopAppName = n; showForceStopApps = false }, selectedPackage = forceStopPackage, onDismiss = { showForceStopApps = false })
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
            confirmButton = { TextButton({ scheduledMinute = pickerState.hour * 60 + pickerState.minute; showTimePicker = false }) { Text(stringResource(R.string.btn_ok)) } },
            dismissButton = { TextButton({ showTimePicker = false }) { Text(stringResource(R.string.btn_cancel)) } },
            text = { TimePicker(pickerState) },
        )
    }
    if (showAlarmTimePicker) {
        val alarmPickerState = rememberTimePickerState(alarmHour, alarmMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showAlarmTimePicker = false },
            confirmButton = { TextButton({ alarmHour = alarmPickerState.hour; alarmMinute = alarmPickerState.minute; showAlarmTimePicker = false }) { Text(stringResource(R.string.btn_ok)) } },
            dismissButton = { TextButton({ showAlarmTimePicker = false }) { Text(stringResource(R.string.btn_cancel)) } },
            text = { TimePicker(alarmPickerState) },
        )
    }

    if (showRunConfirm) {
        val hasDirectCall = actions.any { it == ActionType.CALL_NUMBER }
        val runConfirmDesc = stringResource(R.string.test_run_now_confirm_desc)
        val warning = if (hasDirectCall) stringResource(R.string.test_run_now_warning_call) else ""
        AlertDialog(
            onDismissRequest = { showRunConfirm = false },
            title = { Text(stringResource(R.string.test_run_now_confirm_title)) },
            text = {
                Text("$runConfirmDesc$warning")
            },
            confirmButton = {
                TextButton({
                    showRunConfirm = false
                    val currentFormRule = Automation(
                        id = newRuleId,
                        name = name.ifBlank { "New automation" },
                        enabled = true,
                        triggerEvent = event,
                        appPackage = pkg,
                        appName = appName,
                        action = actions.firstOrNull() ?: ActionType.SHOW_NOTIFICATION,
                        actions = actions,
                        actionDelays = actions.indices.map { actionDelays.getOrElse(it) { 0 } },
                        scheduledMinute = scheduledMinute,
                        scheduledDays = scheduledDays,
                        batteryLevel = batteryLevel,
                        wifiSsid = wifiSsid,
                        bluetoothDeviceAddress = bluetoothDeviceAddress,
                        bluetoothDeviceName = bluetoothDeviceName,
                        nfcTagId = nfcTagId.trim(),
                        flipScreenOffDetection = flipScreenOffDetection,
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
                        alarmHour = alarmHour,
                        alarmMinute = alarmMinute,
                        alarmMessage = alarmMessage,
                        timerDurationSeconds = timerDurationSeconds,
                        timerMessage = timerMessage,
                        cooldownMinutes = cooldownMinutes,
                        webhookMethod = webhookMethod,
                        webhookUrl = webhookUrl,
                        webhookHeaders = webhookHeaders,
                        webhookBody = webhookBody,
                        webhookTimeoutSeconds = webhookTimeoutSeconds,
                        ttsText = ttsText,
                        ttsVoiceName = ttsVoiceName,
                        ttsSpeechRate = ttsSpeechRate,
                        ttsAudioFileName = ttsAudioFileName,
                        phoneNumber = phoneNumber.trim(),
                        smsSenderFilter = smsSenderFilter.trim(),
                        smsMatchMode = smsMatchMode,
                        smsKeyword = smsKeyword.trim(),
                        smsRecipient = smsRecipient.trim(),
                        smsMessage = smsMessage,
                        lightLux = lightLux,
                        screenBrightnessPercent = screenBrightnessPercent,
                        forceStopPackage = forceStopPackage,
                        forceStopAppName = forceStopAppName,
                        createdAt = System.currentTimeMillis(),
                    )
                    vm.runRuleNow(currentFormRule) { result ->
                        scope.launch {
                            val msg = if (result.failureCount == 0) {
                                context.getString(R.string.test_run_executed_success, result.successCount)
                            } else {
                                context.getString(R.string.test_run_executed_errors, result.successCount, result.failureCount, result.failureMessages.joinToString(", "))
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }) {
                    Text(stringResource(R.string.btn_run_now))
                }
            },
            dismissButton = {
                TextButton({ showRunConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
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
                title = { Text(stringResource(R.string.create_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton({ showPresets = true }) {
                        Icon(Icons.Default.AutoAwesome, stringResource(R.string.presets_sheet_title), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (actions.isNotEmpty()) {
                        IconButton({ showRunConfirm = true }) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.test_actions_title), tint = MaterialTheme.colorScheme.primary)
                        }
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
            var showAdvanced by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { showPresets = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.presets_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.presets_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .bringIntoViewOnFocusOrChange(name),
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.automation_name_label)) },
                placeholder = { Text(if (appName.isNotEmpty()) "When $appName opened..." else "${event.localizedLabel()}...") },
                singleLine = true,
            )

            Text(stringResource(R.string.section_when), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            TriggerCardItem(
                event = event,
                onChangeTrigger = { showTriggers = true },
                pkg = pkg,
                appName = appName,
                onOpenAppPicker = { showApps = true },
                scheduledMinute = scheduledMinute,
                scheduledDays = scheduledDays,
                onOpenTimePicker = { showTimePicker = true },
                onDaysChange = { scheduledDays = it },
                batteryLevel = batteryLevel,
                onBatteryLevelChange = { batteryLevel = it },
                wifiSsid = wifiSsid,
                onWifiSsidChange = { wifiSsid = it },
                bluetoothAddress = bluetoothDeviceAddress,
                bluetoothName = bluetoothDeviceName,
                onBluetoothChange = { addr, devName ->
                    bluetoothDeviceAddress = addr
                    bluetoothDeviceName = devName
                },
                nfcTagId = nfcTagId,
                onNfcTagChange = { nfcTagId = it },
                notificationAppPackage = notificationAppPackage,
                notificationAppName = notificationAppName,
                notificationKeyword = notificationKeyword,
                onOpenNotificationAppPicker = { showNotificationApps = true },
                onNotificationKeywordChange = { notificationKeyword = it },
                flipScreenOffDetection = flipScreenOffDetection,
                onFlipScreenOffChange = { flipScreenOffDetection = it },
                lightLux = lightLux,
                onLightLuxChange = { lightLux = it },
                smsSenderFilter = smsSenderFilter,
                onSmsSenderFilterChange = { smsSenderFilter = it },
                smsMatchMode = smsMatchMode,
                onSmsMatchModeChange = { smsMatchMode = it },
                smsKeyword = smsKeyword,
                onSmsKeywordChange = { smsKeyword = it },
            )

            Text(
                stringResource(R.string.section_conditions),
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

            if (actions.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.section_do, actions.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.hold_to_reorder),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.section_do_single),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { index, act ->
                    key(act) {
                        ActionCardItem(
                            index = index,
                            action = act,
                            delaySeconds = actionDelays.getOrElse(index) { 0 },
                            totalActions = actions.size,
                            cardModifier = Modifier.actionDragTarget(index, reorderState),
                            dragModifier = Modifier.actionDragHandle({ index }, reorderState),
                            isDragging = reorderState.draggedIndex == index,
                            onMoveUp = if (index > 0) { { onReorderActions(index, index - 1) } } else null,
                            onMoveDown = if (index < actions.size - 1) { { onReorderActions(index, index + 1) } } else null,
                            onDelayChange = { newDelay ->
                                val safeDelay = newDelay.coerceIn(0, 300)
                                val currentList = actionDelays.toMutableList()
                                while (currentList.size <= index) currentList.add(0)
                                currentList[index] = safeDelay
                                actionDelays = currentList
                            },
                            onChangeAction = {
                                editingActionIndex = index
                                showActions = true
                            },
                            onRemoveAction = {
                                actions = actions.filterIndexed { i, _ -> i != index }
                                actionDelays = actionDelays.filterIndexed { i, _ -> i != index }
                            },
                            // Notification
                            notificationTitle = notificationTitle,
                            notificationBody = notificationBody,
                            onNotificationTitleChange = { notificationTitle = it },
                            onNotificationBodyChange = { notificationBody = it },
                            // Vibration
                            vibrationPattern = vibrationPattern,
                            vibrationDurationMs = vibrationDurationMs,
                            vibrationAmplitude = vibrationAmplitude,
                            onVibrationPatternChange = { vibrationPattern = it },
                            onVibrationDurationChange = { vibrationDurationMs = it },
                            onVibrationAmplitudeChange = { vibrationAmplitude = it },
                            onPreviewVibration = { pattern, dur, amp ->
                                previewVibration.execute(
                                    ActionType.VIBRATE,
                                    ActionParameters(vibrationPattern = pattern, vibrationDurationMs = dur, vibrationAmplitude = amp),
                                )
                            },
                            // Sound
                            soundPreset = soundPreset,
                            soundName = soundName,
                            soundDurationMs = soundDurationMs,
                            sourceSoundDurationMs = sourceSoundDurationMs,
                            onSoundPresetChange = { preset ->
                                soundPreset = preset
                                if (preset != SoundPreset.CUSTOM) soundUri = ""
                            },
                            onSoundDurationChange = { soundDurationMs = it },
                            onPreviewSound = {
                                previewSound.execute(
                                    ActionType.PLAY_SOUND,
                                    ActionParameters(soundPreset = soundPreset, soundUri = soundUri, soundDurationMs = soundDurationMs),
                                )
                            },
                            onStopPreviewSound = previewSound::stopPreview,
                            onChooseCustomSound = { soundPicker.launch(arrayOf("audio/*")) },
                            // Volume
                            mediaVolumePercent = mediaVolumePercent,
                            onMediaVolumeChange = { mediaVolumePercent = it },
                            // Launch app
                            launchAppName = launchAppName,
                            launchPackage = launchPackage,
                            onChooseLaunchApp = { showLaunchApps = true },
                            // URL
                            url = url,
                            onUrlChange = { url = it },
                            // Alarm
                            alarmHour = alarmHour,
                            alarmMinute = alarmMinute,
                            alarmMessage = alarmMessage,
                            onChooseAlarmTime = { showAlarmTimePicker = true },
                            onAlarmMessageChange = { alarmMessage = it },
                            // Timer
                            timerDurationSeconds = timerDurationSeconds,
                            timerMessage = timerMessage,
                            onTimerDurationChange = { timerDurationSeconds = it },
                            onTimerMessageChange = { timerMessage = it },
                            // Webhook
                            webhookMethod = webhookMethod,
                            webhookUrl = webhookUrl,
                            webhookHeaders = webhookHeaders,
                            webhookBody = webhookBody,
                            webhookTimeoutSeconds = webhookTimeoutSeconds,
                            onWebhookMethodChange = { webhookMethod = it },
                            onWebhookUrlChange = { webhookUrl = it },
                            onWebhookHeadersChange = { webhookHeaders = it },
                            onWebhookBodyChange = { webhookBody = it },
                            onWebhookTimeoutChange = { webhookTimeoutSeconds = it },
                            // TTS
                            ttsContent = {
                                TtsSettings(
                                    text = ttsText,
                                    voiceName = ttsVoiceName,
                                    speechRate = ttsSpeechRate,
                                    audioFileName = ttsAudioFileName,
                                    ruleId = "",
                                    setText = { ttsText = it },
                                    setVoiceName = { ttsVoiceName = it },
                                    setSpeechRate = { ttsSpeechRate = it },
                                    setAudioFileName = { ttsAudioFileName = it },
                                    ttsManager = ttsManager,
                                    ttsExecutor = previewTts,
                                )
                            },
                            // Phone
                            phoneNumber = phoneNumber,
                            onPhoneNumberChange = { phoneNumber = it },
                            // Brightness
                            screenBrightnessPercent = screenBrightnessPercent,
                            onScreenBrightnessChange = { screenBrightnessPercent = it },
                            // Force stop app
                            forceStopAppName = forceStopAppName,
                            forceStopPackage = forceStopPackage,
                            onOpenForceStopAppPicker = { showForceStopApps = true },
                            // SMS
                            smsRecipient = smsRecipient,
                            onSmsRecipientChange = { smsRecipient = it },
                            smsMessage = smsMessage,
                            onSmsMessageChange = { smsMessage = it },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        editingActionIndex = null
                        showActions = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_add_action))
                }
                if (actions.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { showRunConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_test))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val advancedChevronRotation by animateFloatAsState(
                targetValue = if (showAdvanced) 180f else 0f,
                animationSpec = tween(durationMillis = 250, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                label = "advancedChevronRotation",
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.advanced_options), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val cdText = if (cooldownMinutes == 0) stringResource(R.string.cooldown_none) else "${cooldownMinutes}m"
                        Text(
                            stringResource(R.string.cooldown_prefix, cdText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(advancedChevronRotation),
                        )
                    }
                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 250)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 250, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 150)
                        ),
                    ) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.execution_cooldown), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            RuleCooldownSettings(cooldownMinutes) { cooldownMinutes = it }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(done, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.btn_cancel)) }
                Button(
                    onClick = {
                        vm.addRule(
                            name = name,
                            triggerEvent = event,
                            appPackage = pkg,
                            appName = appName,
                            actions = actions,
                            actionDelays = actionDelays,
                            cooldownMinutes = cooldownMinutes,
                            flipScreenOffDetection = flipScreenOffDetection,
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
                            phoneNumber = phoneNumber.trim(),
                            smsSenderFilter = smsSenderFilter.trim(),
                            smsMatchMode = smsMatchMode,
                            smsKeyword = smsKeyword.trim(),
                            smsRecipient = smsRecipient.trim(),
                            smsMessage = smsMessage,
                            lightLux = lightLux,
                            screenBrightnessPercent = screenBrightnessPercent,
                            forceStopPackage = forceStopPackage,
                            forceStopAppName = forceStopAppName,
                            ruleId = newRuleId,
                        )
                        done()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled =
                        (event != TriggerEvent.APP_OPENED && event != TriggerEvent.APP_CLOSED || pkg.isNotEmpty()) &&
                            (event != TriggerEvent.NOTIFICATION_RECEIVED || notificationAppPackage.isNotEmpty()) &&
                            (event != TriggerEvent.BLUETOOTH_CONNECTED && event != TriggerEvent.BLUETOOTH_DISCONNECTED || bluetoothDeviceAddress.isNotEmpty()) &&
                            (event != TriggerEvent.NFC_TAG_SCANNED || NfcTagUtils.isValidTagId(nfcTagId)) &&
                            (ActionType.LAUNCH_APP !in actions || launchPackage.isNotEmpty()) &&
                            (ActionType.FORCE_STOP_APP !in actions || forceStopPackage.isNotEmpty()) &&
                            (ActionType.OPEN_URL !in actions || isWebUrl(url)) &&
                            (ActionType.HTTP_WEBHOOK !in actions || (isWebUrl(webhookUrl) && WebhookExecutor.validateHeaders(webhookHeaders) == null)) &&
                            (ActionType.PLAY_SOUND !in actions || soundPreset != SoundPreset.CUSTOM || soundUri.isNotEmpty()) &&
                            (ActionType.SPEAK_TEXT !in actions || (ttsAudioFileName.isNotEmpty() && ttsManager.getCacheFile(ttsAudioFileName)?.exists() == true && ttsAudioFileName == ttsManager.computeCacheFileName(newRuleId, ttsText.trim(), ttsVoiceName, ttsSpeechRate))) &&
                            (ActionType.DIAL_NUMBER !in actions && ActionType.CALL_NUMBER !in actions || phoneNumber.trim().isNotEmpty()) &&
                            (ActionType.SEND_SMS !in actions || (smsRecipient.trim().isNotEmpty() && smsMessage.trim().isNotEmpty())) &&
                            (ActionType.DRAFT_SMS !in actions || (smsRecipient.trim().isNotEmpty() || smsMessage.trim().isNotEmpty())) &&
                            actions.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }
}
}

@Composable
fun PhoneCallTriggerExplanation() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Phone-number filters are unavailable. Android 12+ does not provide outgoing numbers to normal apps; this trigger matches every call of this state.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DeviceFlipTriggerSettings(
    allowScreenOff: Boolean,
    onToggleAllowScreenOff: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Allow detection when screen is off",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (allowScreenOff) "Sensors will evaluate flip orientation while screen is locked."
                        else "Sensors only listen while screen is on to save battery (Recommended).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = allowScreenOff,
                    onCheckedChange = onToggleAllowScreenOff,
                )
            }
            if (allowScreenOff) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "⚠️ Battery Notice: Background sensor listening uses SENSOR_DELAY_NORMAL (~5Hz) to minimize drain. For reliable execution on HyperOS, ensure FlowPilot is exempted from battery optimization.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PhoneActionSettings(
    actionType: ActionType,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
) {
    Text(
        if (actionType == ActionType.CALL_NUMBER) "Call recipient" else "Dialer number",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
    if (actionType == ActionType.CALL_NUMBER) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⚠️ Starts a real phone call automatically. Requires Make phone calls permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneNumberChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(phoneNumber),
        shape = RoundedCornerShape(16.dp),
        label = { Text(stringResource(R.string.trigger_phone_number_label)) },
        placeholder = { Text("+905551234567") },
        singleLine = true,
    )
}

@Composable
fun NfcTagTriggerSettings(
    tagId: String,
    onTagIdChange: (String) -> Unit,
) {
    Text(stringResource(R.string.trigger_nfc_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    OutlinedTextField(
        value = tagId,
        onValueChange = { onTagIdChange(it.uppercase()) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(tagId),
        shape = RoundedCornerShape(16.dp),
        label = { Text(stringResource(R.string.trigger_nfc_label)) },
        placeholder = { Text(stringResource(R.string.trigger_nfc_placeholder)) },
        supportingText = {
            Text(stringResource(R.string.trigger_nfc_help))
        },
        singleLine = true,
    )
}

@Composable
fun ActionDelaySetting(
    delaySeconds: Int,
    onDelayChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (delaySeconds > 0) stringResource(R.string.action_delay_before, delaySeconds) else stringResource(R.string.action_no_delay_label),
            style = MaterialTheme.typography.bodySmall,
            color = if (delaySeconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = delaySeconds.toFloat(),
        onValueChange = { onDelayChange(it.toInt()) },
        valueRange = 0f..300f,
        steps = 0,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
fun WifiTriggerSettings(
    ssid: String,
    setSsid: (String) -> Unit,
) {
    Text(stringResource(R.string.trigger_wifi_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    WifiSsidPickerField(
        ssid = ssid,
        onSsidChange = setSsid,
    )
}

@Composable
fun BluetoothTriggerSettings(
    address: String,
    name: String,
    onDeviceSelected: (address: String, name: String) -> Unit,
) {
    Text(stringResource(R.string.trigger_bluetooth_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    BluetoothDevicePickerField(address = address, name = name, onDeviceSelected = onDeviceSelected)
}

@Composable
fun NotificationTriggerSettings(
    appPackage: String,
    appName: String,
    keyword: String,
    chooseApp: () -> Unit,
    setKeyword: (String) -> Unit,
) {
    Text(stringResource(R.string.trigger_notif_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    SelectionRow(
        if (appPackage.isEmpty()) stringResource(R.string.cat_app) else appName,
        if (appPackage.isEmpty()) stringResource(R.string.notification_choose_app) else appPackage,
        chooseApp,
    )
    OutlinedTextField(
        value = keyword,
        onValueChange = setKeyword,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(keyword),
        shape = RoundedCornerShape(16.dp),
        label = { Text(stringResource(R.string.trigger_notif_keyword_label)) },
        singleLine = true,
    )
}

@Composable
fun WebhookSettings(
    method: String,
    url: String,
    headers: String,
    body: String,
    timeoutSeconds: Int,
    setMethod: (String) -> Unit,
    setUrl: (String) -> Unit,
    setHeaders: (String) -> Unit,
    setBody: (String) -> Unit,
    setTimeoutSeconds: (Int) -> Unit,
) {
    Text(stringResource(R.string.trigger_webhook_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    var showTemplateVariables by remember { mutableStateOf(false) }

    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        methods.forEach { m ->
            FilterChip(
                selected = method.equals(m, ignoreCase = true),
                onClick = { setMethod(m) },
                label = { Text(m) },
            )
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = setUrl,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(url),
        shape = RoundedCornerShape(16.dp),
        label = { Text(stringResource(R.string.action_url_label)) },
        singleLine = true,
    )

    val headerError = WebhookExecutor.validateHeaders(headers)
    OutlinedTextField(
        value = headers,
        onValueChange = setHeaders,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(headers),
        shape = RoundedCornerShape(16.dp),
        label = { Text(stringResource(R.string.webhook_headers_label)) },
        placeholder = { Text("Content-Type: application/json\nAuthorization: Bearer token") },
        isError = headerError != null,
        supportingText = headerError?.let { err ->
            { Text(text = err, color = MaterialTheme.colorScheme.error) }
        },
        minLines = 2,
    )

    if (method.uppercase() in listOf("POST", "PUT", "PATCH")) {
        OutlinedTextField(
            value = body,
            onValueChange = setBody,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .bringIntoViewOnFocusOrChange(body),
            shape = RoundedCornerShape(16.dp),
            label = { Text(stringResource(R.string.webhook_body_label)) },
            placeholder = { Text("{\"event\": \"\${trigger}\", \"battery\": \${batteryPercent}}") },
            minLines = 3,
        )
    }

    TextButton(
        onClick = { showTemplateVariables = true },
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.webhook_view_vars))
    }

    if (showTemplateVariables) {
        WebhookTemplateVariablesDialog(onDismiss = { showTemplateVariables = false })
    }

    Text(stringResource(R.string.webhook_timeout_label, timeoutSeconds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    Slider(
        value = timeoutSeconds.toFloat(),
        onValueChange = { setTimeoutSeconds(it.toInt().coerceIn(1, 60)) },
        valueRange = 1f..60f,
        steps = 58,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun WebhookTemplateVariablesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webhook_vars_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Use these in request headers or body. URL variables are not supported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WebhookVariableRow("\${trigger}", "CHARGER_CONNECTED", "Event that ran this rule")
                WebhookVariableRow("\${batteryPercent}", "72", "Current battery percentage")
                WebhookVariableRow("\${isCharging}", "true", "Whether charger is connected")
                WebhookVariableRow("\${wifiSsid}", "Home_5G", "Current Wi-Fi name")
                WebhookVariableRow("\${time}", "2026-09-01T12:34:56Z", "ISO-8601 event time")
                WebhookVariableRow("\${timestamp}", "1788266096000", "Epoch milliseconds")
                Text(
                    "Unknown variables stay unchanged. Variables expand once only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
        },
    )
}

@Composable
private fun WebhookVariableRow(token: String, example: String, description: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(token, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Example: $example", style = MaterialTheme.typography.bodySmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConditionsSection(
    conditions: List<RuleCondition>,
    onAddCondition: () -> Unit,
    onRemoveCondition: (Int) -> Unit,
    onUpdateCondition: (Int, RuleCondition) -> Unit,
) {
    var timePickerTarget by remember { mutableStateOf<Triple<Int, Boolean, Int>?>(null) } // (index, isStart, initialMinute)

    if (timePickerTarget != null) {
        val (condIdx, isStart, initialMin) = timePickerTarget!!
        val pickerState = rememberTimePickerState(initialMin / 60, initialMin % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            confirmButton = {
                TextButton({
                    val selectedMin = pickerState.hour * 60 + pickerState.minute
                    val cond = conditions.getOrNull(condIdx)
                    if (cond != null) {
                        val updated = if (isStart) cond.copy(startMinute = selectedMin) else cond.copy(endMinute = selectedMin)
                        onUpdateCondition(condIdx, updated)
                    }
                    timePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton({ timePickerTarget = null }) { Text("Cancel") }
            },
            text = { TimePicker(pickerState) },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        conditions.forEachIndexed { index, cond ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cond.type.localizedLabel(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onRemoveCondition(index) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Close, stringResource(R.string.btn_remove_condition), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (cond.type == ConditionType.BATTERY_BELOW || cond.type == ConditionType.BATTERY_ABOVE) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text("${cond.batteryLevel}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                            Slider(
                                value = cond.batteryLevel.toFloat(),
                                onValueChange = { onUpdateCondition(index, cond.copy(batteryLevel = it.toInt())) },
                                valueRange = 1f..100f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else if (cond.type == ConditionType.WIFI_CONNECTED || cond.type == ConditionType.WIFI_DISCONNECTED) {
                        WifiSsidPickerField(
                            ssid = cond.wifiSsid,
                            onSsidChange = { onUpdateCondition(index, cond.copy(wifiSsid = it)) },
                            label = stringResource(R.string.wifi_ssid_label),
                        )
                    } else if (cond.type == ConditionType.TIME_BETWEEN) {
                        val startH = cond.startMinute / 60
                        val startM = cond.startMinute % 60
                        val endH = cond.endMinute / 60
                        val endM = cond.endMinute % 60
                        val isOvernight = cond.startMinute > cond.endMinute

                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Card(
                                    onClick = { timePickerTarget = Triple(index, true, cond.startMinute) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh),
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(stringResource(R.string.time_start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("%02d:%02d".format(startH, startM), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Card(
                                    onClick = { timePickerTarget = Triple(index, false, cond.endMinute) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh),
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(stringResource(R.string.time_end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("%02d:%02d".format(endH, endM), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                            if (isOvernight) {
                                Text(
                                    stringResource(R.string.time_overnight_window),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                                )
                            }
                        }
                    } else if (cond.type == ConditionType.DAYS_OF_WEEK) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = cond.days.isEmpty(),
                                    onClick = { onUpdateCondition(index, cond.copy(days = emptySet())) },
                                    label = { Text(stringResource(R.string.schedule_daily)) },
                                )
                                FilterChip(
                                    selected = cond.days == setOf(1, 2, 3, 4, 5),
                                    onClick = { onUpdateCondition(index, cond.copy(days = setOf(1, 2, 3, 4, 5))) },
                                    label = { Text(stringResource(R.string.schedule_weekdays)) },
                                )
                                FilterChip(
                                    selected = cond.days == setOf(6, 7),
                                    onClick = { onUpdateCondition(index, cond.copy(days = setOf(6, 7))) },
                                    label = { Text(stringResource(R.string.schedule_weekends)) },
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                                listOf(
                                    1 to R.string.day_m,
                                    2 to R.string.day_t,
                                    3 to R.string.day_w,
                                    4 to R.string.day_th,
                                    5 to R.string.day_f,
                                    6 to R.string.day_sa,
                                    7 to R.string.day_su,
                                ).forEach { (day, labelRes) ->
                                    FilterChip(
                                        selected = cond.days.isNotEmpty() && day in cond.days,
                                        onClick = {
                                            val next = if (day in cond.days) cond.days - day else cond.days + day
                                            onUpdateCondition(index, cond.copy(days = next))
                                        },
                                        label = { Text(stringResource(labelRes)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = onAddCondition,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.btn_add_condition))
    }
}

@Composable
fun ConditionPickerDialog(
    onAdd: (RuleCondition) -> Unit,
    onDismiss: () -> Unit,
) {
    ConditionPicker(
        onAdd = onAdd,
        onDismiss = onDismiss,
    )
}

fun isWebUrl(value: String): Boolean {
    val uri = android.net.Uri.parse(value.trim())
    return uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()
}

@Composable
fun AlarmSettings(
    hour: Int,
    minute: Int,
    message: String,
    chooseTime: () -> Unit,
    setMessage: (String) -> Unit,
) {
    Text("Alarm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    SelectionRow("Alarm time", "%02d:%02d".format(hour, minute), chooseTime)
    OutlinedTextField(
        value = message,
        onValueChange = setMessage,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(message),
        shape = RoundedCornerShape(16.dp),
        label = { Text("Label (optional)") },
        singleLine = true,
    )
}

@Composable
fun TimerSettings(
    durationSeconds: Int,
    message: String,
    setDurationSeconds: (Int) -> Unit,
    setMessage: (String) -> Unit,
) {
    val minutes = durationSeconds / 60
    Text("Timer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
    Text("Duration  ${if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        listOf(1 to "1m", 5 to "5m", 10 to "10m", 15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (m, label) ->
            FilterChip(
                selected = durationSeconds == m * 60,
                onClick = { setDurationSeconds(m * 60) },
                label = { Text(label) },
            )
        }
    }

    Slider(
        value = (durationSeconds / 60).toFloat(),
        onValueChange = { setDurationSeconds((it.toInt() * 60).coerceIn(60, 86400)) },
        valueRange = 1f..1440f, // 1 minute to 24 hours
        steps = 0,
        modifier = Modifier.padding(top = 8.dp),
    )

    OutlinedTextField(
        value = message,
        onValueChange = setMessage,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(message),
        shape = RoundedCornerShape(16.dp),
        label = { Text("Label (optional)") },
        singleLine = true,
    )
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
    OutlinedTextField(
        value = title,
        onValueChange = setTitle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(title),
        label = { Text("Title") },
        singleLine = true,
    )
    OutlinedTextField(
        value = body,
        onValueChange = setBody,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewOnFocusOrChange(body),
        label = { Text("Message") },
        minLines = 2,
    )
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
fun RuleCooldownSettings(
    cooldownMinutes: Int,
    onCooldownChange: (Int) -> Unit,
) {
    Text(
        "Cooldown",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
    val options = listOf(0, 1, 5, 15, 60)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        options.forEach { minutes ->
            val label = if (minutes == 0) "None" else "${minutes}m"
            FilterChip(
                selected = cooldownMinutes == minutes,
                onClick = { onCooldownChange(minutes) },
                label = { Text(label) },
            )
        }
    }
    Text(
        text = if (cooldownMinutes == 0) "Rule triggers on every matching event."
        else "Rule will wait $cooldownMinutes minute${if (cooldownMinutes > 1) "s" else ""} after a successful run before triggering automatically again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
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
