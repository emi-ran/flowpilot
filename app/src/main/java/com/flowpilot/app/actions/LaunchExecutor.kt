package com.flowpilot.app.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.flowpilot.app.data.model.ActionType

/** Launches an installed app or a validated web URL through Android intent resolution. */
class LaunchExecutor(
    private val context: Context,
    private val launch: (Intent) -> Unit = { context.startActivity(it) },
) : ActionExecutor {
    override val supportedTypes = setOf(ActionType.LAUNCH_APP, ActionType.OPEN_URL)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult = try {
        Log.i(TAG, "Executing ${action.name}")
        val intent = when (action) {
            ActionType.LAUNCH_APP -> {
                if (parameters.launchPackage.isBlank()) return ActionResult(false, "Choose an app to launch")
                context.packageManager.getLaunchIntentForPackage(parameters.launchPackage)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: return ActionResult(false, "Selected app is not installed or launchable")
            }
            ActionType.OPEN_URL -> {
                val uri = Uri.parse(parameters.url.trim())
                if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
                    return ActionResult(false, "Enter a valid http or https URL")
                }
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
                    if (it.resolveActivity(context.packageManager) == null) return ActionResult(false, "No app can open this URL")
                }
            }
            else -> return ActionResult(false, "Unsupported launch action")
        }
        launch(intent)
        Log.i(TAG, "Started ${action.name}")
        ActionResult(true, if (action == ActionType.LAUNCH_APP) "App launched" else "URL opened")
    } catch (_: Throwable) {
        Log.w(TAG, "Failed ${action.name}")
        ActionResult(false, "Unable to complete ${action.name.lowercase().replace('_', ' ')}")
    }

    private companion object {
        const val TAG = "FlowPilotLaunch"
    }
}
