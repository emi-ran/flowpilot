package com.flowpilot.app.actions

import com.flowpilot.app.data.model.TtsVoiceOption
import java.io.File

/** Pluggable speech synthesis backend. Implementations own the platform TTS lifecycle. */
interface TtsEngine {
    /** Package that produced cached audio; part of the cache key. */
    val enginePackageName: String

    suspend fun availableVoices(): List<TtsVoiceOption>

    /**
     * Synthesizes [text] into [target].
     *
     * @return true only when [target] was written with non-empty audio.
     */
    suspend fun synthesize(text: String, voiceName: String, rate: Float, target: File): Boolean
}
