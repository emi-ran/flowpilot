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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowpilot.app.R
import com.flowpilot.app.actions.TtsExecutor
import com.flowpilot.app.actions.TtsManager
import com.flowpilot.app.data.model.TtsVoiceOption
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TtsSettings(
    text: String,
    voiceName: String,
    speechRate: Float,
    audioFileName: String,
    ruleId: String,
    setText: (String) -> Unit,
    setVoiceName: (String) -> Unit,
    setSpeechRate: (Float) -> Unit,
    setAudioFileName: (String) -> Unit,
    ttsManager: TtsManager,
    ttsExecutor: TtsExecutor,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var voices by remember { mutableStateOf<List<TtsVoiceOption>>(emptyList()) }
    var isLoadingVoices by remember { mutableStateOf(true) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var synthesisStatusMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(ttsExecutor) {
        onDispose {
            ttsExecutor.stopPreview()
        }
    }

    LaunchedEffect(Unit) {
        isLoadingVoices = true
        voices = ttsManager.getAvailableOfflineVoices()
        isLoadingVoices = false
        if (voiceName.isBlank() && voices.isNotEmpty()) {
            setVoiceName(voices.first().name)
        }
    }

    val cacheFile = remember(audioFileName) {
        if (audioFileName.isNotBlank()) ttsManager.getCacheFile(audioFileName) else null
    }
    val hasValidCache = remember(cacheFile, text, voiceName, speechRate, audioFileName) {
        cacheFile != null && cacheFile.exists() && cacheFile.length() > 0 &&
                audioFileName == ttsManager.computeCacheFileName(ruleId, text.trim(), voiceName, speechRate)
    }

    if (showVoicePicker) {
        VoicePickerDialog(
            voices = voices,
            selectedVoiceName = voiceName,
            onSelect = {
                setVoiceName(it.name)
                showVoicePicker = false
            },
            onPreview = { previewVoice ->
                val trimmed = text.trim()
                if (trimmed.isBlank()) {
                    return@VoicePickerDialog
                }
                scope.launch {
                    isSynthesizing = true
                    synthesisStatusMessage = "Preparing ${previewVoice.displayName}..."
                    val fileName = ttsManager.computeCacheFileName(ruleId, trimmed, previewVoice.name, speechRate)
                    val targetFile = ttsManager.getCacheFile(fileName)
                    val success = targetFile != null && ttsManager.synthesizeToFile(trimmed, previewVoice.name, speechRate, targetFile)
                    isSynthesizing = false
                    synthesisStatusMessage = if (success) {
                        ttsExecutor.playCacheFile(requireNotNull(targetFile))
                        "Previewing ${previewVoice.displayName}"
                    } else {
                        "Voice preview failed. Check offline TTS data."
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
            value = text,
            onValueChange = {
                setText(it)
                synthesisStatusMessage = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .bringIntoViewOnFocusOrChange(text),
            label = { Text(stringResource(R.string.tts_spoken_message_label)) },
            placeholder = { Text(stringResource(R.string.tts_spoken_message_placeholder)) },
            minLines = 2,
        )

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
            val selectedOption = voices.firstOrNull { it.name == voiceName } ?: voices.firstOrNull()
            SelectionRow(
                title = stringResource(R.string.tts_offline_voice_title),
                sub = selectedOption?.displayName ?: voiceName.ifBlank { stringResource(R.string.tts_system_default) },
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

        if (hasValidCache) {
            Text(stringResource(R.string.tts_cached_ready), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(stringResource(R.string.tts_needs_synthesis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        synthesisStatusMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isBlank()) return@Button
                    scope.launch {
                        isSynthesizing = true
                        synthesisStatusMessage = context.getString(R.string.tts_synthesizing)
                        val fileName = ttsManager.computeCacheFileName(ruleId, trimmed, voiceName, speechRate)
                        val targetFile = ttsManager.getCacheFile(fileName)
                        if (targetFile == null) {
                            isSynthesizing = false
                            synthesisStatusMessage = context.getString(R.string.tts_invalid_cache_file)
                            return@launch
                        }
                        val success = ttsManager.synthesizeToFile(trimmed, voiceName, speechRate, targetFile)
                        isSynthesizing = false
                        if (success) {
                            setAudioFileName(fileName)
                            synthesisStatusMessage = context.getString(R.string.tts_synthesis_complete)
                            ttsExecutor.playCacheFile(targetFile)
                        } else {
                            synthesisStatusMessage = context.getString(R.string.tts_synthesis_failed)
                        }
                    }
                },
                enabled = !isSynthesizing && text.isNotBlank() && voices.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (hasValidCache) stringResource(R.string.tts_btn_regenerate) else stringResource(R.string.tts_btn_generate))
            }

            if (hasValidCache) {
                OutlinedButton(
                    onClick = {
                        val file = cacheFile ?: return@OutlinedButton
                        ttsExecutor.playCacheFile(file)
                    },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.tts_btn_preview))
                }
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
                    locale.getDisplayName(Locale("tr")),
                ).any { value -> value.lowercase(Locale.ROOT).contains(needle) }
            }
        }
    }
    LaunchedEffect(filteredVoices, selectedVoiceName, query) {
        if (query.isBlank()) {
            filteredVoices.indexOfFirst { it.name == selectedVoiceName }
                .takeIf { it >= 0 }
                ?.let { listState.scrollToItem(it + 1) }
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
