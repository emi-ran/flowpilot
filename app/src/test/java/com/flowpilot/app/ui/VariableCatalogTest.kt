package com.flowpilot.app.ui

import com.flowpilot.app.actions.RenderMode
import com.flowpilot.app.actions.TemplateRenderer
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.actions.VariableCatalog
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VariableCatalogTest {

    @Test
    fun globalGroupIsAvailableForEveryTrigger() {
        TriggerEvent.entries.forEach { trigger ->
            val groups = VariableCatalog.availableFor(listOf(trigger))
            assertThat(groups.map { it.id }).contains("global")
            assertThat(VariableCatalog.specsFor(listOf(trigger)).map { it.name })
                .containsAtLeast("trigger", "time", "rule.name")
        }
    }

    @Test
    fun eventGroupsAreShownOnlyForMatchingTriggers() {
        val wifiOnly = VariableCatalog.availableFor(listOf(TriggerEvent.WIFI_CONNECTED))
        assertThat(wifiOnly.map { it.id }).contains("wifi")
        assertThat(wifiOnly.map { it.id }).doesNotContain("notification")
        assertThat(wifiOnly.map { it.id }).doesNotContain("sms")

        val notificationOnly = VariableCatalog.availableFor(listOf(TriggerEvent.NOTIFICATION_RECEIVED))
        assertThat(notificationOnly.map { it.id }).contains("notification")
        assertThat(notificationOnly.map { it.id }).doesNotContain("wifi")
    }

    @Test
    fun smsTriggerShowsSmsAndGlobalVariables() {
        val groups = VariableCatalog.availableFor(listOf(TriggerEvent.SMS_RECEIVED))
        assertThat(groups.map { it.id }).containsAtLeast("global", "sms")
        assertThat(VariableCatalog.specsFor(listOf(TriggerEvent.SMS_RECEIVED)).map { it.name })
            .containsAtLeast("trigger", "sms.sender", "sms.body", "sms.otp")
    }

    @Test
    fun multiTriggerRuleShowsTheUnionOfEventGroups() {
        val union = VariableCatalog.availableFor(
            listOf(TriggerEvent.NOTIFICATION_RECEIVED, TriggerEvent.WIFI_CONNECTED),
        )
        assertThat(union.map { it.id }).containsAtLeast("global", "notification", "wifi")
        assertThat(VariableCatalog.specsFor(
            listOf(TriggerEvent.NOTIFICATION_RECEIVED, TriggerEvent.WIFI_CONNECTED),
        ).map { it.name }).containsAtLeast("notification.text", "wifi.ssid", "trigger")
    }

    @Test
    fun everyTokenIsInsertionSafe() {
        val tokenRegex = Regex("^\\$\\{[A-Za-z0-9_.]+}$")
        VariableCatalog.allSpecs.forEach { spec ->
            assertThat(tokenRegex.matches(spec.token)).isTrue()
            assertThat(spec.token).doesNotContain("\n")
            assertThat(spec.filterExample).startsWith("\${${spec.name}")
        }
    }

    @Test
    fun sampleValuesCoverEveryAvailableToken() {
        val triggers = listOf(TriggerEvent.NOTIFICATION_RECEIVED, TriggerEvent.CALL_RINGING)
        val specs = VariableCatalog.specsFor(triggers)
        val values = VariableCatalog.sampleValues(triggers)
        assertThat(values.keys).containsExactlyElementsIn(specs.map { it.name })
    }

    @Test
    fun sampleContextStrictRenderingAcceptsAvailableVariables() {
        val triggers = listOf(TriggerEvent.NOTIFICATION_RECEIVED)
        val result = TemplateRenderer.renderResult(
            "\${notification.title}: \${notification.text|stripPrefix:\"tts\"|trim}",
            VariableCatalog.sampleContext(triggers),
            RenderMode.STRICT,
        )
        assertThat(result.unknownVariables).isEmpty()
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.text).isEqualTo("Alice: bugün nasılsın")
    }

    @Test
    fun notificationSampleCanFollowTheSelectedVoiceLanguage() {
        val result = VariableCatalog.renderSample(
            "\${notification.title}: \${notification.text|stripPrefix:\"tts\"|trim}",
            listOf(TriggerEvent.NOTIFICATION_RECEIVED),
            "TTS 今日はお元気ですか？",
        )
        assertThat(result.unknownVariables).isEmpty()
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.text).isEqualTo("Alice: 今日はお元気ですか？")
    }

    @Test
    fun strictRenderingRejectsVariablesOutsideTheAvailableGroups() {
        val result = VariableCatalog.renderSample(
            "\${clipboard.text}",
            listOf(TriggerEvent.WIFI_CONNECTED),
        )
        assertThat(result.unknownVariables).contains("clipboard.text")
        assertThat(VariableCatalog.isTemplateValid("\${clipboard.text}", listOf(TriggerEvent.WIFI_CONNECTED)))
            .isFalse()
    }

    @Test
    fun smsTriggerRejectsNotificationVariableButRendersSmsBody() {
        val result = VariableCatalog.renderSample(
            "\${notification.text}|\${sms.body}",
            listOf(TriggerEvent.SMS_RECEIVED),
        )

        assertThat(result.unknownVariables).containsExactly("notification.text")
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.text).isEqualTo("\${notification.text}|Kod: 123456")
        assertThat(VariableCatalog.isTemplateValid("\${notification.text}", listOf(TriggerEvent.SMS_RECEIVED)))
            .isFalse()
        assertThat(VariableCatalog.isTemplateValid("\${sms.body}", listOf(TriggerEvent.SMS_RECEIVED)))
            .isTrue()
    }

    @Test
    fun switchingTriggersSwapsValidEventVariables() {
        val notificationTrigger = listOf(TriggerEvent.NOTIFICATION_RECEIVED)
        val smsTrigger = listOf(TriggerEvent.SMS_RECEIVED)
        val notificationTemplate = "\${notification.text}"
        val smsTemplate = "\${sms.body}"

        // Trigger switch is proven by the catalog groups: legacy SMS fields resolve as known-empty
        // even for notification rules, so strict rendering alone cannot reject ${sms.body}.
        val notificationSpecs = VariableCatalog.specsFor(notificationTrigger).map { it.name }
        val smsSpecs = VariableCatalog.specsFor(smsTrigger).map { it.name }
        assertThat(notificationSpecs).contains("notification.text")
        assertThat(notificationSpecs).doesNotContain("sms.body")
        assertThat(smsSpecs).contains("sms.body")
        assertThat(smsSpecs).doesNotContain("notification.text")

        assertThat(VariableCatalog.isTemplateValid(notificationTemplate, notificationTrigger)).isTrue()
        assertThat(VariableCatalog.isTemplateValid(notificationTemplate, smsTrigger)).isFalse()
        assertThat(VariableCatalog.isTemplateValid(smsTemplate, smsTrigger)).isTrue()
    }

    @Test
    fun catalogDoesNotOfferPrivateOrUnsupportedSources() {
        val names = VariableCatalog.allSpecs.map { it.name }
        listOf("clipboard", "contact", "contacts", "history").forEach { forbidden ->
            assertThat(names.any { it.startsWith("$forbidden.") }).isFalse()
        }
    }
}
