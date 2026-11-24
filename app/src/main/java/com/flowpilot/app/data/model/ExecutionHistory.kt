package com.flowpilot.app.data.model

import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.actions.ActionResult
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.actions.ActionDiagnostic
import com.flowpilot.app.actions.ActionTemplateDiagnostic
import com.flowpilot.app.engine.PhoneNumberUtils
import com.flowpilot.app.diagnostics.DiagnosticText
import com.flowpilot.app.diagnostics.ReasonCode
import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionStatus(val label: String) {
    SUCCESS("Success"),
    PARTIAL("Partial success"),
    FAILURE("Failure");

    companion object {
        fun fromCounts(successCount: Int, failureCount: Int): ExecutionStatus = when {
            failureCount == 0 && successCount > 0 -> SUCCESS
            failureCount > 0 && successCount > 0 -> PARTIAL
            else -> FAILURE
        }
    }
}

@Serializable
data class ActionExecutionRecord(
    val actionType: ActionType,
    val actionLabel: String,
    val success: Boolean,
    val message: String,
    val resultCode: ActionResultCode? = null,
    val resultArgs: List<String> = emptyList(),
    val diagnostic: ActionDiagnostic? = null,
    val templateDiagnostics: List<ActionTemplateDiagnostic> = emptyList(),
    /** [ReasonCode] name; null on records written before diagnostics existed. */
    val reasonCode: String? = null,
    /** Sanitized, bounded, payload-free detail (e.g. "exception=SecurityException"). */
    val detail: String? = null,
    /** Sanitized summary of what the action was configured to do (no personal data). */
    val attempted: String? = null,
    /** Configured delay before this action, in seconds. */
    val delaySeconds: Int? = null,
) {
    val reason: ReasonCode? get() = ReasonCode.fromName(reasonCode)

    companion object {
        fun create(
            actionType: ActionType,
            result: ActionResult,
            templateDiagnostics: List<ActionTemplateDiagnostic> = emptyList(),
            attempted: String? = null,
            delaySeconds: Int? = null,
        ): ActionExecutionRecord = create(
            actionType = actionType,
            success = result.success,
            message = result.message,
            resultCode = result.resultCode,
            resultArgs = result.resultArgs,
            diagnostic = result.diagnostic,
            templateDiagnostics = templateDiagnostics,
            reasonCode = result.reasonCode?.name,
            detail = result.detail,
            attempted = attempted,
            delaySeconds = delaySeconds,
        )

        fun create(
            actionType: ActionType,
            success: Boolean,
            message: String,
            resultCode: ActionResultCode? = null,
            resultArgs: List<String> = emptyList(),
            diagnostic: ActionDiagnostic? = null,
            templateDiagnostics: List<ActionTemplateDiagnostic> = emptyList(),
            reasonCode: String? = null,
            detail: String? = null,
            attempted: String? = null,
            delaySeconds: Int? = null,
        ): ActionExecutionRecord {
            val safeMessage = sanitizeMessage(message, success, actionType)
            val safeArgs = resultArgs.map(WebhookExecutor::redactSensitiveText).map { arg ->
                maskPhoneNumber(arg)
            }
            return ActionExecutionRecord(
                actionType = actionType,
                actionLabel = actionType.label,
                success = success,
                message = safeMessage,
                resultCode = resultCode ?: successCodeFor(actionType, success),
                resultArgs = safeArgs,
                diagnostic = diagnostic,
                templateDiagnostics = templateDiagnostics,
                reasonCode = (ReasonCode.fromName(reasonCode)
                    ?: ReasonCode.derive(success, resultCode, diagnostic?.reason))?.name,
                detail = DiagnosticText.sanitize(detail),
                attempted = DiagnosticText.sanitize(attempted),
                delaySeconds = delaySeconds?.coerceIn(0, 300),
            )
        }

        private fun sanitizeMessage(message: String, success: Boolean, actionType: ActionType): String {
            val redacted = WebhookExecutor.redactSensitiveText(message)
            val masked = maskPhoneNumber(redacted)
            return when {
                masked.isBlank() || masked == "[REDACTED]" -> if (!success) "Execution failed" else actionType.label
                else -> masked
            }
        }

        private fun maskPhoneNumber(text: String): String {
            var result = text
            result = result.replace(Regex("""(?<!\w)(?:\+\d{1,3}[\s-]?)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}(?!\w)""")) { match ->
                PhoneNumberUtils.mask(match.value)
            }
            result = result.replace(Regex("""(?<!\w)\+\d{7,15}(?!\w)""")) { match ->
                PhoneNumberUtils.mask(match.value)
            }
            return result
        }

        internal fun successCodeFor(actionType: ActionType, success: Boolean): ActionResultCode? {
            if (!success) return null
            return when (actionType) {
                ActionType.BLUETOOTH_ON, ActionType.NFC_ON, ActionType.BATTERY_SAVER_ON,
                ActionType.DARK_THEME_ON, ActionType.AUTO_ROTATE_ON, ActionType.DND_ON,
                ActionType.MOBILE_DATA_ON, ActionType.WIFI_ON, ActionType.AIRPLANE_MODE_ON,
                ActionType.TORCH_ON, ActionType.AUTO_BRIGHTNESS_ON, ActionType.LOCATION_ON ->
                    ActionResultCode.STATE_ENABLED
                ActionType.BLUETOOTH_OFF, ActionType.NFC_OFF, ActionType.BATTERY_SAVER_OFF,
                ActionType.DARK_THEME_OFF, ActionType.AUTO_ROTATE_OFF, ActionType.DND_OFF,
                ActionType.MOBILE_DATA_OFF, ActionType.WIFI_OFF, ActionType.AIRPLANE_MODE_OFF,
                ActionType.TORCH_OFF, ActionType.AUTO_BRIGHTNESS_OFF, ActionType.LOCATION_OFF ->
                    ActionResultCode.STATE_DISABLED
                ActionType.SHOW_NOTIFICATION -> ActionResultCode.NOTIFICATION_POSTED
                else -> ActionResultCode.COMPLETED
            }
        }
    }
}

