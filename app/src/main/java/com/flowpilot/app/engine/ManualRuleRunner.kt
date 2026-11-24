package com.flowpilot.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.flowpilot.app.R
import com.flowpilot.app.actions.ActionDiagnosticReason
import com.flowpilot.app.data.model.ActionType
import android.os.BatteryManager
import android.os.PowerManager
import com.flowpilot.app.actions.ActionDispatcher
import com.flowpilot.app.actions.ActionParametersFactory
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.actions.TemplateDiagnostics
import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.ui.util.labelRes
import com.flowpilot.app.ui.util.localizedForAppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ManualRunResult(
    val totalActions: Int,
    val successCount: Int,
    val failureCount: Int,
    val failureMessages: List<String>,
)

/**
 * Executes a rule's actions once without evaluating its trigger or conditions.
 *
 * Manual runs bypass conditions and cooldown, do not change the rule's enabled state or
 * last-trigger time, and append a redacted history entry. Draft runs may be unsaved or disabled;
 * saved shortcuts instead require the confirmed revision to remain enabled at each dispatch.
 */
class ManualRuleRunner(
    private val context: Context,
    private val repository: AutomationRepository = AutomationRepository(context),
) {

    suspend fun run(rule: Automation, language: String): ManualRunResult =
        runInternal(rule, language, savedShortcut = false)

    suspend fun runSavedShortcut(rule: Automation, language: String): ManualRunResult =
        runInternal(rule, language, savedShortcut = true)

    private suspend fun runInternal(rule: Automation, language: String, savedShortcut: Boolean): ManualRunResult = withContext(Dispatchers.IO) {
        val liveState = readLiveState()
        val coords = if (rule.requiresLocation()) {
            LocationFetcher.getCoordinates(context)
        } else {
            null
        }
        var templateContext = EventTemplateContext.manual(
            liveState = liveState,
            locationLat = coords?.first,
            locationLng = coords?.second,
        ).copy(values = mapOf("rule.name" to rule.normalizedName))
        val dispatcher = ActionDispatcher.get(context)
        val localizedContext = context.localizedForAppLanguage(language)
        var successCount = 0
        var failureCount = 0
        val failureMessages = mutableListOf<String>()
        val actionRecords = mutableListOf<ActionExecutionRecord>()

        val actions = rule.effectiveActions
        val delays = rule.effectiveActionDelays
        var cancellation: CancellationException? = null
        try {
            for (index in actions.indices) {
                val action = actions[index]
                val delaySeconds = delays.getOrElse(index) { 0 }

                if (delaySeconds > 0) {
                    try {
                        delay(delaySeconds * 1000L)
                    } catch (ce: CancellationException) {
                        failureCount++
                        val message = "Execution cancelled during ${delaySeconds}s delay"
                        failureMessages += "${localizedContext.getString(action.labelRes)}: $message"
                        actionRecords += ActionExecutionRecord.create(
                            actionType = action,
                            success = false,
                            message = message,
                            resultCode = ActionResultCode.EXECUTION_CANCELLED,
                        )
                        throw ce
                    }
                }

                val actionParameters = ActionParametersFactory.create(rule, templateContext)
                suspend fun dispatch() = if (action == com.flowpilot.app.data.model.ActionType.EXECUTE_JSON_COMMAND) {
                    val outcome = com.flowpilot.app.actions.JsonCommandExecution.execute(actionParameters.jsonCommandSource, actionParameters.jsonCommandToken) { nestedAction, nestedParameters ->
                        dispatcher.executeAsync(nestedAction, nestedParameters)
                    }
                    templateContext = outcome.applyTo(templateContext)
                    com.flowpilot.app.actions.ActionResult(outcome.success, outcome.message, if (outcome.status == "unverified") com.flowpilot.app.actions.ActionResultCode.REQUEST_UNVERIFIED else if (outcome.success) com.flowpilot.app.actions.ActionResultCode.JSON_CONFIRMED else null)
                } else {
                    dispatcher.executeAsync(action, actionParameters)
                }
                val result = if (savedShortcut) {
                    repository.dispatchSavedRuleIfAuthorized(rule.id, rule.executionRevision) { dispatch() }
                } else {
                    dispatch()
                }
                if (result == null) {
                    failureCount++
                    val message = "Execution revoked before dispatch"
                    failureMessages += "${localizedContext.getString(action.labelRes)}: $message"
                    actionRecords += ActionExecutionRecord.create(
                        actionType = action,
                        success = false,
                        message = message,
                        resultCode = ActionResultCode.EXECUTION_CANCELLED,
                    )
                    break
                }
                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                    val volumeDiagnostic = if (action == ActionType.SET_MEDIA_VOLUME || action == ActionType.SET_NOTIFICATION_VOLUME || action == ActionType.SET_ALARM_VOLUME) {
                        when (result.diagnostic?.reason) {
                            ActionDiagnosticReason.VOLUME_PERMISSION_REQUIRED -> R.string.history_diagnostic_volume_permission_required
                            ActionDiagnosticReason.VOLUME_BELOW_MINIMUM -> R.string.history_diagnostic_volume_below_minimum
                            ActionDiagnosticReason.STATE_UNAVAILABLE -> R.string.history_diagnostic_state_unavailable
                            ActionDiagnosticReason.STATE_MISMATCH -> R.string.history_diagnostic_state_mismatch
                            ActionDiagnosticReason.SYSTEM_COMMAND_FAILED -> R.string.history_diagnostic_system_command_failed
                            else -> null
                        }
                    } else null
                    val message = volumeDiagnostic?.let(localizedContext::getString)
                        ?: WebhookExecutor.redactSensitiveText(result.message)
                    failureMessages += "${localizedContext.getString(action.labelRes)}: $message"
                }
                actionRecords += ActionExecutionRecord.create(action, result, TemplateDiagnostics.forAction(action, rule, templateContext, actionParameters))
            }
        } catch (ce: CancellationException) {
            cancellation = ce
        } finally {
            val historyEntry = ExecutionHistoryEntry.create(
                ruleId = rule.id,
                ruleName = rule.normalizedName,
                trigger = "MANUAL",
                timestamp = System.currentTimeMillis(),
                actions = actionRecords,
            )
            withContext(NonCancellable) {
                repository.appendHistory(historyEntry)
            }
        }

        val summary = ManualRunResult(
            totalActions = actions.size,
            successCount = successCount,
            failureCount = failureCount,
            failureMessages = failureMessages,
        )
        cancellation?.let { throw it }
        summary
    }

    private fun readLiveState(): LiveSystemState {
        val batteryIntent = try {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else null
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wifiSsid = try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            WifiStateTracker.queryCurrentSsid(connectivityManager, wifiManager)
        } catch (_: Throwable) {
            null
        }
        return LiveSystemState(
            batteryPercent = batteryPercent,
            isChargerConnected = plugged != 0,
            isScreenOn = powerManager?.isInteractive,
            connectedWifiSsid = wifiSsid,
        )
    }
}
