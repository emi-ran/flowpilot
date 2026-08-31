package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.VibrationPattern

/** Result of attempting to execute an action. Kept honest — never claims success on a no-op. */
data class ActionResult(
    val success: Boolean,
    val message: String,
)

data class ActionParameters(
    val notificationTitle: String = "",
    val notificationBody: String = "",
    val vibrationPattern: VibrationPattern = VibrationPattern.PULSE,
    val vibrationDurationMs: Int = 220,
    val vibrationAmplitude: Int = 180,
    val launchPackage: String = "",
    val url: String = "",
)

/** A capability-aware executor for one family of system actions. */
interface ActionExecutor {
    val supportedTypes: Set<ActionType>
    fun execute(action: ActionType, parameters: ActionParameters = ActionParameters()): ActionResult
}
