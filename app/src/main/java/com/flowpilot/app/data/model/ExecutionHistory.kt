package com.flowpilot.app.data.model

import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.actions.ActionResult
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.engine.PhoneNumberUtils
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
) {
    companion object {
        fun create(actionType: ActionType, result: ActionResult): ActionExecutionRecord = create(
            actionType = actionType,
            success = result.success,
            message = result.message,
            resultCode = result.resultCode,
            resultArgs = result.resultArgs,
        )

        fun create(
            actionType: ActionType,
            success: Boolean,
            message: String,
            resultCode: ActionResultCode? = null,
            resultArgs: List<String> = emptyList(),
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

@Serializable
data class ExecutionHistoryEntry(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val trigger: String,
    val timestamp: Long,
    val status: ExecutionStatus,
    val actions: List<ActionExecutionRecord>,
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

    companion object {
        fun create(
            id: String = java.util.UUID.randomUUID().toString(),
            ruleId: String,
            ruleName: String,
            trigger: String,
            timestamp: Long = System.currentTimeMillis(),
            actions: List<ActionExecutionRecord>,
        ): ExecutionHistoryEntry {
            val successCount = actions.count { it.success }
            val failureCount = actions.count { !it.success }
            val status = ExecutionStatus.fromCounts(successCount, failureCount)
            val safeRuleName = WebhookExecutor.redactSensitiveText(ruleName)
            val safeTrigger = WebhookExecutor.redactSensitiveText(trigger)
            val sanitizedActions = actions.map { action ->
                ActionExecutionRecord.create(
                    actionType = action.actionType,
                    success = action.success,
                    message = action.message,
                    resultCode = action.resultCode,
                    resultArgs = action.resultArgs,
                )
            }
            return ExecutionHistoryEntry(
                id = id,
                ruleId = ruleId,
                ruleName = safeRuleName,
                trigger = safeTrigger,
                timestamp = timestamp,
                status = status,
                actions = sanitizedActions,
            )
        }
    }
}
