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
            val safeMessage = WebhookExecutor.redactSensitiveText(message)
            val safeArgs = resultArgs.map(WebhookExecutor::redactSensitiveText).let { args ->
                if (resultCode == ActionResultCode.SMS_SENT) args.map(PhoneNumberUtils::mask) else args
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
            return ExecutionHistoryEntry(
                id = id,
                ruleId = ruleId,
                ruleName = ruleName,
                trigger = trigger,
                timestamp = timestamp,
                status = status,
                actions = actions,
            )
        }
    }
}
