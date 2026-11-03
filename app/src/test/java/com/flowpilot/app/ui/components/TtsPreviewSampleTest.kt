package com.flowpilot.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TtsPreviewSampleTest {

    @Test
    fun sampleFollowsTheSelectedVoiceLanguage() {
        assertThat(TtsPreviewSample.forLanguageTag("ja-JP")).contains("今日")
        assertThat(TtsPreviewSample.forLanguageTag("en-US")).isEqualTo("TTS how are you today?")
        assertThat(TtsPreviewSample.forLanguageTag("tr-TR")).isEqualTo("TTS bugün nasılsın?")
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertThat(TtsPreviewSample.forLanguageTag("xx-YY"))
            .isEqualTo(TtsPreviewSample.forLanguageTag("en"))
    }
}
