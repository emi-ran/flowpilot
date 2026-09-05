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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.ui.util.localizedLabel
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
import com.flowpilot.app.ui.components.SelectionRow
import com.flowpilot.app.ui.components.TriggerCardItem
import com.flowpilot.app.ui.components.TriggerPicker
import com.flowpilot.app.ui.components.TtsSettings
import com.flowpilot.app.ui.components.bringIntoViewOnFocusOrChange
import com.flowpilot.app.ui.components.rememberActionsReorderState
import com.flowpilot.app.ui.components.actionDragTarget
import com.flowpilot.app.ui.components.actionDragHandle
import com.flowpilot.app.ui.screens.AlarmSettings
import com.flowpilot.app.ui.screens.TimerSettings
import com.flowpilot.app.engine.NfcTagHandoff
import com.flowpilot.app.engine.NfcTagUtils
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(vm: AppViewModel, initialRule: Automation, back: () -> Unit) {
    var pendingShare by remember { mutableStateOf<Automation?>(null) }
    pendingShare?.let { rule ->
        BackupDisclosureDialog(
            onConfirm = { pendingShare = null; vm.shareRule(rule) },
            onDismiss = { pendingShare = null },
        )
    }
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
    var phoneNumber by remember(initialRule.id) { mutableStateOf(initialRule.phoneNumber) }
    var flipScreenOffDetection by remember(initialRule.id) { mutableStateOf(initialRule.flipScreenOffDetection) }
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
    var lightLux by remember(initialRule.id) { mutableIntStateOf(initialRule.lightLux) }
    var screenBrightnessPercent by remember(initialRule.id) { mutableIntStateOf(initialRule.screenBrightnessPercent) }
    var forceStopPackage by remember(initialRule.id) { mutableStateOf(initialRule.forceStopPackage) }
    var forceStopAppName by remember(initialRule.id) { mutableStateOf(initialRule.forceStopAppName) }
    var showForceStopApps by remember { mutableStateOf(false) }
    var smsSenderFilter by remember(initialRule.id) { mutableStateOf(initialRule.smsSenderFilter) }
    var smsMatchMode by remember(initialRule.id) { mutableStateOf(initialRule.smsMatchMode) }
    var smsKeyword by remember(initialRule.id) { mutableStateOf(initialRule.smsKeyword) }
    var smsRecipient by remember(initialRule.id) { mutableStateOf(initialRule.smsRecipient) }
    var smsMessage by remember(initialRule.id) { mutableStateOf(initialRule.smsMessage) }
    var showTimePicker by remember { mutableStateOf(false) }
    var actions by remember(initialRule.id) { mutableStateOf(initialRule.effectiveActions.distinct()) }
    var actionDelays by remember(initialRule.id) { mutableStateOf(initialRule.effectiveActionDelays) }
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
            confirmButton = { TextButton({ scheduledMinute = pickerState.hour * 60 + pickerState.minute; showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton({ showTimePicker = false }) { Text("Cancel") } },
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
                    val currentFormRule = initialRule.copy(
                        name = name.ifBlank { initialRule.name },
                        triggerEvent = event,
                        appPackage = pkg,
                        appName = appName,
                        action = actions.firstOrNull() ?: initialRule.action,
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_single_confirm, initialRule.name)) },
            confirmButton = {
                TextButton({
                    showDeleteConfirm = false
                    vm.delete(initialRule.id)
                    back()
                }) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showDeleteConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
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
                title = { Text(stringResource(R.string.edit_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(
                        onClick = {
                            val currentFormRule = initialRule.copy(
                                name = name.ifBlank { initialRule.name },
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
                                action = actions.firstOrNull() ?: initialRule.action,
                                actions = actions,
                                actionDelays = actions.indices.map { actionDelays.getOrElse(it) { 0 } },
                            )
                            pendingShare = currentFormRule
                        }
                    ) {
                        Icon(
                            Icons.Default.Share,
                            stringResource(R.string.btn_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (actions.isNotEmpty()) {
                                showRunConfirm = true
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.msg_add_action_to_test))
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            stringResource(R.string.btn_run_now),
                            tint = if (actions.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        )
                    }
                    IconButton({ showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
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
                                    ruleId = initialRule.id,
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
                        Text(
                            stringResource(
                                R.string.cooldown_prefix,
                                if (cooldownMinutes == 0) stringResource(R.string.cooldown_none) else "${cooldownMinutes}m"
                            ),
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
                OutlinedButton(back, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.btn_cancel)) }
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
                                TriggerEvent.CALL_RINGING,
                                TriggerEvent.CALL_ANSWERED,
                                TriggerEvent.CALL_OUTGOING,
                                TriggerEvent.CALL_ENDED,
                                TriggerEvent.DEVICE_FLIPPED_DOWN,
                                TriggerEvent.DEVICE_FLIPPED_UP -> "${event.label} · $summary"
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
                            (ActionType.FORCE_STOP_APP !in actions || forceStopPackage.isNotEmpty()) &&
                            (ActionType.OPEN_URL !in actions || isWebUrl(url)) &&
                            (ActionType.HTTP_WEBHOOK !in actions || (isWebUrl(webhookUrl) && WebhookExecutor.validateHeaders(webhookHeaders) == null)) &&
                            (ActionType.PLAY_SOUND !in actions || soundPreset != SoundPreset.CUSTOM || soundUri.isNotEmpty()) &&
                            (ActionType.SPEAK_TEXT !in actions || (ttsAudioFileName.isNotEmpty() && ttsManager.getCacheFile(ttsAudioFileName)?.exists() == true && ttsAudioFileName == ttsManager.computeCacheFileName(initialRule.id, ttsText.trim(), ttsVoiceName, ttsSpeechRate))) &&
                            (ActionType.DIAL_NUMBER !in actions && ActionType.CALL_NUMBER !in actions || phoneNumber.trim().isNotEmpty()) &&
                            (ActionType.SEND_SMS !in actions || (smsRecipient.trim().isNotEmpty() && smsMessage.trim().isNotEmpty())) &&
                            (ActionType.DRAFT_SMS !in actions || (smsRecipient.trim().isNotEmpty() || smsMessage.trim().isNotEmpty())) &&
                            actions.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.btn_save_changes))
                }
            }
        }
        }
    }
}
