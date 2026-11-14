package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType

/** Per-run, in-memory result; command IDs and payloads are never part of history records. */
internal data class CommandOutcome(
    val success: Boolean,
    val status: String,
    val message: String,
    val id: String = "",
) {
    fun applyTo(context: TemplateContext): TemplateContext = context.copy(
        values = context.values + mapOf(
            "command.success" to if (status == "unverified") "" else success.toString(),
            "command.status" to status,
            "command.message" to message,
            "command.id" to id,
            "command.result" to message,
        ),
    )
}

/** Dispatches only parsed actions and replaces executor messages with payload-free outcomes. */
internal object JsonCommandExecution {
    suspend fun execute(
        input: String,
        dispatch: suspend (ActionType, ActionParameters) -> ActionResult,
    ): CommandOutcome = when (val parsed = JsonCommandParser.parse(input)) {
        is JsonCommandParser.Result.Error -> CommandOutcome(false, "invalid_command", "Invalid or unsupported command")
        is JsonCommandParser.Result.Success -> {
            val result = dispatch(parsed.action, parsed.parameters)
            val status = when {
                result.success && result.resultCode == ActionResultCode.REQUEST_UNVERIFIED -> "unverified"
                result.success -> "success"
                result.resultCode == ActionResultCode.PERMISSION_REQUIRED || result.diagnostic?.reason in permissionDiagnostics -> "permission_required"
                else -> "execution_failed"
            }
            val message = when {
                !result.success && status == "permission_required" -> "Permission required"
                !result.success -> "Command execution failed"
                status == "unverified" -> "Request sent; outcome not verified"
                else -> "Command completed"
            }
            CommandOutcome(result.success, status, message, parsed.id.orEmpty())
        }
    }

    private val permissionDiagnostics = setOf(
        ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED,
        ActionDiagnosticReason.BLUETOOTH_PERMISSION_REQUIRED,
        ActionDiagnosticReason.SETTINGS_PERMISSION_REQUIRED,
        ActionDiagnosticReason.DND_PERMISSION_REQUIRED,
        ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED,
        ActionDiagnosticReason.PHONE_PERMISSION_REQUIRED,
        ActionDiagnosticReason.SMS_PERMISSION_REQUIRED,
    )
}
