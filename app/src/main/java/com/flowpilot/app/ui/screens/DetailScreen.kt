@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.screens

import com.flowpilot.app.data.model.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.ui.components.SecondaryActionButton
import com.flowpilot.app.ui.components.SectionHeader
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.flattenConditions
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.actions.MusicLinkValidator
import com.flowpilot.app.ui.components.rememberMusicAppAvailability
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.engine.isValidGeofenceConfig
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.ui.util.localizedLabel
import com.flowpilot.app.ui.util.automaticAutomationName
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
import com.flowpilot.app.ui.components.canSaveTtsConfiguration
import com.flowpilot.app.ui.components.bringIntoViewOnFocusOrChange
import com.flowpilot.app.ui.components.rememberActionsReorderState
import com.flowpilot.app.ui.components.actionDragTarget
import com.flowpilot.app.ui.components.actionDragHandle
import com.flowpilot.app.ui.screens.AlarmSettings
import com.flowpilot.app.ui.screens.TimerSettings
import com.flowpilot.app.engine.NfcTagUtils
import com.flowpilot.app.analysis.AutomationConflict
import com.flowpilot.app.analysis.AutomationConflictAnalyzer
import com.flowpilot.app.ui.components.ConflictWarningDialog
import com.flowpilot.app.shortcut.RuleShortcutManager
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    vm: AppViewModel,
    initialRule: Automation,
    inspectRule: (Automation) -> Unit = {},
    showConflictWarning: Boolean = true,
    back: () -> Unit,
) {
    var pendingShare by remember { mutableStateOf<Automation?>(null) }
    pendingShare?.let { rule ->
        BackupDisclosureDialog(
            onDismiss = { pendingShare = null },
            onConfirmWithPassword = { password ->
                pendingShare = null
                vm.shareRule(rule, password)
            },
        )
    }
    BackHandler(onBack = back)
    val context = LocalContext.current
    val musicAppAvailable = rememberMusicAppAvailability()
    val existingRules by vm.automations.collectAsState()
    var pendingSave by remember(initialRule.id) { mutableStateOf<Automation?>(null) }
    var pendingConflicts by remember(initialRule.id) { mutableStateOf<List<AutomationConflict>>(emptyList()) }
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
    var mediaTriggerPackage by remember(initialRule.id) { mutableStateOf(initialRule.mediaTriggerPackage) }
    var mediaTargetPackage by remember(initialRule.id) { mutableStateOf(initialRule.mediaTargetPackage) }
    var musicAppPackage by remember(initialRule.id) { mutableStateOf(initialRule.musicAppPackage) }
    var musicQuery by remember(initialRule.id) { mutableStateOf(initialRule.musicQuery) }
    var musicLink by remember(initialRule.id) { mutableStateOf(initialRule.musicLink) }
    var showMediaTargetApps by remember { mutableStateOf(false) }
    var phoneNumber by remember(initialRule.id) { mutableStateOf(initialRule.phoneNumber) }
    var flipScreenOffDetection by remember(initialRule.id) { mutableStateOf(initialRule.flipScreenOffDetection) }
    var conditions by remember(initialRule.id) { mutableStateOf(initialRule.conditions) }
    var conditionTree by remember(initialRule.id) { mutableStateOf(initialRule.conditionTree) }
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
    var shakeThreshold by remember(initialRule.id) { mutableFloatStateOf(initialRule.shakeThreshold) }
    var screenBrightnessPercent by remember(initialRule.id) { mutableIntStateOf(initialRule.screenBrightnessPercent) }
    var forceStopPackage by remember(initialRule.id) { mutableStateOf(initialRule.forceStopPackage) }
    var forceStopAppName by remember(initialRule.id) { mutableStateOf(initialRule.forceStopAppName) }
    var showForceStopApps by remember { mutableStateOf(false) }
    var smsSenderFilter by remember(initialRule.id) { mutableStateOf(initialRule.smsSenderFilter) }
    var smsMatchMode by remember(initialRule.id) { mutableStateOf(initialRule.smsMatchMode) }
    var smsKeyword by remember(initialRule.id) { mutableStateOf(initialRule.smsKeyword) }
    var smsRecipient by remember(initialRule.id) { mutableStateOf(initialRule.smsRecipient) }
    var smsMessage by remember(initialRule.id) { mutableStateOf(initialRule.smsMessage) }
    var geofenceName by remember(initialRule.id) { mutableStateOf(initialRule.geofenceName) }
    var geofenceLatitude by remember(initialRule.id) { mutableStateOf(initialRule.geofenceLatitude) }
    var geofenceLongitude by remember(initialRule.id) { mutableStateOf(initialRule.geofenceLongitude) }
    var geofenceRadiusMeters by remember(initialRule.id) { mutableIntStateOf(initialRule.geofenceRadiusMeters) }
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
    var showLaunchApps by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRunConfirm by remember { mutableStateOf(false) }
    var showShortcutPermissionDialog by remember { mutableStateOf(false) }
    var shortcutRequested by remember(initialRule.id) {
        mutableStateOf(RuleShortcutManager.isPinned(context, initialRule.id))
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var triggerItems by remember(initialRule.id) { mutableStateOf(initialRule.normalizedTriggers()) }
    val triggerDefinitions = if (triggerItems.size > 1) TriggerDefinitions(items = triggerItems) else null

    fun draftRule(ruleName: String = name.ifBlank { initialRule.name }): Automation = initialRule.copy(
        name = ruleName,
        triggerEvent = event,
        triggerDefinitions = triggerDefinitions,
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
        mediaTriggerPackage = mediaTriggerPackage,
        mediaTargetPackage = mediaTargetPackage,
        musicAppPackage = musicAppPackage,
        musicQuery = musicQuery,
        musicLink = musicLink,
        conditions = conditions,
        conditionTree = conditionTree,
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
        shakeThreshold = shakeThreshold,
        screenBrightnessPercent = screenBrightnessPercent,
        forceStopPackage = forceStopPackage,
        forceStopAppName = forceStopAppName,
        geofenceName = geofenceName,
        geofenceLatitude = geofenceLatitude,
        geofenceLongitude = geofenceLongitude,
        geofenceRadiusMeters = geofenceRadiusMeters,
    )

    if (showLaunchApps) AppPicker(select = { p, n -> launchPackage = p; launchAppName = n; showLaunchApps = false }, selectedPackage = launchPackage, onDismiss = { showLaunchApps = false })
    if (showForceStopApps) AppPicker(select = { p, n -> forceStopPackage = p; forceStopAppName = n; showForceStopApps = false }, selectedPackage = forceStopPackage, onDismiss = { showForceStopApps = false })
    if (showMediaTargetApps) AppPicker(select = { p, _ -> mediaTargetPackage = p; showMediaTargetApps = false }, selectedPackage = mediaTargetPackage, onDismiss = { showMediaTargetApps = false })
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
    if (showAlarmTimePicker) {
        val alarmPickerState = rememberTimePickerState(alarmHour, alarmMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showAlarmTimePicker = false },
            confirmButton = { TextButton({ alarmHour = alarmPickerState.hour; alarmMinute = alarmPickerState.minute; showAlarmTimePicker = false }) { Text(stringResource(R.string.btn_ok)) } },
            dismissButton = { TextButton({ showAlarmTimePicker = false }) { Text(stringResource(R.string.btn_cancel)) } },
            text = { TimePicker(alarmPickerState) },
        )
    }

    if (showConflictWarning && pendingConflicts.isNotEmpty()) {
        ConflictWarningDialog(
            conflicts = pendingConflicts,
            onInspect = { id -> existingRules.firstOrNull { it.rule.id == id }?.rule?.let(inspectRule) },
            onOverride = {
                pendingSave?.let { candidate ->
                    val current = AutomationConflictAnalyzer.analyze(candidate, existingRules.map { it.rule })
                    if (current != pendingConflicts) {
                        pendingConflicts = current
                        if (current.isEmpty()) {
                            vm.updateRule(candidate)
                            pendingSave = null
                            back()
                        }
                    } else {
                        vm.updateRule(candidate)
                        pendingSave = null
                        pendingConflicts = emptyList()
                        back()
                    }
                }
            },
            onDismiss = { pendingSave = null; pendingConflicts = emptyList() },
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
                val invalidMusicLink = ActionType.MUSIC_PLAY_LINK in actions && !MusicLinkValidator.isValidTemplate(musicAppPackage, musicLink)
                val musicWarning = if (actions.any { it == ActionType.MUSIC_SEARCH || it == ActionType.MUSIC_PLAY_LINK }) stringResource(R.string.music_template_manual_warning) else ""
                Text(if (actions.any { it == ActionType.MUSIC_SEARCH || it == ActionType.MUSIC_PLAY_LINK } && !musicAppAvailable(musicAppPackage)) stringResource(R.string.music_app_unavailable) else if (invalidMusicLink) stringResource(R.string.music_link_invalid) else "$runConfirmDesc$warning$musicWarning")
            },
            confirmButton = {
                TextButton({
                    showRunConfirm = false
                    val currentFormRule = draftRule()
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
                }, enabled = (actions.none { it == ActionType.MUSIC_SEARCH || it == ActionType.MUSIC_PLAY_LINK } || musicAppAvailable(musicAppPackage)) && (ActionType.MUSIC_PLAY_LINK !in actions || MusicLinkValidator.isValidTemplate(musicAppPackage, musicLink))) {
                    Text(stringResource(R.string.btn_run_now))
                }
            },
            dismissButton = {
                TextButton({ showRunConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (showShortcutPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutPermissionDialog = false },
            title = { Text(stringResource(R.string.shortcut_permission_title)) },
            text = { Text(stringResource(R.string.shortcut_permission_desc)) },
            confirmButton = {
                TextButton({
                    showShortcutPermissionDialog = false
                    RuleShortcutManager.openPermissionSettings(context)
                }) {
                    Text(stringResource(R.string.shortcut_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton({ showShortcutPermissionDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
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
                            pendingShare = draftRule()
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
                    .testTag("rule-detail-${initialRule.id}")
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

            SectionHeader(
                title = stringResource(R.string.section_when),
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )
            com.flowpilot.app.ui.components.TriggerListEditor(triggerItems) { updated ->
                triggerItems = updated
                val first = Automation(id = "", name = "", createdAt = 0).withTrigger(updated.first())
                event = first.triggerEvent
                    pkg = first.appPackage
                    appName = first.appName
                    batteryLevel = first.batteryLevel
                    scheduledMinute = first.scheduledMinute
                    scheduledDays = first.scheduledDays
                    wifiSsid = first.wifiSsid
                    bluetoothDeviceAddress = first.bluetoothDeviceAddress
                    bluetoothDeviceName = first.bluetoothDeviceName
                    nfcTagId = first.nfcTagId
                    notificationAppPackage = first.notificationAppPackage
                    notificationAppName = first.notificationAppName
                    notificationKeyword = first.notificationKeyword
                    mediaTriggerPackage = first.mediaTriggerPackage
                    flipScreenOffDetection = first.flipScreenOffDetection
                    shakeThreshold = first.shakeThreshold
                    lightLux = first.lightLux
                    smsSenderFilter = first.smsSenderFilter
                    smsMatchMode = first.smsMatchMode
                    smsKeyword = first.smsKeyword
                    geofenceName = first.geofenceName
                    geofenceLatitude = first.geofenceLatitude
                    geofenceLongitude = first.geofenceLongitude
                    geofenceRadiusMeters = first.geofenceRadiusMeters
            }

            SectionHeader(
                title = stringResource(R.string.section_conditions_title),
                hint = stringResource(R.string.section_conditions_hint),
                modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
            )

            ConditionsSection(
                conditions = conditions,
                conditionTree = conditionTree,
                onAddCondition = { showConditionPicker = true },
                onRemoveCondition = { index ->
                    conditions = conditions.filterIndexed { i, _ -> i != index }
                },
                onUpdateCondition = { index, updated ->
                    conditions = conditions.mapIndexed { i, c -> if (i == index) updated else c }
                },
                onTreeChange = { tree ->
                    conditionTree = tree
                    // Legacy readers/exports keep a flat list; the tree stays authoritative when present.
                    conditions = tree?.flattenConditions().orEmpty()
                },
            )

            if (actions.size > 1) {
                SectionHeader(
                    title = stringResource(R.string.section_do, actions.size),
                    modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.hold_to_reorder),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            } else {
                SectionHeader(
                    title = stringResource(R.string.section_do_single),
                    modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
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
                            triggerEvents = triggerItems.map { it.event },
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
                            mediaTargetPackage = mediaTargetPackage,
                            onChooseMediaTarget = { showMediaTargetApps = true },
                            onClearMediaTarget = { mediaTargetPackage = "" },
                            musicAppPackage = musicAppPackage,
                            onMusicAppPackageChange = { musicAppPackage = it },
                            musicQuery = musicQuery,
                            onMusicQueryChange = { musicQuery = it },
                            musicLink = musicLink,
                            onMusicLinkChange = { musicLink = it },
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
                                    triggerEvents = triggerItems.map { it.event },
                                    setText = { ttsText = it },
                                    setVoiceName = { ttsVoiceName = it },
                                    setSpeechRate = { ttsSpeechRate = it },
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
                    SecondaryActionButton(
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.shortcut_section_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.shortcut_section_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    if (shortcutRequested) {
                                        RuleShortcutManager.remove(context, initialRule.id)
                                        shortcutRequested = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.shortcut_removed))
                                        }
                                    } else {
                                        when (RuleShortcutManager.requestPin(context, draftRule())) {
                                            RuleShortcutManager.PinResult.Requested -> {
                                                shortcutRequested = true
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.shortcut_request_sent))
                                                }
                                            }
                                            RuleShortcutManager.PinResult.PermissionDenied -> {
                                                showShortcutPermissionDialog = true
                                            }
                                            RuleShortcutManager.PinResult.Unsupported -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.shortcut_unsupported))
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    stringResource(
                                        if (shortcutRequested) R.string.shortcut_remove else R.string.shortcut_add,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(back, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.btn_cancel)) }
                Button(
                    onClick = {
                        val finalName = name.ifBlank {
                            automaticAutomationName(
                                context = context,
                                trigger = event,
                                actions = actions,
                                appName = appName,
                                appPackage = pkg,
                                scheduledMinute = scheduledMinute,
                                batteryLevel = batteryLevel,
                                wifiSsid = wifiSsid,
                                bluetoothDeviceName = bluetoothDeviceName,
                                bluetoothDeviceAddress = bluetoothDeviceAddress,
                                nfcTagId = nfcTagId,
                                notificationAppName = notificationAppName,
                                notificationAppPackage = notificationAppPackage,
                                lightLux = lightLux,
                                geofenceName = geofenceName,
                                geofenceRadiusMeters = geofenceRadiusMeters,
                            )
                        }
                        val updatedRule = draftRule(finalName)
                        val conflicts = AutomationConflictAnalyzer.analyze(updatedRule, existingRules.map { it.rule })
                        if (conflicts.isNotEmpty()) {
                            pendingSave = updatedRule
                            pendingConflicts = conflicts
                        } else {
                            vm.updateRule(updatedRule)
                            back()
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled =
                        triggerItems.all { it.isValidTriggerDefinition() } &&
                            (ActionType.LAUNCH_APP !in actions || launchPackage.isNotEmpty()) &&
                            (ActionType.FORCE_STOP_APP !in actions || forceStopPackage.isNotEmpty()) &&
                            (ActionType.OPEN_URL !in actions || isWebUrl(url)) &&
                            (actions.none { it == ActionType.MUSIC_SEARCH || it == ActionType.MUSIC_PLAY_LINK } || musicAppAvailable(musicAppPackage)) &&
                            (ActionType.MUSIC_SEARCH !in actions || (MusicLinkValidator.isSupportedPackage(musicAppPackage) && musicQuery.isNotBlank())) &&
                            (ActionType.MUSIC_PLAY_LINK !in actions || MusicLinkValidator.isValidTemplate(musicAppPackage, musicLink)) &&
                            (ActionType.HTTP_WEBHOOK !in actions || (isWebUrl(webhookUrl) && WebhookExecutor.validateHeaders(webhookHeaders) == null)) &&
                            (ActionType.PLAY_SOUND !in actions || soundPreset != SoundPreset.CUSTOM || soundUri.isNotEmpty()) &&
                            (ActionType.SPEAK_TEXT !in actions || canSaveTtsConfiguration(ttsText, ttsSpeechRate, ttsAudioFileName, triggerItems.map { it.event }, ttsManager)) &&
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
