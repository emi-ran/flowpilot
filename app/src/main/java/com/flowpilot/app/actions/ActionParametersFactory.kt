package com.flowpilot.app.actions

import com.flowpilot.app.data.model.Automation

/**
 * Single place that turns a rule's stored fields into executable [ActionParameters].
 *
 * Every user-facing text field is rendered exactly once, here. Executors receive already
 * rendered values and must never run the template engine again.
 *
 * TTS is strict: an unknown variable, a malformed expression, or an empty rendered result
 * produces a generic [ActionParameters.ttsTemplateError] so TtsExecutor can fail without
 * exposing template content. Every other field renders leniently and keeps unknown tokens.
 */
object ActionParametersFactory {

    /** Generic failure shown when a rule's TTS template cannot produce speakable text. */
    const val TTS_TEMPLATE_ERROR = "TTS text could not be resolved"

    /**
     * Marker used when a rendered header value contains line breaks or control characters.
     * The webhook header validator rejects this line, so injected lines never reach the wire.
     */
    private const val INVALID_RENDERED_HEADER = "X-FlowPilot-Invalid: \u007F"

    fun create(rule: Automation, context: TemplateContext = TemplateContext()): ActionParameters {
        val tts = renderTts(rule, context)
        return ActionParameters(
            notificationTitle = render(rule.notificationTitle, context),
            notificationBody = render(rule.notificationBody, context),
            vibrationPattern = rule.vibrationPattern,
            vibrationDurationMs = rule.vibrationDurationMs,
            vibrationAmplitude = rule.vibrationAmplitude,
            mediaVolumePercent = rule.mediaVolumePercent,
            mediaTargetPackage = rule.mediaTargetPackage,
            musicAppPackage = rule.musicAppPackage,
            musicQuery = render(rule.musicQuery, context),
            musicLink = render(rule.musicLink, context),
            soundPreset = rule.soundPreset,
            soundUri = rule.soundUri,
            soundDurationMs = rule.soundDurationMs,
            launchPackage = rule.launchPackage,
            url = render(rule.url, context),
            ttsText = tts.text,
            ttsVoiceName = rule.ttsVoiceName,
            ttsSpeechRate = rule.ttsSpeechRate,
            ttsAudioFileName = rule.ttsAudioFileName,
            ttsTemplateError = tts.error,
            alarmHour = rule.alarmHour,
            alarmMinute = rule.alarmMinute,
            alarmMessage = render(rule.alarmMessage, context),
            timerDurationSeconds = rule.timerDurationSeconds,
            timerMessage = render(rule.timerMessage, context),
            webhookMethod = rule.webhookMethod,
            webhookUrl = render(rule.webhookUrl, context),
            webhookHeaders = renderHeaders(rule.webhookHeaders, context),
            webhookBody = render(rule.webhookBody, context),
            webhookTimeoutSeconds = rule.webhookTimeoutSeconds,
            phoneNumber = render(rule.phoneNumber, context),
            screenBrightnessPercent = rule.screenBrightnessPercent,
            forceStopPackage = rule.forceStopPackage,
            smsRecipient = resolveSmsRecipient(rule, context),
            smsMessage = render(rule.smsMessage, context),
        )
    }

    private fun render(template: String, context: TemplateContext): String =
        TemplateRenderer.render(template, context, RenderMode.LENIENT)

    /**
     * Headers are rendered one configured line at a time so a variable value cannot introduce a
     * new header. A poisoned line is replaced with [INVALID_RENDERED_HEADER]; the executor's
     * post-render validation then rejects the request without echoing the value.
     */
    private fun renderHeaders(rawHeaders: String, context: TemplateContext): String {
        if (rawHeaders.isBlank()) return rawHeaders
        return rawHeaders.split("\r\n", "\n", "\r").joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line
            } else {
                val rendered = render(line, context)
                if (rendered.any { it == '\r' || it == '\n' || it.isISOControl() }) {
                    INVALID_RENDERED_HEADER
                } else {
                    rendered
                }
            }
        }
    }

    private fun resolveSmsRecipient(rule: Automation, context: TemplateContext): String {
        val rendered = render(rule.smsRecipient, context)
        val fallback = if (rendered.isBlank()) context.smsSender.orEmpty() else rendered
        return fallback.trim()
    }

    private data class RenderedTts(val text: String, val error: String?)

    private fun renderTts(rule: Automation, context: TemplateContext): RenderedTts {
        if (rule.ttsText.isBlank()) {
            return if (rule.ttsAudioFileName.isBlank()) {
                RenderedTts(text = "", error = TTS_TEMPLATE_ERROR)
            } else {
                RenderedTts(text = "", error = null)
            }
        }
        val result = TemplateRenderer.renderResult(rule.ttsText, context, RenderMode.STRICT)
        if (result.unknownVariables.isNotEmpty() || result.parseErrors.isNotEmpty()) {
            return RenderedTts(text = "", error = TTS_TEMPLATE_ERROR)
        }
        if (result.text.isBlank()) {
            return RenderedTts(text = "", error = TTS_TEMPLATE_ERROR)
        }
        return RenderedTts(text = result.text, error = null)
    }
}
