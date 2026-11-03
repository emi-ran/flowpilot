package com.flowpilot.app.data.backup

import com.flowpilot.app.actions.TemplateRenderer
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerDefinition
import com.flowpilot.app.data.model.TriggerDefinitions
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportValidatorTest {

    private fun rule(
        id: String = "rule-1",
        trigger: TriggerEvent = TriggerEvent.NOTIFICATION_RECEIVED,
        triggerDefinitions: TriggerDefinitions? = null,
        ttsText: String = "",
        ttsAudioFileName: String = "",
        ttsSpeechRate: Float = 1.0f,
        actions: List<ActionType> = listOf(ActionType.SPEAK_TEXT),
    ) = Automation(
        id = id,
        name = "Speech rule",
        triggerEvent = trigger,
        triggerDefinitions = triggerDefinitions,
        actions = actions,
        action = actions.first(),
        ttsText = ttsText,
        ttsSpeechRate = ttsSpeechRate,
        ttsAudioFileName = ttsAudioFileName,
        createdAt = 1L,
    )

    private fun assertRejected(rule: Automation) {
        assertThrows(IllegalArgumentException::class.java) {
            ImportValidator.validate(listOf(rule))
        }
    }

    @Test
    fun dynamicRuleWithoutAudioIsAccepted() {
        ImportValidator.validate(
            listOf(rule(ttsText = "\${notification.text|stripPrefix:\"tts\"|trim}")),
        )
    }

    @Test
    fun legacyRuleWithSafeFileNameIsAccepted() {
        ImportValidator.validate(
            listOf(rule(ttsText = "", ttsAudioFileName = "tts_rule-1_0123456789abcdef.wav")),
        )
    }

    @Test
    fun contentAddressedFileNameIsAcceptedWhenTextIsPresent() {
        val contentName = "tts_" + "a".repeat(32) + ".wav"
        ImportValidator.validate(
            listOf(rule(ttsText = "Battery is \${batteryPercent}", ttsAudioFileName = contentName)),
        )
    }

    @Test
    fun unsafeCacheNamesAreRejected() {
        assertRejected(rule(ttsAudioFileName = "../../tts_evil.wav"))
        assertRejected(rule(ttsAudioFileName = "tts_evil.wav"))
        assertRejected(rule(ttsText = "Hello", ttsAudioFileName = "tts_" + "A".repeat(32) + ".wav"))
    }

    @Test
    fun speechWithoutTextOrLegacyAudioIsRejected() {
        assertRejected(rule(ttsText = "", ttsAudioFileName = ""))
        assertRejected(rule(ttsText = "", ttsAudioFileName = "tts_" + "a".repeat(32) + ".wav"))
    }

    @Test
    fun oversizedTemplateIsRejected() {
        assertRejected(rule(ttsText = "a".repeat(TemplateRenderer.MAX_TEMPLATE_LENGTH + 1)))
    }

    @Test
    fun speechRateOutsideEditorRangeIsRejected() {
        assertRejected(rule(ttsText = "Hello", ttsSpeechRate = 0.1f))
        assertRejected(rule(ttsText = "Hello", ttsSpeechRate = 2.5f))
    }

    @Test
    fun unknownTtsVariableIsRejected() {
        assertRejected(rule(ttsText = "\${clipboard.text}"))
        assertRejected(rule(trigger = TriggerEvent.WIFI_CONNECTED, ttsText = "\${notification.text}"))
    }

    @Test
    fun malformedTtsExpressionIsRejected() {
        assertRejected(rule(ttsText = "\${notification.text"))
    }

    @Test
    fun multiTriggerRuleMayUseTheUnionOfEventVariables() {
        ImportValidator.validate(
            listOf(
                rule(
                    triggerDefinitions = TriggerDefinitions(
                        items = listOf(
                            TriggerDefinition.Notification(),
                            TriggerDefinition.WifiConnected(wifiSsid = "Home_5G"),
                        ),
                    ),
                    ttsText = "\${notification.text|trim} on \${wifi.ssid}",
                ),
            ),
        )
    }
}
