package com.flowpilot.app.diagnostics

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionNode

/**
 * Derives payload-free summaries from a rule for history storage and reports. Only enumerated
 * settings, numbers and app package names are emitted; every free-text or personal field
 * (SMS, phone, webhook, notification, JSON source, TTS text, URLs, Wi-Fi, Bluetooth) is skipped.
 */
object RuleDiagnostics {
    private const val MAX_CONDITIONS = 20

    /** Non-sensitive parameter summary of what an action was configured to do, or null. */
    fun attemptedSummary(rule: Automation, action: ActionType): String? {
        val raw: String? = when (action) {
            ActionType.SET_MEDIA_VOLUME -> "percent=${rule.mediaVolumePercent}"
            ActionType.SET_NOTIFICATION_VOLUME -> "percent=${rule.notificationVolumePercent}"
            ActionType.SET_ALARM_VOLUME -> "percent=${rule.alarmVolumePercent}"
            ActionType.SET_SCREEN_BRIGHTNESS -> "percent=${rule.screenBrightnessPercent}"
            ActionType.VIBRATE -> "pattern=${rule.vibrationPattern.name}, durationMs=${rule.vibrationDurationMs}, amplitude=${rule.vibrationAmplitude}"
            ActionType.PLAY_SOUND -> "preset=${rule.soundPreset.name}, durationMs=${rule.soundDurationMs}"
            ActionType.LAUNCH_APP -> rule.launchPackage.takeIf { it.isNotBlank() }?.let { "package=$it" }
            ActionType.FORCE_STOP_APP -> rule.forceStopPackage.takeIf { it.isNotBlank() }?.let { "package=$it" }
            ActionType.MEDIA_PLAY, ActionType.MEDIA_PAUSE, ActionType.MEDIA_TOGGLE,
            ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS ->
                "targetPackage=" + rule.mediaTargetPackage.ifBlank { "(active player)" }
            ActionType.MUSIC_SEARCH, ActionType.MUSIC_PLAY_LINK ->
                rule.musicAppPackage.takeIf { it.isNotBlank() }?.let { "appPackage=$it" }
            ActionType.START_TIMER -> "durationSeconds=${rule.timerDurationSeconds}"
            ActionType.CREATE_ALARM -> "time=%02d:%02d, days=%s".format(rule.alarmHour, rule.alarmMinute, rule.alarmDays.joinToString(",").ifEmpty { "once" })
            ActionType.CREATE_CALENDAR_EVENT -> "repeat=${rule.calendarRepeat}, reminderMinutes=${rule.calendarReminderMinutes}"
            ActionType.HTTP_WEBHOOK -> "method=${rule.webhookMethod}, timeoutSeconds=${rule.webhookTimeoutSeconds}, url=${DiagnosticText.REDACTED}"
            ActionType.SPEAK_TEXT -> "speechRate=${rule.ttsSpeechRate}, text=${DiagnosticText.REDACTED}"
            ActionType.SHOW_NOTIFICATION -> "title=${DiagnosticText.REDACTED}, body=${DiagnosticText.REDACTED}"
            ActionType.EXECUTE_JSON_COMMAND -> "source=${DiagnosticText.REDACTED}"
            ActionType.OPEN_URL -> "url=${DiagnosticText.REDACTED}"
            ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> "number=${DiagnosticText.REDACTED}"
            ActionType.SEND_SMS, ActionType.DRAFT_SMS -> "recipient=${DiagnosticText.REDACTED}, message=${DiagnosticText.REDACTED}"
            else -> null
        }
        return DiagnosticText.sanitize(raw)
    }

    /** Condition type names (never their values such as SSIDs), flattened, bounded. */
    fun conditionTypes(rule: Automation): List<String> {
        val tree = rule.conditionTree
        val types = if (tree != null) leaves(tree) else rule.conditions.map { it.type.name }
        return types.take(MAX_CONDITIONS)
    }

    private fun leaves(node: ConditionNode): List<String> = when (node) {
        is ConditionNode.Leaf -> listOf(node.condition.type.name)
        is ConditionNode.Group -> node.children.flatMap { leaves(it) }
    }
}
