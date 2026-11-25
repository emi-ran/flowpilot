package com.flowpilot.app.diagnostics

import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.ExecutionStatus
import com.flowpilot.app.data.model.isUnverifiedRequest
import com.flowpilot.app.data.model.resolvedHttpFailureStatus
import java.time.Instant

/** Device and app facts included in a report. All values are non-personal build metadata. */
data class ReportEnvironment(
    val appVersionName: String,
    val appVersionCode: Long,
    val buildType: String,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
)

/**
 * Builds the plain-text/Markdown run report a user can paste to a coding agent. Pure Kotlin:
 * reads only the already-sanitized history entry, never free-text action messages, and applies
 * a second redaction pass to every string it prints.
 */
object RunReportFormatter {
    fun statusExplanation(entry: ExecutionHistoryEntry): String = when {
        entry.status == ExecutionStatus.SUCCESS && entry.actions.any { it.isUnverifiedRequest() } ->
            "UNVERIFIED: every action was accepted by Android, but Android only confirms the request " +
                "was sent; there is no readback, so it may still have been blocked or ignored."
        entry.status == ExecutionStatus.SUCCESS -> "SUCCESS: every action reported success."
        entry.status == ExecutionStatus.PARTIAL -> "PARTIAL: some actions succeeded and at least one failed."
        else -> "FAILURE: no action succeeded."
    }

    fun format(entry: ExecutionHistoryEntry, env: ReportEnvironment): String = buildString {
        fun line(text: String = "") = append(text).append('\n')
        line("# FlowPilot run report")
        line()
        line("## Environment")
        line("- App: ${safe(env.appVersionName)} (${env.appVersionCode}) ${safe(env.buildType)}")
        line("- Android: ${safe(env.androidRelease)} (API ${env.sdkInt})")
        line("- Device: ${safe(env.manufacturer)} ${safe(env.model)}")
        line()
        line("## Run")
        line("- Rule: ${name(entry.ruleName)}")
        line("- Run id: ${safe(entry.id)}")
        line("- Started: ${Instant.ofEpochMilli(entry.startedAt ?: entry.timestamp)}" +
            (if (entry.startedAt == null) " (finish time; start unknown)" else ""))
        line("- Duration: ${entry.durationMs?.let { "$it ms" } ?: "unknown"}")
        line("- Trigger: ${safe(entry.trigger)}" +
            (entry.triggerSourceIndex?.let { ", source index $it" } ?: ""))
        line("- Status: ${statusExplanation(entry)}")
        line()
        line("## Rule structure")
        line("- Trigger type: ${safe(entry.trigger)}")
        line("- Conditions: ${entry.conditionTypes.joinToString(", ") { safe(it) }.ifEmpty { "none recorded" }}")
        line("- Actions:")
        entry.actions.forEachIndexed { i, a ->
            line("  ${i + 1}. ${a.actionType.name}" +
                " | delay=${a.delaySeconds?.let { "${it}s" } ?: "unknown"}" +
                " | params: ${a.attempted?.let(::safe) ?: "none recorded"}")
        }
        line()
        line("## Action results")
        if (entry.actions.isEmpty()) line("- (no actions recorded)")
        entry.actions.forEachIndexed { i, a -> appendAction(i + 1, a) }
        line()
        line("_Privacy: phone numbers, SMS content, webhook data, notification text, JSON command text, " +
            "tokens, clipboard text and network identifiers are never stored or included._")
    }

    private fun StringBuilder.appendAction(index: Int, a: ActionExecutionRecord) {
        fun line(text: String) = append(text).append('\n')
        val state = when {
            a.isUnverifiedRequest() -> "UNVERIFIED (accepted, no readback)"
            a.success -> "SUCCESS"
            else -> "FAILED"
        }
        line("### $index. ${a.actionType.name}: $state")
        val code = a.reason
        if (code != null) {
            line("- reasonCode: ${code.name}")
            line("- meaning: ${code.description}")
        } else if (a.reasonCode != null) {
            line("- reasonCode: ${safe(a.reasonCode)}")
        }
        a.resultCode?.let { line("- resultCode: ${it.name}") }
        a.diagnostic?.let { d ->
            line("- diagnostic: ${d.reason.name}" +
                (d.expectedLength?.let { ", expectedLength=$it" } ?: "") +
                (d.observedLength?.let { ", observedLength=$it" } ?: ""))
        }
        a.resolvedHttpFailureStatus()?.let { line("- httpStatus: $it") }
        a.detail?.let { line("- detail: ${safe(it)}") }
        a.templateDiagnostics.forEach { t ->
            line("- template: field=${t.field.name}, parseErrors=${t.hasParseErrors}, " +
                "unknownVariables=${t.hasUnknownVariables}, renderedEmpty=${t.renderedEmpty}")
        }
    }

    private fun safe(text: String): String = DiagnosticText.sanitize(text, 200) ?: ""

    private fun name(text: String): String =
        DiagnosticText.redactFreeText(text).replace('\n', ' ').take(120).ifBlank { "(untitled)" }
}
