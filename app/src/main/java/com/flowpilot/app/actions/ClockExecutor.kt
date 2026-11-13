package com.flowpilot.app.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.flowpilot.app.data.model.ActionType

/**
 * Executes Alarm and Timer actions (CREATE_ALARM, START_TIMER).
 *
 * Uses standard Android intents:
 * - AlarmClock.ACTION_SET_ALARM (EXTRA_HOUR, EXTRA_MINUTES, EXTRA_MESSAGE)
 * - AlarmClock.ACTION_SET_TIMER (EXTRA_LENGTH, EXTRA_MESSAGE, EXTRA_SKIP_UI = true)
 * Both carry FLAG_ACTIVITY_NEW_TASK.
 * Resolves intent before starting. START_TIMER requests background execution via EXTRA_SKIP_UI = true;
 * CREATE_ALARM leaves UI behavior to the system Clock app.
 * Result accurately reports that the request was dispatched to the system Clock app.
 */
class ClockExecutor(
    private val context: Context,
    private val intentResolver: (Context, Intent) -> ComponentName? = { ctx, intent ->
        intent.resolveActivity(ctx.packageManager)
    },
    private val activityStarter: (Context, Intent) -> Unit = { ctx, intent ->
        ctx.startActivity(intent)
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.CREATE_ALARM, ActionType.START_TIMER)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        return when (action) {
            ActionType.CREATE_ALARM -> createAlarm(parameters)
            ActionType.START_TIMER -> startTimer(parameters)
            else -> ActionResult(false, "Unsupported action for Clock")
        }
    }

    private fun createAlarm(parameters: ActionParameters): ActionResult {
        val hour = parameters.alarmHour
        val minute = parameters.alarmMinute
        if (hour !in 0..23 || minute !in 0..59) {
            return ActionResult(false, "Invalid alarm time", diagnostic = ActionDiagnostic(ActionDiagnosticReason.INVALID_INPUT))
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (parameters.alarmMessage.isNotBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, parameters.alarmMessage.trim())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intentResolver(context, intent) == null) {
            return ActionResult(false, "No Clock application available to handle alarm creation", diagnostic = ActionDiagnostic(ActionDiagnosticReason.HANDLER_UNAVAILABLE))
        }

        return try {
            activityStarter(context, intent)
            ActionResult(true, "Alarm request sent to Clock app")
        } catch (_: ActivityNotFoundException) {
            ActionResult(false, "No Clock application available to handle alarm creation", diagnostic = ActionDiagnostic(ActionDiagnosticReason.HANDLER_UNAVAILABLE))
        } catch (_: Throwable) {
            ActionResult(false, "Unable to send alarm request")
        }
    }

    private fun startTimer(parameters: ActionParameters): ActionResult {
        val durationSeconds = parameters.timerDurationSeconds
        // Bounded duration: 1 second up to 24 hours (86400 seconds)
        if (durationSeconds < 1 || durationSeconds > 86400) {
            return ActionResult(false, "Invalid timer duration (must be 1s to 24h)", diagnostic = ActionDiagnostic(ActionDiagnosticReason.INVALID_INPUT))
        }

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (parameters.timerMessage.isNotBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, parameters.timerMessage.trim())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intentResolver(context, intent) == null) {
            return ActionResult(false, "No Clock application available to handle timer start", diagnostic = ActionDiagnostic(ActionDiagnosticReason.HANDLER_UNAVAILABLE))
        }

        return try {
            activityStarter(context, intent)
            ActionResult(true, "Timer request sent to Clock app")
        } catch (_: ActivityNotFoundException) {
            ActionResult(false, "No Clock application available to handle timer start", diagnostic = ActionDiagnostic(ActionDiagnosticReason.HANDLER_UNAVAILABLE))
        } catch (_: Throwable) {
            ActionResult(false, "Unable to send timer request")
        }
    }
}
