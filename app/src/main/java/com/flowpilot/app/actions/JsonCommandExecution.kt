package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.diagnostics.ReasonCode

/** Per-run, in-memory result; command IDs and payloads are never part of history records. */
internal data class CommandOutcome(
    val success: Boolean,
    val status: String,
    val message: String,
    val id: String = "",
    val reasonCode: ReasonCode? = null,
    val detail: String? = null,
) {
    /** The single mapping from a command outcome to the history-facing action result. */
    fun toActionResult(): ActionResult = ActionResult(
        success = success,
        message = message,
        resultCode = if (status == "unverified") ActionResultCode.REQUEST_UNVERIFIED
        else if (success) ActionResultCode.JSON_CONFIRMED else null,
        reasonCode = reasonCode,
        detail = detail,
    )

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
        is JsonCommandParser.Result.Error -> CommandOutcome(
            false, "invalid_command", "Invalid or unsupported command",
            reasonCode = when (parsed.reason) {
                JsonCommandParser.ErrorReason.INVALID_JSON -> ReasonCode.JSON_PARSE_ERROR
                JsonCommandParser.ErrorReason.INPUT_TOO_LARGE -> ReasonCode.JSON_INPUT_TOO_LARGE
                JsonCommandParser.ErrorReason.INVALID_SCHEMA -> ReasonCode.JSON_INVALID_SCHEMA
                JsonCommandParser.ErrorReason.UNSUPPORTED_VERSION -> ReasonCode.JSON_UNSUPPORTED_VERSION
                JsonCommandParser.ErrorReason.UNSUPPORTED_ACTION -> ReasonCode.JSON_UNSUPPORTED_ACTION
                JsonCommandParser.ErrorReason.INVALID_PARAMETERS -> ReasonCode.JSON_INVALID_PARAMETERS
            },
            detail = "parser=" + parsed.reason.name,
        )
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
            val nestedReason = if (result.success) null else
                result.reasonCode ?: ReasonCode.derive(false, result.resultCode, result.diagnostic?.reason)
            val reason = when {
                result.success && status == "unverified" -> ReasonCode.REQUEST_UNVERIFIED
                result.success -> null
                // Permission/runtime-capability causes stay specific so the suggested fix is actionable.
                nestedReason != null && nestedReason != ReasonCode.UNKNOWN -> nestedReason
                else -> ReasonCode.JSON_DISPATCH_FAILED
            }
            val detail = "nestedAction=${parsed.action.name}" +
                (if (!result.success) ", nestedReason=${(nestedReason ?: ReasonCode.UNKNOWN).name}" else "")
            CommandOutcome(result.success, status, message, parsed.id.orEmpty(), reason, detail)
        }
    }

    private val permissionDiagnostics = setOf(
        ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED,
        ActionDiagnosticReason.BLUETOOTH_PERMISSION_REQUIRED,
        ActionDiagnosticReason.SETTINGS_PERMISSION_REQUIRED,
        ActionDiagnosticReason.DND_PERMISSION_REQUIRED,
        ActionDiagnosticReason.VOLUME_PERMISSION_REQUIRED,
        ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED,
        ActionDiagnosticReason.PHONE_PERMISSION_REQUIRED,
        ActionDiagnosticReason.SMS_PERMISSION_REQUIRED,
    )
}
