package com.flowpilot.app.actions

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.flowpilot.app.data.model.ActionType

/**
 * Executes Do Not Disturb actions (DND_ON, DND_OFF).
 *
 * Uses NotificationManager.setInterruptionFilter:
 * - DND_ON: NotificationManager.INTERRUPTION_FILTER_NONE (total silence / Do Not Disturb on)
 * - DND_OFF: NotificationManager.INTERRUPTION_FILTER_ALL (all interruptions / Do Not Disturb off)
 *
 * Requires Notification Policy Access (android.permission.ACCESS_NOTIFICATION_POLICY),
 * checked via NotificationManager.isNotificationPolicyAccessGranted.
 * Reads back currentInterruptionFilter to verify the setting changed honestly.
 */
class DndExecutor(
    private val context: Context,
    private val policyAccessChecker: (Context) -> Boolean = { ctx ->
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.isNotificationPolicyAccessGranted == true
    },
    private val interruptionFilterWriter: (Context, Int) -> Unit = { ctx, filter ->
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(filter)
    },
    private val interruptionFilterReader: (Context) -> Int = { ctx ->
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.currentInterruptionFilter
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.DND_ON, ActionType.DND_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val targetFilter = when (action) {
            ActionType.DND_ON -> NotificationManager.INTERRUPTION_FILTER_NONE
            ActionType.DND_OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
            else -> return ActionResult(false, "Unsupported action for Do Not Disturb")
        }

        if (!policyAccessChecker(context)) {
            return ActionResult(
                false,
                "Do Not Disturb requires Notification Policy Access permission",
            )
        }

        return try {
            interruptionFilterWriter(context, targetFilter)
            val readBack = interruptionFilterReader(context)
            if (readBack != targetFilter) {
                return ActionResult(
                    false,
                    "Do Not Disturb state mismatch: requested $targetFilter but read back $readBack",
                )
            }

            val message = if (targetFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                "Do Not Disturb turned on"
            } else {
                "Do Not Disturb turned off"
            }
            ActionResult(true, message)
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
