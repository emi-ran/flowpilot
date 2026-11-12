package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class TemplateDiagnosticHistoryTest {
    @Test
    fun legacyHistoryDefaultsToNoDiagnostics() {
        val old = """{"actionType":"SPEAK_TEXT","actionLabel":"Speak","success":false,"message":"Execution failed"}"""
        val record = Json.decodeFromString<ActionExecutionRecord>(old)
        assertThat(record.templateDiagnostics).isEmpty()
        assertThat(record.diagnostic).isNull()
    }

    @Test
    fun actionDiagnosticSurvivesJsonRoundTrip() {
        val diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING)
        val record = ActionExecutionRecord.create(
            ActionType.NFC_ON,
            ActionResult(false, "Execution failed", diagnostic = diagnostic),
        )
        val encoded = Json.encodeToString(record)

        assertThat(encoded).contains("SHIZUKU_NOT_RUNNING")
        assertThat(Json.decodeFromString<ActionExecutionRecord>(encoded).diagnostic).isEqualTo(diagnostic)
    }

    @Test
    fun structuredDiagnosticsSurviveSerializationWithoutSensitiveInputs() {
        val diagnostic = ActionTemplateDiagnostic(ActionTemplateField.TTS_TEXT, TemplateValueState.EMPTY,
            TemplateValueState.PRESENT, true, true, false, true)
        val record = ActionExecutionRecord.create(ActionType.SPEAK_TEXT, ActionResult(false, "Execution failed"), listOf(diagnostic))
        val history = ExecutionHistoryEntry.create(ruleId = "r", ruleName = "r", trigger = "MANUAL", actions = listOf(record))
        assertThat(history.actions.single().templateDiagnostics).containsExactly(diagnostic)
        val encoded = Json.encodeToString(record)
        assertThat(Json.decodeFromString<ActionExecutionRecord>(encoded).templateDiagnostics).containsExactly(diagnostic)
        assertThat(encoded).doesNotContain("notification content")
        assertThat(encoded).doesNotContain("unknown variable name")
    }
}