/** True when Android accepted the request but no readback can confirm the final outcome. */
fun ActionExecutionRecord.isUnverifiedRequest(): Boolean = success && (
    resultCode == ActionResultCode.REQUEST_UNVERIFIED ||
        (actionType in UNVERIFIED_BY_DEFAULT && resultCode != ActionResultCode.JSON_CONFIRMED) ||
        (actionType == ActionType.EXECUTE_JSON_COMMAND && resultCode != ActionResultCode.JSON_CONFIRMED)
    )

private val UNVERIFIED_BY_DEFAULT = setOf(
    ActionType.MUSIC_SEARCH, ActionType.MUSIC_PLAY_LINK, ActionType.CREATE_ALARM, ActionType.START_TIMER,
    ActionType.LAUNCH_APP, ActionType.OPEN_URL, ActionType.OPEN_DIALER, ActionType.DIAL_NUMBER,
    ActionType.CALL_NUMBER, ActionType.DRAFT_SMS,
)

/** Converts only known, non-technical legacy outcome text into current structured results. */
fun ActionExecutionRecord.resolvedResultCode(): ActionResultCode? = resultCode ?: when {
    !success -> null
    message == "Notification posted" -> ActionResultCode.NOTIFICATION_POSTED
    actionType == ActionType.LOCATION_ON && message == "Location turned on" -> ActionResultCode.STATE_ENABLED
    actionType == ActionType.LOCATION_OFF && message == "Location turned off" -> ActionResultCode.STATE_DISABLED
    actionType == ActionType.SEND_SMS && message.startsWith("SMS sent to ") -> ActionResultCode.SMS_SENT
    success -> ActionExecutionRecord.Companion.successCodeFor(actionType, true)
    else -> null
}

