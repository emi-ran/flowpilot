package com.flowpilot.app.actions

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.flowpilot.app.data.model.TtsVoiceOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * [TtsEngine] backed by the platform [TextToSpeech] service.
 *
 * Every operation creates a short-lived service binding and shuts it down afterwards so the
 * manager can stay a singleton without leaking a speech engine.
 */
class AndroidTtsEngine(context: Context) : TtsEngine {

    private val appContext = context.applicationContext

    override val enginePackageName: String by lazy {
        try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
                ?.takeIf { it.isNotBlank() }
                ?: FALLBACK_PACKAGE
        } catch (_: Throwable) {
            FALLBACK_PACKAGE
        }
    }

    override suspend fun availableVoices(): List<TtsVoiceOption> = withContext(Dispatchers.IO) {
        var ttsInstance: TextToSpeech? = null
        try {
            val tts = bindService() ?: return@withContext emptyList()
            ttsInstance = tts

            val voices = try {
                tts.voices
            } catch (_: Throwable) {
                null
            }
            if (voices.isNullOrEmpty()) return@withContext emptyList()

            voices
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
        } catch (_: Throwable) {
            emptyList()
        } finally {
            shutdownQuietly(ttsInstance)
        }
    }

    override suspend fun synthesize(
        text: String,
        voiceName: String,
        rate: Float,
        target: File,
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false

        // The caller supplies a temporary destination; clear stale bytes before asking TTS to write.
        try {
            if (target.exists() && !target.delete()) return@withContext false
        } catch (_: Throwable) {
            return@withContext false
        }

        var ttsInstance: TextToSpeech? = null
        try {
            val tts = bindService() ?: return@withContext false
            ttsInstance = tts

            val voice = selectOfflineVoice(tts, voiceName) ?: return@withContext false
            tts.voice = voice
            tts.setSpeechRate(rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE))

            val utteranceId = "tts_${System.nanoTime()}"
            val synthesis = CompletableDeferred<Boolean>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId) synthesis.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) synthesis.complete(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) synthesis.complete(false)
                }
            })

            if (tts.synthesizeToFile(text, null, target, utteranceId) != TextToSpeech.SUCCESS) {
                return@withContext false
            }

            val completed = withTimeoutOrNull(synthesisTimeoutMs(text)) {
                synthesis.await()
            } ?: false
            completed && target.isFile && target.length() > 0
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            false
        } finally {
            try {
                ttsInstance?.stop()
            } catch (_: Throwable) {
            }
            shutdownQuietly(ttsInstance)
        }
    }

    /** Binds a TTS instance, or null when initialization fails or exceeds [INIT_TIMEOUT_MS]. */
    private suspend fun bindService(): TextToSpeech? {
        val ready = CompletableDeferred<Boolean>()
        var instance: TextToSpeech? = null
        instance = TextToSpeech(appContext) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: false
        val tts = instance
        if (!initialized || tts == null) {
            shutdownQuietly(tts)
            return null
        }
        return tts
    }

    private fun selectOfflineVoice(tts: TextToSpeech, voiceName: String): Voice? {
        if (voiceName.isNotBlank() && voiceName != DEFAULT_VOICE_NAME) {
            val matched = try {
                tts.voices?.firstOrNull { it.name == voiceName }
            } catch (_: Throwable) {
                null
            }
            // A requested voice must exist and must not require a network connection.
            return matched?.takeUnless { it.isNetworkConnectionRequired }
        }

        val defaultVoice = try {
            tts.defaultVoice
        } catch (_: Throwable) {
            null
        }
        if (defaultVoice != null && !defaultVoice.isNetworkConnectionRequired) {
            return defaultVoice
        }

        // The platform default may be a network voice (or absent). Stay offline by choosing the
        // best available voice for the same language, then any offline voice.
        val offlineVoices = try {
            tts.voices?.filterNot { it.isNetworkConnectionRequired }
        } catch (_: Throwable) {
            null
        }
        val preferredLanguage = (defaultVoice?.locale ?: Locale.getDefault()).language
        return offlineVoices
            ?.filter { it.locale.language == preferredLanguage }
            ?.maxByOrNull { it.quality }
            ?: offlineVoices?.maxByOrNull { it.quality }
    }

    private fun shutdownQuietly(tts: TextToSpeech?) {
        try {
            tts?.shutdown()
        } catch (_: Throwable) {
        }
    }

    private fun synthesisTimeoutMs(text: String): Long {
        val perLength = ((text.length + CHARS_PER_SECOND - 1) / CHARS_PER_SECOND) * 1_000L
        return maxOf(MIN_SYNTHESIS_TIMEOUT_MS, perLength).coerceAtMost(MAX_SYNTHESIS_TIMEOUT_MS)
    }

    private companion object {
        const val FALLBACK_PACKAGE = "android.tts"
        const val DEFAULT_VOICE_NAME = "default"
        const val INIT_TIMEOUT_MS = 6_000L
        const val MIN_SYNTHESIS_TIMEOUT_MS = 15_000L
        const val MAX_SYNTHESIS_TIMEOUT_MS = 60_000L
        const val CHARS_PER_SECOND = 100
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
    }
}
