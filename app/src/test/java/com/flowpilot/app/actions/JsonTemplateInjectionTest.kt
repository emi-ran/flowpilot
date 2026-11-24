package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class JsonTemplateInjectionTest {

    private val malicious = "x\", \"admin\": true, \"y\": \"\\ \n end"
    private val context = TemplateContext(smsBody = malicious)

    private fun rule(webhookBody: String = "", jsonCommandSource: String = "") = Automation(
        id = "r",
        name = "n",
        createdAt = 1L,
        triggerEvent = TriggerEvent.SMS_RECEIVED,
        action = ActionType.HTTP_WEBHOOK,
        webhookMethod = "POST",
        webhookBody = webhookBody,
        jsonCommandSource = jsonCommandSource,
    )

    private fun parse(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun renderEscapesValuesWhenRequested() {
        val out = TemplateRenderer.render("{\"t\":\"\${sms.body}\"}", context, jsonEscapeValues = true)
        val json = parse(out)
        assertThat(json.keys).containsExactly("t")
        assertThat(json.getValue("t").jsonPrimitive.content).isEqualTo(malicious)
    }

    @Test
    fun explicitJsonescapeFilterIsNotDoubleEscaped() {
        val out = TemplateRenderer.render("{\"t\":\"\${sms.body|jsonescape}\"}", context, jsonEscapeValues = true)
        assertThat(parse(out).getValue("t").jsonPrimitive.content).isEqualTo(malicious)
    }

    @Test
    fun renderDoesNotEscapeByDefault() {
        assertThat(TemplateRenderer.render("\${sms.body}", context)).isEqualTo(malicious)
    }

    @Test
    fun jsonWebhookBodyCannotInjectKeys() {
        val params = ActionParametersFactory.create(rule(webhookBody = "{\"text\":\"\${sms.body}\"}"), context)
        val json = parse(params.webhookBody)
        assertThat(json.keys).containsExactly("text")
        assertThat(json.getValue("text").jsonPrimitive.content).isEqualTo(malicious)
    }

    @Test
    fun nonJsonWebhookBodyIsLeftRaw() {
        val params = ActionParametersFactory.create(rule(webhookBody = "msg=\${sms.body}"), context)
        assertThat(params.webhookBody).isEqualTo("msg=$malicious")
    }

    @Test
    fun jsonCommandSourceValuesAreEscaped() {
        val params = ActionParametersFactory.create(
            rule(jsonCommandSource = "{\"action\":\"notify\",\"body\":\"\${sms.body}\"}"),
            context,
        )
        val json = parse(params.jsonCommandSource)
        assertThat(json.keys).containsExactly("action", "body")
        assertThat(json.getValue("body").jsonPrimitive.content).isEqualTo(malicious)
    }
}
