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
            "command.success" to success.toString(),
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
                result.success -> "success"
                result.resultCode == ActionResultCode.PERMISSION_REQUIRED -> "permission_required"
                else -> "execution_failed"
            }
            val message = when {
                !result.success && status == "permission_required" -> "Permission required"
                !result.success -> "Command execution failed"
                parsed.action == ActionType.MUSIC_SEARCH || parsed.action == ActionType.MUSIC_PLAY_LINK -> "Music app opened; playback not verified"
                else -> "Command completed"
            }
            CommandOutcome(result.success, status, message, parsed.id.orEmpty())
        }
    }
}
