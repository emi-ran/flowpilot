package com.flowpilot.app.actions

import kotlinx.serialization.Serializable
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation

/** Presence of a notification value before any template filters are applied. */
@Serializable
enum class TemplateValueState { MISSING, EMPTY, PRESENT }

/** Content-free summary; neither template, values, renderer output nor errors are retained. */
data class TemplateAnalysis(
    val notificationText: TemplateValueState,
    val notificationBigText: TemplateValueState,
    val hasParseErrors: Boolean,
    val hasUnknownVariables: Boolean,
    val analysisFailed: Boolean,
)

@Serializable
enum class ActionTemplateField {
    NOTIFICATION_TITLE, NOTIFICATION_BODY, MUSIC_QUERY, MUSIC_LINK, URL, TTS_TEXT,
    ALARM_MESSAGE, TIMER_MESSAGE, WEBHOOK_URL, WEBHOOK_HEADERS, WEBHOOK_BODY,
    PHONE_NUMBER, SMS_RECIPIENT, SMS_MESSAGE,
}

/** Only fixed field identifiers and content-free states are persisted. */
@Serializable
data class ActionTemplateDiagnostic(
    val field: ActionTemplateField,
    val notificationText: TemplateValueState,
    val notificationBigText: TemplateValueState,
    val hasParseErrors: Boolean,
    val hasUnknownVariables: Boolean,
    val analysisFailed: Boolean,
    val renderedEmpty: Boolean,
)

object TemplateDiagnostics {
    /** Analyze only fields consumed by the dispatched action; never retain source or output. */
    fun forAction(action: ActionType, rule: Automation, context: TemplateContext, parameters: ActionParameters): List<ActionTemplateDiagnostic> {
        val fields = when (action) {
            ActionType.SHOW_NOTIFICATION -> listOf(Triple(ActionTemplateField.NOTIFICATION_TITLE, rule.notificationTitle, parameters.notificationTitle), Triple(ActionTemplateField.NOTIFICATION_BODY, rule.notificationBody, parameters.notificationBody))
            ActionType.MUSIC_SEARCH -> listOf(Triple(ActionTemplateField.MUSIC_QUERY, rule.musicQuery, parameters.musicQuery))
            ActionType.MUSIC_PLAY_LINK -> listOf(Triple(ActionTemplateField.MUSIC_LINK, rule.musicLink, parameters.musicLink))
            ActionType.OPEN_URL -> listOf(Triple(ActionTemplateField.URL, rule.url, parameters.url))
            ActionType.SPEAK_TEXT -> if (rule.ttsText.isBlank() && rule.ttsAudioFileName.isNotBlank()) emptyList() else listOf(Triple(ActionTemplateField.TTS_TEXT, rule.ttsText, parameters.ttsText))
            ActionType.CREATE_ALARM -> listOf(Triple(ActionTemplateField.ALARM_MESSAGE, rule.alarmMessage, parameters.alarmMessage))
            ActionType.START_TIMER -> listOf(Triple(ActionTemplateField.TIMER_MESSAGE, rule.timerMessage, parameters.timerMessage))
            ActionType.HTTP_WEBHOOK -> listOf(Triple(ActionTemplateField.WEBHOOK_URL, rule.webhookUrl, parameters.webhookUrl), Triple(ActionTemplateField.WEBHOOK_HEADERS, rule.webhookHeaders, parameters.webhookHeaders), Triple(ActionTemplateField.WEBHOOK_BODY, rule.webhookBody, parameters.webhookBody))
            ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> listOf(Triple(ActionTemplateField.PHONE_NUMBER, rule.phoneNumber, parameters.phoneNumber))
            ActionType.SEND_SMS, ActionType.DRAFT_SMS -> listOf(Triple(ActionTemplateField.SMS_RECIPIENT, rule.smsRecipient, parameters.smsRecipient), Triple(ActionTemplateField.SMS_MESSAGE, rule.smsMessage, parameters.smsMessage))
            else -> emptyList()
        }
        return fields.map { (field, template, rendered) ->
            val analysis = analyze(template, context)
            ActionTemplateDiagnostic(field, analysis.notificationText, analysis.notificationBigText, analysis.hasParseErrors, analysis.hasUnknownVariables, analysis.analysisFailed, rendered.isBlank())
        }
    }

    /** Uses the renderer's own parser and resolution rules without changing rendering behavior. */
    fun analyze(template: String, context: TemplateContext?): TemplateAnalysis {
        val text = valueState(context, "notification.text")
        val bigText = valueState(context, "notification.bigText")
        return try {
            // A caller-provided clock can have side effects or throw; its value cannot affect parsing.
            val safeContext = context?.copy(timeProvider = { "" })
            val result = TemplateRenderer.renderResult(template, safeContext, RenderMode.STRICT)
            TemplateAnalysis(text, bigText, result.parseErrors.isNotEmpty(), result.unknownVariables.isNotEmpty(), false)
        } catch (_: Exception) {
            TemplateAnalysis(text, bigText, false, false, true)
        }
    }

    private fun valueState(context: TemplateContext?, key: String): TemplateValueState {
        val value = context?.values?.get(key) ?: return TemplateValueState.MISSING
        return if (value.isEmpty()) TemplateValueState.EMPTY else TemplateValueState.PRESENT
    }
}
