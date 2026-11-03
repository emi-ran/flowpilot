package com.flowpilot.app.ui.components

import java.util.Locale

/**
 * Demo sentence for the editor preview. Android's accessibility TTS settings read a sample in the
 * selected voice's language; FlowPilot mirrors that so a Japanese voice is not asked to pronounce a
 * Turkish sentence. The `TTS` prefix keeps the template filter example (`stripPrefix:"tts"`) useful.
 */
internal object TtsPreviewSample {

    private val samples = mapOf(
        "ar" to "TTS كيف حالك اليوم؟",
        "de" to "TTS wie geht es dir heute?",
        "en" to "TTS how are you today?",
        "es" to "TTS ¿cómo estás hoy?",
        "fr" to "TTS comment vas-tu aujourd'hui ?",
        "hi" to "TTS आज आप कैसे हैं?",
        "id" to "TTS apa kabar hari ini?",
        "it" to "TTS come stai oggi?",
        "ja" to "TTS 今日はお元気ですか？",
        "ko" to "TTS 오늘 기분은 어때요?",
        "nl" to "TTS hoe gaat het vandaag met je?",
        "pl" to "TTS jak się dzisiaj masz?",
        "pt" to "TTS como você está hoje?",
        "ru" to "TTS как ты сегодня?",
        "tr" to "TTS bugün nasılsın?",
        "vi" to "TTS hôm nay bạn thế nào?",
        "zh" to "TTS 你今天好吗？",
    )

    private const val FALLBACK_LANGUAGE = "en"

    fun forLanguageTag(languageTag: String): String {
        val language = Locale.forLanguageTag(languageTag).language
        return samples[language] ?: samples.getValue(FALLBACK_LANGUAGE)
    }
}
