package com.flowpilot.app.actions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemplateDiagnosticsTest {
    @Test
    fun notificationValuesDistinguishMissingEmptyAndPresent() {
        val missing = TemplateDiagnostics.analyze("", null)
        assertThat(missing.notificationText).isEqualTo(TemplateValueState.MISSING)
        assertThat(missing.notificationBigText).isEqualTo(TemplateValueState.MISSING)

        val result = TemplateDiagnostics.analyze(
            "\${notification.text} \${notification.bigText}",
            TemplateContext(values = mapOf("notification.text" to "", "notification.bigText" to " ")),
        )
        assertThat(result.notificationText).isEqualTo(TemplateValueState.EMPTY)
        assertThat(result.notificationBigText).isEqualTo(TemplateValueState.PRESENT)
        assertThat(result.hasParseErrors).isFalse()
        assertThat(result.hasUnknownVariables).isFalse()
    }

    @Test
    fun parserDiagnosticsMatchRendererIncludingEscapesAndMalformedExpressions() {
        val context = TemplateContext(values = mapOf("notification.text" to "private"))
        val template = "\$\${unknown} \${notification.text|trim} \${unknown} \${notification.text|bad}"
        val result = TemplateDiagnostics.analyze(template, context)
        assertThat(result.hasParseErrors).isTrue()
        assertThat(result.hasUnknownVariables).isTrue()
        assertThat(result.analysisFailed).isFalse()
        assertThat(TemplateDiagnostics.analyze("\$\${unknown}", context).hasUnknownVariables).isFalse()
        assertThat(TemplateDiagnostics.analyze("\${notification.text", context).hasParseErrors).isTrue()
        assertThat(TemplateDiagnostics.analyze("x".repeat(TemplateRenderer.MAX_TEMPLATE_LENGTH + 1), context).hasParseErrors).isTrue()
    }

    @Test
    fun nullContextFollowsRendererAndClockIsNeverInvoked() {
        val withoutContext = TemplateDiagnostics.analyze("\${broken", null)
        assertThat(withoutContext.hasParseErrors).isFalse()
        assertThat(withoutContext.hasUnknownVariables).isFalse()
        val context = TemplateContext(timeProvider = { error("private exception") })
        val result = TemplateDiagnostics.analyze("\${time}", context)
        assertThat(result.analysisFailed).isFalse()
        assertThat(result.hasParseErrors).isFalse()
    }

    @Test
    fun summaryDoesNotContainTemplateValuesOrParserMessages() {
        val result = TemplateDiagnostics.analyze(
            "private-template \${privateUnknown} \${notification.text|privateFilter}",
            TemplateContext(values = mapOf("notification.text" to "private-value")),
        )
        assertThat(result.toString()).doesNotContain("private")
        assertThat(result.hasParseErrors).isTrue()
        assertThat(result.hasUnknownVariables).isTrue()
    }
}
