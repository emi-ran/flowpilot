package com.flowpilot.app.actions

import android.content.Context
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.engine.ActiveNotificationRef
import com.flowpilot.app.engine.FlowPilotNotificationListener
import com.flowpilot.app.engine.selectNotificationKeysToDismiss
import com.flowpilot.app.permission.CapabilityManager

/**
 * Dismisses the notification that triggered a NOTIFICATION_RECEIVED rule (or every active
 * notification of that app) through the connected notification listener.
 *
 * The notification key arrives only through the transient [ActionParameters]; neither the key nor
 * any notification content is logged or placed in results.
 */
class DismissNotificationExecutor internal constructor(
    private val hasAccess: () -> Boolean,
    private val activeNotifications: () -> List<ActiveNotificationRef>?,
    /** Returns false when no listener is connected. */
    private val cancel: (List<String>) -> Boolean,
    private val listenerConnected: () -> Boolean,
) : ActionExecutor {
    constructor(context: Context) : this(
        hasAccess = { CapabilityManager(context).hasNotificationListenerAccess() },
        activeNotifications = { FlowPilotNotificationListener.activeNotificationRefs() },
        cancel = { FlowPilotNotificationListener.cancelNotificationKeys(it) },
        listenerConnected = { FlowPilotNotificationListener.isConnected },
    )

    override val supportedTypes = setOf(ActionType.DISMISS_NOTIFICATION)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.DISMISS_NOTIFICATION) return ActionResult(false, "Unsupported dismiss action")
        if (parameters.triggerNotificationKey.isBlank()) {
            return ActionResult(
                false,
                "Dismiss notification works only for rules triggered by a received notification",
                diagnostic = ActionDiagnostic(ActionDiagnosticReason.NOT_NOTIFICATION_TRIGGER),
            )
        }
        return try {
            if (!hasAccess()) {
                return ActionResult(
                    false, "Notification listener access required", ActionResultCode.PERMISSION_REQUIRED,
                    diagnostic = ActionDiagnostic(ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED),
                )
            }
            if (!listenerConnected()) return listenerDisconnected()
            val active = if (parameters.dismissScope == com.flowpilot.app.data.model.NotificationDismissScope.ALL_FROM_PACKAGE) {
                activeNotifications() ?: return listenerDisconnected()
            } else {
                emptyList()
            }
            val keys = selectNotificationKeysToDismiss(
                parameters.dismissScope,
                parameters.triggerNotificationKey,
                parameters.triggerNotificationPackage,
                active,
            )
            if (keys.isEmpty()) {
                return ActionResult(false, "No notification to dismiss", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
            }
            if (!cancel(keys)) return listenerDisconnected()
            // cancelNotification is asynchronous and gives no acknowledgement.
            ActionResult(true, "Dismiss request sent; removal not verified")
        } catch (_: SecurityException) {
            ActionResult(
                false, "Notification listener access required", ActionResultCode.PERMISSION_REQUIRED,
                diagnostic = ActionDiagnostic(ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED),
            )
        } catch (_: Exception) {
            ActionResult(false, "Dismiss notification failed")
        }
    }

    private fun listenerDisconnected() = ActionResult(
        false,
        "Notification listener is not connected",
        diagnostic = ActionDiagnostic(ActionDiagnosticReason.NOTIFICATION_LISTENER_DISCONNECTED),
    )
}
