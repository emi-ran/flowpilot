package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType

/** Result of attempting to execute an action. Kept honest — never claims success on a no-op. */
data class ActionResult(
    val success: Boolean,
    val message: String,
)

/** A capability-aware executor for one family of system actions. */
interface ActionExecutor {
    val supportedTypes: Set<ActionType>
    fun execute(action: ActionType, notificationTitle: String = "", notificationBody: String = ""): ActionResult
}