fun ActionExecutionRecord.resolvedResultArgs(): List<String> = when {
    resultArgs.isNotEmpty() -> resultArgs
    resolvedResultCode() == ActionResultCode.SMS_SENT ->
        listOf(PhoneNumberUtils.mask(message.removePrefix("SMS sent to ")))
    else -> emptyList()
}

/** Extract only an entire, known webhook failure format; never display stored message text. */
fun ActionExecutionRecord.resolvedHttpFailureStatus(): Int? {
    if (success || actionType != ActionType.HTTP_WEBHOOK) return null
    val match = Regex("""(?:HTTP Webhook failed: status ([0-9]{3})|HTTP ([0-9]{3}) from upstream)""")
        .matchEntire(message) ?: return null
    return (match.groups[1]?.value ?: match.groups[2]?.value)?.toIntOrNull()?.takeIf { it in 300..599 }
}

@Serializable
data class ExecutionHistoryEntry(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val trigger: String,
    val timestamp: Long,
    val status: ExecutionStatus,
    val actions: List<ActionExecutionRecord>,
    /** One-based ordered OR source; null for legacy/manual entries. Contains no payload. */
    val triggerSourceIndex: Int? = null,
    /** Epoch millis when the run began; null for older entries. */
    val startedAt: Long? = null,
    /** Run duration including configured delays; null for older entries. */
    val durationMs: Long? = null,
    /** Condition type names (no values) the rule had at run time. */
    val conditionTypes: List<String> = emptyList(),
) {
    /** Normalize only legacy generated SMS snapshots, never today's rule name. */
    val normalizedRuleName: String
        get() = if ((trigger == TriggerEvent.SMS_RECEIVED.name || trigger == "MANUAL") &&
            Automation.LEGACY_SMS_GENERATED_NAME.matches(ruleName)
        ) {
            "SMS Received · ${ruleName.substringAfter(" · ")}"
        } else {
            ruleName
        }

    fun sanitized(): ExecutionHistoryEntry {
        val safeRuleName = WebhookExecutor.redactSensitiveText(normalizedRuleName)
        val safeTrigger = WebhookExecutor.redactSensitiveText(trigger)
        val sanitizedActions = actions.map { action ->
            ActionExecutionRecord.create(
                actionType = action.actionType,
                success = action.success,
                message = action.message,
                resultCode = action.resultCode,
                resultArgs = action.resultArgs,
                diagnostic = action.diagnostic,
                templateDiagnostics = action.templateDiagnostics,
                reasonCode = action.reasonCode,
                detail = action.detail,
                attempted = action.attempted,
                delaySeconds = action.delaySeconds,
            )
        }
        return copy(
            ruleName = safeRuleName,
            trigger = safeTrigger,
            actions = sanitizedActions,
            durationMs = durationMs?.coerceIn(0L, 86_400_000L),
            conditionTypes = conditionTypes
                .filter { name -> com.flowpilot.app.data.model.ConditionType.entries.any { it.name == name } }
                .take(20),
        )
    }

    companion object {
        fun create(
            id: String = java.util.UUID.randomUUID().toString(),
            ruleId: String,
            ruleName: String,
            trigger: String,
            timestamp: Long = System.currentTimeMillis(),
            actions: List<ActionExecutionRecord>,
            triggerSourceIndex: Int? = null,
            startedAt: Long? = null,
            durationMs: Long? = null,
            conditionTypes: List<String> = emptyList(),
        ): ExecutionHistoryEntry {
            val successCount = actions.count { it.success }
            val failureCount = actions.count { !it.success }
            val status = ExecutionStatus.fromCounts(successCount, failureCount)
            val entry = ExecutionHistoryEntry(
                id = id,
                ruleId = ruleId,
                ruleName = ruleName,
                trigger = trigger,
                timestamp = timestamp,
                status = status,
                actions = actions,
                triggerSourceIndex = triggerSourceIndex,
                startedAt = startedAt,
                durationMs = durationMs,
                conditionTypes = conditionTypes,
            )
            return entry.sanitized()
        }
    }
}
