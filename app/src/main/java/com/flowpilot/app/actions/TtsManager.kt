package com.flowpilot.app.actions

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.flowpilot.app.data.model.TtsVoiceOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Offline-first TTS synthesis manager and audio cache resolver. */
class TtsManager(private val context: Context) {

    private val ttsDir: File by lazy {
        File(context.filesDir, "tts_cache").apply { if (!exists()) mkdirs() }
    }

    suspend fun getAvailableOfflineVoices(): List<TtsVoiceOption> = withContext(Dispatchers.IO) {
        val ttsDeferred = CompletableDeferred<TextToSpeech?>()
        var ttsInstance: TextToSpeech? = null
        try {
            ttsInstance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsDeferred.complete(ttsInstance)
                } else {
                    ttsDeferred.complete(null)
                }
            }
            val tts = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { ttsDeferred.await() }
            if (tts == null) return@withContext emptyList<TtsVoiceOption>()

            val voices = try {
                tts.voices
            } catch (_: Throwable) {
                null
            }

            if (voices.isNullOrEmpty()) {
                return@withContext emptyList<TtsVoiceOption>()
            }

            val offlineVoices = voices
                .filter { voice -> !voice.isNetworkConnectionRequired }
                .map { voice ->
                    val loc = voice.locale ?: Locale.getDefault()
                    val quality = when (voice.quality) {
                        Voice.QUALITY_VERY_HIGH -> "Very High Quality"
                        Voice.QUALITY_HIGH -> "High Quality"
                        Voice.QUALITY_NORMAL -> "Normal Quality"
                        Voice.QUALITY_LOW -> "Low Quality"
                        Voice.QUALITY_VERY_LOW -> "Very Low Quality"
                        else -> ""
                    }
                    val label = buildString {
                        append(loc.displayName.ifBlank { voice.name })
                        if (quality.isNotBlank()) append(" · ").append(quality)
                        if (voice.name.isNotBlank()) append(" (${voice.name})")
                    }
                    TtsVoiceOption(
                        name = voice.name,
                        locale = loc.toLanguageTag(),
                        displayName = label,
                    )
                }
                .sortedBy { it.displayName.lowercase() }

            offlineVoices
        } catch (_: Throwable) {
            emptyList()
        } finally {
            try {
                ttsInstance?.shutdown()
            } catch (_: Throwable) {}
        }
    }

    fun computeCacheFileName(ruleId: String, text: String, voiceName: String, rate: Float): String {
        val input = "$ruleId|$text|$voiceName|$rate"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)
        val safeRule = ruleId.filter { it.isLetterOrDigit() || it == '-' }.take(20).ifBlank { "preview" }
        return "tts_${safeRule}_${hash}.wav"
    }

    private val cacheFileNameRegex = Regex("^tts_[A-Za-z0-9-]+_[0-9a-f]{16}\\.wav$")

    fun isValidCacheFileName(fileName: String): Boolean {
        return cacheFileNameRegex.matches(fileName)
    }

    fun getCacheFile(fileName: String): File? {
        if (!isValidCacheFileName(fileName)) return null
        val file = File(ttsDir, fileName)
        // Ensure path remains strictly within ttsDir and cannot traverse
        val canonicalDir = ttsDir.canonicalFile
        val canonicalTarget = file.canonicalFile
        if (canonicalTarget.parentFile != canonicalDir) return null
        return file
    }

    suspend fun synthesizeToFile(
        text: String,
        voiceName: String,
        rate: Float,
        targetFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false
        if (!isValidCacheFileName(targetFile.name)) return@withContext false
        val cacheFile = getCacheFile(targetFile.name) ?: return@withContext false
        if (cacheFile.canonicalFile != targetFile.canonicalFile) return@withContext false

        val tempFile = File(ttsDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val ttsDeferred = CompletableDeferred<TextToSpeech?>()
        var ttsInstance: TextToSpeech? = null
        try {
            ttsInstance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsDeferred.complete(ttsInstance)
                } else {
                    ttsDeferred.complete(null)
                }
            }

            val tts = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { ttsDeferred.await() } ?: return@withContext false

            if (voiceName.isNotBlank() && voiceName != "default") {
                val matchedVoice = try {
                    tts.voices?.firstOrNull { it.name == voiceName }
                } catch (_: Throwable) { null }

                // Non-default voice requested: must exist and must not require network
                if (matchedVoice == null || matchedVoice.isNetworkConnectionRequired) {
                    return@withContext false
                }
                tts.voice = matchedVoice
            } else {
                // Default voice requested: check that default voice exists and is offline
                val defVoice = try { tts.defaultVoice } catch (_: Throwable) { null }
                    ?: return@withContext false
                if (defVoice.isNetworkConnectionRequired) {
                    return@withContext false
                }
                tts.voice = defVoice
            }

            tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))

            val utteranceId = "tts_${System.currentTimeMillis()}"
            val synthDeferred = CompletableDeferred<Boolean>()

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId) synthDeferred.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) synthDeferred.complete(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) synthDeferred.complete(false)
                }
            })

            val synthResult = tts.synthesizeToFile(text, null, tempFile, utteranceId)
            if (synthResult != TextToSpeech.SUCCESS) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext false
            }

            val completed = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) { synthDeferred.await() } ?: false
            if (completed && tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                true
            } else {
                if (tempFile.exists()) tempFile.delete()
                false
            }
        } catch (_: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            false
        } finally {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (_: Throwable) {}
        }
    }

    fun cleanStaleFiles(activeFileNames: Set<String>) {
        try {
            val files = ttsDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && !activeFileNames.contains(file.name)) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TTS_INIT_TIMEOUT_MS = 6_000L
        private const val SYNTHESIS_TIMEOUT_MS = 15_000L
    }
}
