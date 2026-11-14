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
    fun alarmDaysAcceptCalendarValuesAndRejectOutOfRangeValues() {
        val alarm = rule(actions = listOf(ActionType.CREATE_ALARM))
        ImportValidator.validate(listOf(alarm))
        ImportValidator.validate(listOf(alarm.copy(alarmDays = listOf(1, 3, 7))))
        assertRejected(alarm.copy(alarmDays = listOf(0)))
        assertRejected(alarm.copy(alarmDays = listOf(8)))
    }

    @Test
    fun calendarTimesValidateDefaultsExplicitRangesAndOverflow() {
        val calendar = rule(actions = listOf(ActionType.CREATE_CALENDAR_EVENT))
        ImportValidator.validate(listOf(calendar))
        ImportValidator.validate(listOf(calendar.copy(calendarStartMillis = 100, calendarEndMillis = 200)))
        assertRejected(calendar.copy(calendarStartMillis = -1))
        assertRejected(calendar.copy(calendarEndMillis = -1))
        assertRejected(calendar.copy(calendarStartMillis = 200, calendarEndMillis = 200))
        assertRejected(calendar.copy(calendarStartMillis = 201, calendarEndMillis = 200))
        assertRejected(calendar.copy(calendarStartMillis = Long.MAX_VALUE))
    }

    @Test
    fun calendarSelectionAcceptsLegacyOrCompleteButRejectsPartial() {
        val calendar = rule(actions = listOf(ActionType.CREATE_CALENDAR_EVENT))
        ImportValidator.validate(listOf(calendar))
        ImportValidator.validate(listOf(calendar.copy(calendarAccountName = "account", calendarAccountType = "type", calendarName = "name")))
        assertRejected(calendar.copy(calendarAccountName = "account"))
        assertRejected(calendar.copy(calendarAccountName = "account", calendarAccountType = "type", calendarName = " "))
    }
    @Test
    fun musicImportRequiresMatchingServiceAndFields() {
        val search = rule(actions = listOf(ActionType.MUSIC_SEARCH)).copy(
            musicAppPackage = "com.spotify.music", musicQuery = "song",
        )
        ImportValidator.validate(listOf(search))
        assertRejected(search.copy(musicQuery = " "))
        assertRejected(search.copy(musicAppPackage = "unknown"))
        val link = search.copy(action = ActionType.MUSIC_PLAY_LINK, actions = listOf(ActionType.MUSIC_PLAY_LINK),
            musicLink = "https://open.spotify.com/track/abc?si=share")
        ImportValidator.validate(listOf(link))
        assertRejected(link.copy(musicAppPackage = "com.google.android.apps.youtube.music"))
        assertRejected(link.copy(musicLink = "https://open.spotify.com.evil.test/track/abc"))
    }

    @Test
    fun musicImportAcceptsValidTemplatesButRejectsMalformedOrEscapedExpressions() {
        val link = rule(actions = listOf(ActionType.MUSIC_PLAY_LINK)).copy(
            musicAppPackage = "com.spotify.music", musicLink = "\${notification.text|trim}",
        )
        ImportValidator.validate(listOf(link))
        ImportValidator.validate(listOf(link.copy(musicLink = "https://open.spotify.com/track/\${notification.title}")))
        assertRejected(link.copy(musicLink = "\$\${notification.text}"))
        assertRejected(link.copy(musicLink = "\${notification.text|badFilter}"))
        assertRejected(link.copy(musicLink = "\${notification.text"))
        assertRejected(link.copy(musicAppPackage = "unknown"))
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
