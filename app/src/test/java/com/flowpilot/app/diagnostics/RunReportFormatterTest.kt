package com.flowpilot.app.diagnostics

import com.flowpilot.app.actions.ActionDiagnostic
import com.flowpilot.app.actions.ActionDiagnosticReason
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class RunReportFormatterTest {
    private val env = ReportEnvironment("2.3.0", 9, "debug", "14", 34, "Google", "Pixel 8")

    private val secrets = listOf(
        "+905551234567", "SECRET_SMS_BODY_TEXT", "https://hooks.example.com/abc/SECRET_HOOK_PATH",
        "X-Api-Key: SECRET_HEADER_VALUE", "SECRET_WEBHOOK_BODY", "SECRET_NOTIF_TITLE", "SECRET_NOTIF_TEXT",
        "SECRET_JSON_SOURCE", "SECRET_TTS_TEXT", "MyHomeWifiSSID", "AA:BB:CC:DD:EE:FF", "SECRET_CLIPBOARD_TEXT",
        "123456 is your code", "sender@example.com",
    )

    private val detailSecrets = listOf(
        "+905551234567", "https://hooks.example.com/abc/SECRET_HOOK_PATH", "X-Api-Key: SECRET_HEADER_VALUE",
        "AA:BB:CC:DD:EE:FF", "123456 is your code", "sender@example.com",
    )

    private fun sensitiveRule() = Automation(
        id = "r1", name = "Night mode", createdAt = 0L,
        triggerEvent = TriggerEvent.SMS_RECEIVED,
        smsSenderFilter = "+905551234567", smsKeyword = "SECRET_SMS_BODY_TEXT",
        wifiSsid = "MyHomeWifiSSID", bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF",
        notificationTitle = "SECRET_NOTIF_TITLE", notificationBody = "SECRET_NOTIF_TEXT",
        webhookUrl = "https://hooks.example.com/abc/SECRET_HOOK_PATH", webhookHeaders = "X-Api-Key: SECRET_HEADER_VALUE",
        webhookBody = "SECRET_WEBHOOK_BODY", jsonCommandSource = "SECRET_JSON_SOURCE", ttsText = "SECRET_TTS_TEXT",
        phoneNumber = "+905551234567", smsRecipient = "+905551234567", smsMessage = "SECRET_SMS_BODY_TEXT",
        url = "https://hooks.example.com/abc/SECRET_HOOK_PATH",
        conditions = listOf(RuleCondition(ConditionType.WIFI_CONNECTED, wifiSsid = "MyHomeWifiSSID")),
        actions = listOf(
            ActionType.SHOW_NOTIFICATION, ActionType.HTTP_WEBHOOK, ActionType.EXECUTE_JSON_COMMAND,
            ActionType.SPEAK_TEXT, ActionType.SEND_SMS, ActionType.CALL_NUMBER, ActionType.OPEN_URL,
            ActionType.SET_MEDIA_VOLUME, ActionType.LAUNCH_APP,
        ),
        actionDelays = listOf(0, 2, 0, 0, 0, 0, 0, 5, 0),
        mediaVolumePercent = 40, launchPackage = "com.example.app",
    )

    private fun entryFor(rule: Automation): ExecutionHistoryEntry {
        val records = rule.effectiveActions.mapIndexed { i, a ->
            ActionExecutionRecord.create(
                actionType = a,
                success = false,
                // The report never prints free-text messages; detail is sanitized for pattern-detectable data.
                message = secrets.joinToString(" | "),
                detail = detailSecrets.joinToString(" "),
                attempted = RuleDiagnostics.attemptedSummary(rule, a),
                delaySeconds = rule.effectiveActionDelays[i],
                diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED),
            )
        }
        return ExecutionHistoryEntry.create(
            ruleId = rule.id, ruleName = rule.name, trigger = rule.triggerEvent.name,
            actions = records, startedAt = 1_000L, durationMs = 7_500L,
            conditionTypes = RuleDiagnostics.conditionTypes(rule),
        )
    }

    @Test fun reportNeverLeaksSensitiveCategories() {
        val rule = sensitiveRule()
        val report = RunReportFormatter.format(entryFor(rule), env)
        val entry = entryFor(rule)
        val stored = entry.actions.joinToString("|") { "${it.detail}|${it.attempted}|${it.reasonCode}" }
        secrets.forEach { secret ->
            assertFalse("report leaked: $secret", report.contains(secret))
        }
        (secrets + detailSecrets).forEach { secret ->
            assertFalse("stored diagnostics leaked: $secret", stored.contains(secret))
        }
        assertFalse(stored.contains("123456"))
        listOf("SECRET_", "5551234567", "hooks.example.com", "MyHomeWifiSSID", "sender@example.com").forEach {
            assertFalse("report leaked fragment: $it", report.contains(it))
        }
    }

    @Test fun reportContainsStructureEnvironmentAndCodes() {
        val rule = sensitiveRule()
        val report = RunReportFormatter.format(entryFor(rule), env)
        assertTrue(report.contains("- App: 2.3.0 (9) debug"))
        assertTrue(report.contains("- Android: 14 (API 34)"))
        assertTrue(report.contains("- Device: Google Pixel 8"))
        assertTrue(report.contains("- Trigger type: SMS_RECEIVED"))
        assertTrue(report.contains("- Conditions: WIFI_CONNECTED"))
        assertTrue(report.contains("- Duration: 7500 ms"))
        assertTrue(report.contains("8. SET_MEDIA_VOLUME | delay=5s | params: percent=40"))
        assertTrue(report.contains("9. LAUNCH_APP | delay=0s | params: package=com.example.app"))
        assertTrue(report.contains("- reasonCode: SYSTEM_COMMAND_FAILED"))
        assertTrue(report.contains("- diagnostic: SYSTEM_COMMAND_FAILED"))
        assertTrue(report.contains("1. SHOW_NOTIFICATION | delay=0s | params: title=[redacted], body=[redacted]"))
    }

    @Test fun unverifiedSuccessIsExplainedAsNoReadback() {
        val entry = ExecutionHistoryEntry.create(
            ruleId = "r", ruleName = "Launch", trigger = "MANUAL",
            actions = listOf(ActionExecutionRecord.create(ActionType.LAUNCH_APP, true, "ok")),
        )
        val report = RunReportFormatter.format(entry, env)
        assertTrue(report.contains("UNVERIFIED"))
        assertTrue(report.contains("no readback"))
        assertTrue(report.contains("REQUEST_UNVERIFIED") || report.contains("UNVERIFIED (accepted, no readback)"))
    }

    @Test fun oldHistoryWithoutNewFieldsStillDeserializesWithDefaults() {
        val old = """
            {"id":"1","ruleId":"r","ruleName":"Old","trigger":"MANUAL","timestamp":5,"status":"FAILURE",
             "actions":[{"actionType":"TORCH_ON","actionLabel":"Turn Flashlight on","success":false,
             "message":"No diagnostic information available"}]}
        """.trimIndent()
        val entry = Json { ignoreUnknownKeys = true }.decodeFromString(ExecutionHistoryEntry.serializer(), old)
        assertNull(entry.startedAt)
        assertNull(entry.durationMs)
        assertTrue(entry.conditionTypes.isEmpty())
        val action = entry.actions.single()
        assertNull(action.reasonCode)
        assertNull(action.attempted)
        assertNull(action.delaySeconds)
        // Migration/sanitization derives a stable code instead of "no diagnostic information".
        assertEquals("UNKNOWN", entry.sanitized().actions.single().reasonCode)
        // Report of an old entry still renders.
        assertTrue(RunReportFormatter.format(entry.sanitized(), env).contains("reasonCode: UNKNOWN"))
    }

    @Test fun newFieldsRoundTripAndAreBounded() {
        val rec = ActionExecutionRecord.create(
            ActionType.TORCH_ON, false, "x", reasonCode = ReasonCode.SHIZUKU_NOT_RUNNING.name,
            detail = "a".repeat(1000), attempted = "b".repeat(1000), delaySeconds = 9999,
        )
        assertEquals(DiagnosticText.MAX_LENGTH, rec.detail!!.length)
        assertEquals(DiagnosticText.MAX_LENGTH, rec.attempted!!.length)
        assertEquals(300, rec.delaySeconds)
        val json = Json { encodeDefaults = true }
        val back = json.decodeFromString(ActionExecutionRecord.serializer(), json.encodeToString(ActionExecutionRecord.serializer(), rec))
        assertEquals(rec, back)
        assertEquals(ReasonCode.SHIZUKU_NOT_RUNNING, back.reason)
    }

    @Test fun reasonMappingCoversEveryDiagnosticAndResultCode() {
        ActionDiagnosticReason.entries.forEach { assertNotNull(ReasonCode.fromDiagnostic(it)) }
        assertEquals(ReasonCode.SHIZUKU_NOT_RUNNING, ReasonCode.fromDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING))
        assertEquals(ReasonCode.SHIZUKU_PERMISSION_MISSING, ReasonCode.fromDiagnostic(ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED))
        assertEquals(ReasonCode.PERMISSION_MISSING, ReasonCode.fromDiagnostic(ActionDiagnosticReason.SMS_PERMISSION_REQUIRED))
        assertNull(ReasonCode.derive(true, ActionResultCode.COMPLETED, null))
        assertEquals(ReasonCode.REQUEST_UNVERIFIED, ReasonCode.derive(true, ActionResultCode.REQUEST_UNVERIFIED, null))
        assertEquals(ReasonCode.PERMISSION_MISSING, ReasonCode.derive(false, ActionResultCode.PERMISSION_REQUIRED, null))
        assertEquals(ReasonCode.CANCELLED, ReasonCode.derive(false, ActionResultCode.EXECUTION_CANCELLED, null))
        assertEquals(ReasonCode.UNKNOWN, ReasonCode.derive(false, null, null))
        assertNull(ReasonCode.fromName("NOT_A_CODE"))
    }

    @Test fun diagnosticTextRedactsAndRestrictsCharacters() {
        val out = DiagnosticText.sanitize("call +90 555 123 45 67 token=abc123 https://x.io/p?q=1 a@b.com <b>")!!
        listOf("555", "abc123", "x.io", "a@b.com", "<").forEach { assertFalse(it, out.contains(it)) }
        assertNull(DiagnosticText.sanitize("   "))
    }
}
