@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.flowpilot.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowpilot.app.R
import com.flowpilot.app.actions.TtsExecutor
import com.flowpilot.app.actions.TtsManager
import com.flowpilot.app.actions.VariableCatalog
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.TtsVoiceOption
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Editor save gate for SPEAK_TEXT: dynamic templates must render strictly with the rule's
 * available sample variables, and legacy rules must still point at an existing cached file.
 * Dynamic text is authoritative whenever it is nonblank.
 */
fun canSaveTtsConfiguration(
    text: String,
    speechRate: Float,
    audioFileName: String,
    triggerEvents: List<TriggerEvent>,
    ttsManager: TtsManager,
): Boolean {
    if (!speechRate.isFinite() || speechRate !in 0.5f..2.0f) return false
    if (!VariableCatalog.isTemplateValid(text, triggerEvents)) return false
    if (text.isNotBlank()) return true
    val file = audioFileName.takeIf { it.isNotBlank() }?.let(ttsManager::getCacheFile) ?: return false
    return file.isFile && file.length() > 0L
}

@Composable
fun TtsSettings(
    text: String,
    voiceName: String,
    speechRate: Float,
    audioFileName: String,
    triggerEvents: List<TriggerEvent>,
    setText: (String) -> Unit,
    setVoiceName: (String) -> Unit,
    setSpeechRate: (Float) -> Unit,
    ttsManager: TtsManager,
    ttsExecutor: TtsExecutor,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var voices by remember { mutableStateOf<List<TtsVoiceOption>>(emptyList()) }
    var isLoadingVoices by remember { mutableStateOf(true) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var showVariablePicker by remember { mutableStateOf(false) }
    var synthesisStatusMessage by remember { mutableStateOf<String?>(null) }
    var cacheGeneration by remember { mutableIntStateOf(0) }

    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    val previewLanguageTag = voices.firstOrNull { it.name == voiceName }?.locale
        ?: Locale.getDefault().toLanguageTag()
    val previewSampleText = remember(previewLanguageTag) {
        TtsPreviewSample.forLanguageTag(previewLanguageTag)
    }
    val previewResult = remember(text, triggerEvents, previewSampleText) {
        if (text.isBlank()) null else VariableCatalog.renderSample(text, triggerEvents, previewSampleText)
    }
    val previewText = previewResult
        ?.takeIf { it.unknownVariables.isEmpty() && it.parseErrors.isEmpty() }
        ?.text
        ?.takeIf { it.isNotBlank() }
    val templateMismatch = previewResult
        ?.unknownVariables
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { "\${$it}" }
    val hasTemplateError = text.isNotBlank() &&
        previewResult != null &&
        (previewResult.unknownVariables.isNotEmpty() || previewResult.parseErrors.isNotEmpty())
    val contentFileName = remember(previewText, voiceName, speechRate) {
        previewText?.let { ttsManager.contentCacheFileName(it, voiceName, speechRate) }
    }
    val hasCachedPreview = remember(contentFileName, cacheGeneration) {
        contentFileName?.let { name ->
            ttsManager.getCacheFile(name)?.let { file -> file.isFile && file.length() > 0L }
        } == true
    }
    val hasLegacyFile = remember(audioFileName) {
        if (audioFileName.isBlank()) {
            false
        } else {
            ttsManager.getCacheFile(audioFileName)?.let { it.isFile && it.length() > 0L } == true
        }
    }
    val usesSampleData = text.contains("\${")

    fun insertToken(token: String) {
        val current = fieldValue
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(start, current.text.length)
        val updated = current.text.replaceRange(start, end, token)
        fieldValue = TextFieldValue(updated, selection = TextRange(start + token.length))
        setText(updated)
        synthesisStatusMessage = null
    }

    DisposableEffect(ttsExecutor) {
        onDispose {
            ttsExecutor.stopPreview()
        }
    }

    LaunchedEffect(Unit) {
        isLoadingVoices = true
        voices = ttsManager.getAvailableOfflineVoices()
        isLoadingVoices = false
    }

    if (showVariablePicker) {
        VariablePickerDialog(
            triggers = triggerEvents,
            onInsert = ::insertToken,
            onDismiss = { showVariablePicker = false },
        )
    }

    if (showVoicePicker) {
        VoicePickerDialog(
            voices = voices,
            selectedVoiceName = voiceName,
            onSelect = {
                setVoiceName(it.name)
                showVoicePicker = false
            },
            onSelectDefault = {
                setVoiceName("")
                showVoicePicker = false
            },
            onPreview = { previewVoice ->
                val sampleText = TtsPreviewSample.forLanguageTag(previewVoice.locale)
                val rendered = VariableCatalog.renderSample(text, triggerEvents, sampleText)
                    .takeIf { it.unknownVariables.isEmpty() && it.parseErrors.isEmpty() }
                    ?.text
                    ?.takeIf { it.isNotBlank() }
                if (rendered == null) {
                    synthesisStatusMessage = context.getString(
                        if (text.isBlank()) R.string.tts_preview_empty_error else R.string.tts_preview_invalid_template
                    )
                    return@VoicePickerDialog
                }
                scope.launch {
                    isSynthesizing = true
                    synthesisStatusMessage = context.getString(R.string.tts_synthesizing)
                    val file = ttsManager.ensureSpeech(rendered, previewVoice.name, speechRate)
                    isSynthesizing = false
                    if (file != null && ttsExecutor.playCacheFile(file)) {
                        cacheGeneration++
                        synthesisStatusMessage = null
                    } else {
                        synthesisStatusMessage = context.getString(R.string.tts_synthesis_failed)
                    }
                }
            },
            hasPreviewText = text.isNotBlank(),
            onDismiss = { showVoicePicker = false },
            onOpenTtsSettings = {
                try {
                    val intent = Intent("com.android.settings.TTS_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(stringResource(R.string.tts_section_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                setText(it.text)
                synthesisStatusMessage = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .bringIntoViewOnFocusOrChange(fieldValue.text),
            label = { Text(stringResource(R.string.tts_spoken_message_label)) },
            placeholder = { Text(stringResource(R.string.tts_spoken_message_placeholder)) },
            minLines = 2,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { showVariablePicker = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.variable_picker_insert))
            }
            if (usesSampleData) {
                Text(
                    stringResource(R.string.tts_preview_sample_data),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (hasTemplateError) {
            Text(
                text = templateMismatch?.let { stringResource(R.string.tts_template_trigger_mismatch, it) }
                    ?: stringResource(R.string.tts_preview_invalid_template),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        if (isLoadingVoices) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tts_loading_voices), style = MaterialTheme.typography.bodySmall)
            }
        } else if (voices.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.tts_no_voices_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent("com.android.settings.TTS_SETTINGS")
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Throwable) {}
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tts_open_settings))
                    }
                }
            }
        } else {
            val selectedOption = voices.firstOrNull { it.name == voiceName }
            SelectionRow(
                title = stringResource(R.string.tts_offline_voice_title),
                sub = selectedOption?.displayName ?: stringResource(R.string.tts_system_default),
                click = { showVoicePicker = true }
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(stringResource(R.string.tts_speech_rate_label, "%.1fx".format(speechRate)), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = speechRate,
            onValueChange = {
                setSpeechRate(Math.round(it * 10f) / 10f)
                synthesisStatusMessage = null
            },
            valueRange = 0.5f..2.0f,
            steps = 14,
        )

        Spacer(Modifier.height(6.dp))

        when {
            text.isBlank() && hasLegacyFile -> {
                Text(stringResource(R.string.tts_legacy_file_available), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            text.isBlank() -> Unit
            hasCachedPreview -> {
                Text(stringResource(R.string.tts_cached_ready), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                Text(stringResource(R.string.tts_needs_synthesis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        synthesisStatusMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val result = previewResult
                    when {
                        text.isBlank() -> {
                            synthesisStatusMessage = context.getString(R.string.tts_preview_empty_error)
                        }
                        result == null || result.unknownVariables.isNotEmpty() || result.parseErrors.isNotEmpty() -> {
                            synthesisStatusMessage = context.getString(R.string.tts_preview_invalid_template)
                        }
                        result.text.isBlank() -> {
                            synthesisStatusMessage = context.getString(R.string.tts_preview_renders_empty)
                        }
                        else -> {
                            scope.launch {
                                isSynthesizing = true
                                synthesisStatusMessage = context.getString(R.string.tts_synthesizing)
                                val file = ttsManager.ensureSpeech(result.text, voiceName, speechRate)
                                isSynthesizing = false
                                if (file == null) {
                                    synthesisStatusMessage = context.getString(R.string.tts_synthesis_failed)
                                } else {
                                    cacheGeneration++
                                    synthesisStatusMessage = if (ttsExecutor.playCacheFile(file)) {
                                        null
                                    } else {
                                        context.getString(R.string.tts_playback_failed)
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isSynthesizing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.tts_btn_preview))
            }

            OutlinedButton(
                onClick = { ttsExecutor.stopPreview() },
                modifier = Modifier.weight(0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.tts_btn_stop))
            }
        }
    }
}

@Composable
private fun VoicePickerDialog(
    voices: List<TtsVoiceOption>,
    selectedVoiceName: String,
    onSelect: (TtsVoiceOption) -> Unit,
    onSelectDefault: () -> Unit,
    onPreview: (TtsVoiceOption) -> Unit,
    hasPreviewText: Boolean,
    onDismiss: () -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var previewHint by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val filteredVoices = remember(voices, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isBlank()) {
            voices
        } else {
            voices.filter { voice ->
                val locale = Locale.forLanguageTag(voice.locale)
                listOf(
                    voice.displayName,
                    voice.name,
                    voice.locale,
                    locale.displayName,
                    locale.getDisplayName(Locale.ENGLISH),
                    locale.getDisplayName(Locale.forLanguageTag("tr")),
                ).any { value -> value.lowercase(Locale.ROOT).contains(needle) }
            }
        }
    }
    LaunchedEffect(filteredVoices, selectedVoiceName, query) {
        if (query.isBlank()) {
            if (selectedVoiceName.isBlank()) {
                listState.scrollToItem(1)
            } else {
                filteredVoices.indexOfFirst { it.name == selectedVoiceName }
                    .takeIf { it >= 0 }
                    ?.let { listState.scrollToItem(it + 2) }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.tts_dialog_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.Close, null) } },
                    actions = {
                        IconButton(onOpenTtsSettings) {
                            Icon(Icons.Default.Settings, stringResource(R.string.tts_dialog_tts_settings_desc))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    label = { Text(stringResource(R.string.tts_dialog_search_label)) },
                    placeholder = { Text(stringResource(R.string.tts_dialog_search_placeholder)) },
                    singleLine = true,
                )
                previewHint?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.tts_dialog_banner),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDefault() }
                                .padding(vertical = 14.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.tts_system_default),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    stringResource(R.string.tts_system_default_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selectedVoiceName.isBlank()) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (filteredVoices.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.tts_dialog_no_matches, query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp),
                            )
                        }
                    }
                    items(filteredVoices, key = { it.name }) { voice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onSelect(voice) },
                                    onLongClick = {
                                        if (hasPreviewText) {
                                            previewHint = null
                                            onPreview(voice)
                                        } else {
                                            previewHint = context.getString(R.string.tts_hint_enter_text_first)
                                        }
                                    },
                                )
                                .padding(vertical = 14.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(voice.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(voice.locale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.tts_hold_to_preview), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (voice.name == selectedVoiceName) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
