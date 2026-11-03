package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActionParametersFactoryTest {

    private fun rule(
        ttsText: String = "",
        ttsAudioFileName: String = "",
        smsRecipient: String = "",
        launchPackage: String = "launch.pkg",
        forceStopPackage: String = "force.pkg",
        soundUri: String = "content://sounds/tone",
    ) = Automation(
        id = "rule-1",
        name = "Factory rule",
        triggerEvent = TriggerEvent.NOTIFICATION_RECEIVED,
        action = ActionType.SPEAK_TEXT,
        notificationTitle = "Title \${notification.title}",
        notificationBody = "Body \${notification.text}",
        url = "https://open.example/\${trigger}",
        ttsText = ttsText,
        ttsAudioFileName = ttsAudioFileName,
        alarmMessage = "Alarm \${notification.app}",
        timerMessage = "Timer \${trigger}",
        webhookMethod = "POST",
        webhookUrl = "https://hook.example/\${notification.package}",
        webhookHeaders = "# comment\nX-Event: \${trigger}\nX-App: \${notification.app}",
        webhookBody = "{\"text\":\"\${notification.text}\"}",
        phoneNumber = "\${call.number}",
        smsRecipient = smsRecipient,
        smsMessage = "OTP \${sms.otp}",
        launchPackage = launchPackage,
        forceStopPackage = forceStopPackage,
        soundUri = soundUri,
        vibrationDurationMs = 321,
        webhookTimeoutSeconds = 7,
        createdAt = 1L,
    )

    private fun context() = TemplateContext(
        trigger = "NOTIFICATION_RECEIVED",
        timestamp = 1_800_000_000_000L,
        timeProvider = { "2026-11-03T00:00:00Z" },
        batteryPercent = 42,
        isCharging = true,
        wifiSsid = "Home",
        smsSender = " +905551112233 ",
        smsBody = "Your code is 778899",
        locationLat = 41.0082,
        locationLng = 28.9784,
        values = mapOf(
            "notification.title" to "Hello",
            "notification.text" to "World",
            "notification.bigText" to "Big world",
            "notification.package" to "com.example.app",
            "notification.app" to "Example",
            "call.number" to "+905550001122",
            "call.state" to "RINGING",
        ),
    )

    @Test
    fun create_rendersEverySupportedFieldOnce() {
        val parameters = ActionParametersFactory.create(rule(), context())

        assertThat(parameters.notificationTitle).isEqualTo("Title Hello")
        assertThat(parameters.notificationBody).isEqualTo("Body World")
        assertThat(parameters.url).isEqualTo("https://open.example/NOTIFICATION_RECEIVED")
        assertThat(parameters.alarmMessage).isEqualTo("Alarm Example")
        assertThat(parameters.timerMessage).isEqualTo("Timer NOTIFICATION_RECEIVED")
        assertThat(parameters.webhookUrl).isEqualTo("https://hook.example/com.example.app")
        assertThat(parameters.webhookHeaders).isEqualTo(
            "# comment\nX-Event: NOTIFICATION_RECEIVED\nX-App: Example",
        )
        assertThat(parameters.webhookBody).isEqualTo("{\"text\":\"World\"}")
        assertThat(parameters.phoneNumber).isEqualTo("+905550001122")
        assertThat(parameters.smsRecipient).isEqualTo("+905551112233")
        assertThat(parameters.smsMessage).isEqualTo("OTP 778899")
    }

    @Test
    fun create_leavesPackageSoundAndNumericFieldsUntouched() {
        val parameters = ActionParametersFactory.create(
            rule(launchPackage = "com.\${trigger}", forceStopPackage = "force.\${notification.text}", soundUri = "uri.\${trigger}"),
            context(),
        )

        assertThat(parameters.launchPackage).isEqualTo("com.\${trigger}")
        assertThat(parameters.forceStopPackage).isEqualTo("force.\${notification.text}")
        assertThat(parameters.soundUri).isEqualTo("uri.\${trigger}")
        assertThat(parameters.vibrationDurationMs).isEqualTo(321)
        assertThat(parameters.webhookTimeoutSeconds).isEqualTo(7)
        assertThat(parameters.webhookMethod).isEqualTo("POST")
    }

    @Test
    fun create_keepsUnknownLenientTokens_andDoesNotReRenderValues() {
        val rule = rule().copy(notificationBody = "\${notification.unknown} \${notification.text}")
        val parameters = ActionParametersFactory.create(
            rule,
            context().copy(values = context().values + ("notification.text" to "\${trigger}")),
        )

        assertThat(parameters.notificationBody).isEqualTo("\${notification.unknown} \${trigger}")
        assertThat(parameters.ttsTemplateError).isEqualTo(ActionParametersFactory.TTS_TEMPLATE_ERROR)
    }

    @Test
    fun create_headerValueWithLineBreak_isRejectedByPostRenderValidation() {
        val parameters = ActionParametersFactory.create(
            rule().copy(webhookHeaders = "X-Event: \${notification.text}"),
            context().copy(values = context().values + ("notification.text" to "safe\nInjected: yes")),
        )

        assertThat(WebhookExecutor.validateHeaders(parameters.webhookHeaders)).isNotNull()
    }

    @Test
    fun create_smsRecipient_fallsBackToSmsSender_whenRenderedBlank() {
        val parameters = ActionParametersFactory.create(
            rule(smsRecipient = "\${notification.bigText}"),
            context().copy(values = context().values + ("notification.bigText" to "   ")),
        )

        assertThat(parameters.smsRecipient).isEqualTo("+905551112233")
    }

    @Test
    fun create_smsRecipient_fallsBackToSmsSender_whenConfiguredBlank() {
        val parameters = ActionParametersFactory.create(rule(smsRecipient = "   "), context())

        assertThat(parameters.smsRecipient).isEqualTo("+905551112233")
    }

    @Test
    fun create_tts_rendersStrictlyAndClearsError() {
        val parameters = ActionParametersFactory.create(
            rule(ttsText = "Say \${notification.text}"),
            context(),
        )

        assertThat(parameters.ttsText).isEqualTo("Say World")
        assertThat(parameters.ttsTemplateError).isNull()
    }

    @Test
    fun create_tts_unknownVariable_failsGenericallyWithoutTemplateContent() {
        val parameters = ActionParametersFactory.create(
            rule(ttsText = "Secret \${notification.missing}"),
            context(),
        )

        assertThat(parameters.ttsText).isEmpty()
        assertThat(parameters.ttsTemplateError).isEqualTo(ActionParametersFactory.TTS_TEMPLATE_ERROR)
        assertThat(parameters.ttsTemplateError).doesNotContain("Secret")
        assertThat(parameters.ttsTemplateError).doesNotContain("notification.missing")
    }

    @Test
    fun create_tts_parseError_failsGenerically() {
        val parameters = ActionParametersFactory.create(
            rule(ttsText = "Say \${notification.text"),
            context(),
        )

        assertThat(parameters.ttsText).isEmpty()
        assertThat(parameters.ttsTemplateError).isEqualTo(ActionParametersFactory.TTS_TEMPLATE_ERROR)
    }

    @Test
    fun create_tts_emptyRenderedResult_failsGenerically() {
        val parameters = ActionParametersFactory.create(
            rule(ttsText = "\${notification.text}"),
            context().copy(values = context().values + ("notification.text" to "   ")),
        )

        assertThat(parameters.ttsText).isEmpty()
        assertThat(parameters.ttsTemplateError).isEqualTo(ActionParametersFactory.TTS_TEMPLATE_ERROR)
    }

    @Test
    fun create_tts_blankTextWithLegacyFile_hasNoError() {
        val parameters = ActionParametersFactory.create(
            rule(ttsText = "", ttsAudioFileName = "tts_rule_0123456789abcdef.wav"),
            context(),
        )

        assertThat(parameters.ttsText).isEmpty()
        assertThat(parameters.ttsAudioFileName).isEqualTo("tts_rule_0123456789abcdef.wav")
        assertThat(parameters.ttsTemplateError).isNull()
    }

    @Test
    fun create_tts_blankTextWithoutLegacyFile_failsGenerically() {
        val parameters = ActionParametersFactory.create(rule(), context())

        assertThat(parameters.ttsText).isEmpty()
        assertThat(parameters.ttsTemplateError).isEqualTo(ActionParametersFactory.TTS_TEMPLATE_ERROR)
    }
}
