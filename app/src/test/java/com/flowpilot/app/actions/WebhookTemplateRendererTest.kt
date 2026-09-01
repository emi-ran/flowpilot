package com.flowpilot.app.actions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebhookTemplateRendererTest {

    @Test
    fun render_replacesAllSupportedVariables() {
        val context = WebhookTemplateContext(
            trigger = "CHARGER_CONNECTED",
            timestamp = 1717200000000L,
            timeProvider = { "2024-06-01T00:00:00Z" },
            batteryPercent = 85,
            isCharging = true,
            wifiSsid = "Home_5G",
        )

        val template = """
            {
              "event": "${'$'}{trigger}",
              "ts": ${'$'}{timestamp},
              "time": "${'$'}{time}",
              "battery": ${'$'}{batteryPercent},
              "charging": ${'$'}{isCharging},
              "wifi": "${'$'}{wifiSsid}"
            }
        """.trimIndent()

        val rendered = WebhookTemplateRenderer.render(template, context)

        assertThat(rendered).isEqualTo("""
            {
              "event": "CHARGER_CONNECTED",
              "ts": 1717200000000,
              "time": "2024-06-01T00:00:00Z",
              "battery": 85,
              "charging": true,
              "wifi": "Home_5G"
            }
        """.trimIndent())
    }

    @Test
    fun render_handlesNullValuesGracefully() {
        val context = WebhookTemplateContext(
            trigger = "SCREEN_ON",
            timestamp = 1717200000000L,
            timeProvider = { "2024-06-01T00:00:00Z" },
            batteryPercent = null,
            isCharging = null,
            wifiSsid = null,
        )

        val template = "bat=${'$'}{batteryPercent}&charge=${'$'}{isCharging}&wifi=${'$'}{wifiSsid}"
        val rendered = WebhookTemplateRenderer.render(template, context)

        assertThat(rendered).isEqualTo("bat=&charge=&wifi=")
    }

    @Test
    fun render_unknownAndMalformedTokensUnchanged() {
        val context = WebhookTemplateContext(
            trigger = "APP_OPENED",
            timestamp = 12345L,
            timeProvider = { "2024-01-01T00:00:00Z" },
        )

        val template = "${'$'}{unknownVar} ${'$'}{malformed ${'$'}trigger ${'$'}{trigger}"
        val rendered = WebhookTemplateRenderer.render(template, context)

        assertThat(rendered).isEqualTo("${'$'}{unknownVar} ${'$'}{malformed ${'$'}trigger APP_OPENED")
    }

    @Test
    fun render_noRecursiveEvaluation() {
        // If trigger contains a variable token like ${batteryPercent}, it should NOT be recursively evaluated
        val context = WebhookTemplateContext(
            trigger = "\${batteryPercent}",
            batteryPercent = 99,
        )

        val template = "event=\${trigger}"
        val rendered = WebhookTemplateRenderer.render(template, context)

        assertThat(rendered).isEqualTo("event=\${batteryPercent}")
    }

    @Test
    fun render_nullContextOrEmptyTemplate_returnsOriginal() {
        assertThat(WebhookTemplateRenderer.render("", null)).isEqualTo("")
        assertThat(WebhookTemplateRenderer.render("hello world", null)).isEqualTo("hello world")
        val context = WebhookTemplateContext()
        assertThat(WebhookTemplateRenderer.render("", context)).isEqualTo("")
        assertThat(WebhookTemplateRenderer.render("no variables", context)).isEqualTo("no variables")
    }
}
