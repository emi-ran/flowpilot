package com.flowpilot.app.data.model

import com.flowpilot.app.actions.WebhookExecutor
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
) {
    companion object {
        fun create(
            actionType: ActionType,
            success: Boolean,
            message: String,
        ): ActionExecutionRecord {
            val safeMessage = WebhookExecutor.redactSensitiveText(message)
            return ActionExecutionRecord(
                actionType = actionType,
                actionLabel = actionType.label,
                success = success,
                message = safeMessage,
            )
        }
    }
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
