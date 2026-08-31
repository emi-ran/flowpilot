package com.flowpilot.app.actions

import android.content.Context
import com.flowpilot.app.data.model.ActionType

/** Routes an action to the executor that can perform it. */
class ActionDispatcher private constructor(
    private val nfc: NfcExecutor,
    private val powerSaver: PowerSaverExecutor,
    private val notification: NotificationExecutor,
) {
    private val map: Map<ActionType, ActionExecutor> by lazy {
        listOf(nfc, powerSaver, notification).flatMap { e -> e.supportedTypes.map { it to e } }.toMap()
    }

    fun execute(action: ActionType, notificationTitle: String = "", notificationBody: String = ""): ActionResult {
        val executor = map[action] ?: return ActionResult(false, "No executor for ${action.label}")
        return executor.execute(action, notificationTitle, notificationBody)
    }

    companion object {
        @Volatile
        private var instance: ActionDispatcher? = null

        fun get(context: Context): ActionDispatcher =
            instance ?: synchronized(this) {
                instance ?: ActionDispatcher(
                    NfcExecutor(ShizukuShell.instance),
                    PowerSaverExecutor(context.applicationContext, ShizukuShell.instance),
                    NotificationExecutor(context.applicationContext),
                ).also { instance = it }
            }
    }
}
