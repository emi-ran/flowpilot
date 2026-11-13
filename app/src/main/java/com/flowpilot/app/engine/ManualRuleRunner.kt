package com.flowpilot.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
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
 * Executes a saved rule's actions once without evaluating its trigger or conditions.
 *
 * Manual runs deliberately bypass conditions and cooldown, do not change the rule's enabled
 * state or last-trigger time, and append a redacted history entry. The engine's pause state is
 * irrelevant; UI entry points must still reject disabled or deleted rules before calling this.
 */
class ManualRuleRunner(
    private val context: Context,
    private val repository: AutomationRepository = AutomationRepository(context),
) {

    suspend fun run(rule: Automation, language: String): ManualRunResult = withContext(Dispatchers.IO) {
        val liveState = readLiveState()
        val coords = if (rule.requiresLocation()) {
            LocationFetcher.getCoordinates(context)
        } else {
            null
        }
        val templateContext = EventTemplateContext.manual(
            liveState = liveState,
            locationLat = coords?.first,
            locationLng = coords?.second,
        ).copy(values = mapOf("rule.name" to rule.normalizedName))
        val actionParameters = ActionParametersFactory.create(rule, templateContext)
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

                val result = dispatcher.executeAsync(action, actionParameters)
                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                    val redacted = WebhookExecutor.redactSensitiveText(result.message)
                    failureMessages += "${localizedContext.getString(action.labelRes)}: $redacted"
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
