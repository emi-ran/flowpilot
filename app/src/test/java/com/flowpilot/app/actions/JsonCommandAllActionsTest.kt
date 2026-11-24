package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class JsonCommandAllActionsTest {
    private fun command(action: ActionType, params: String) =
        """{"version":1,"action":"${action.name}","params":$params}"""

    private val noParameters = setOf(
        ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_OFF,
        ActionType.NFC_ON, ActionType.NFC_OFF,
        ActionType.BATTERY_SAVER_ON, ActionType.BATTERY_SAVER_OFF,
        ActionType.DARK_THEME_ON, ActionType.DARK_THEME_OFF,
        ActionType.AUTO_ROTATE_ON, ActionType.AUTO_ROTATE_OFF,
        ActionType.DND_ON, ActionType.DND_OFF,
        ActionType.SOUND_PROFILE_NORMAL, ActionType.SOUND_PROFILE_VIBRATE, ActionType.SOUND_PROFILE_SILENT,
        ActionType.OPEN_DIALER,
        ActionType.MOBILE_DATA_ON, ActionType.MOBILE_DATA_OFF,
        ActionType.WIFI_ON, ActionType.WIFI_OFF,
        ActionType.AIRPLANE_MODE_ON, ActionType.AIRPLANE_MODE_OFF,
        ActionType.HOTSPOT_ON, ActionType.HOTSPOT_OFF,
        ActionType.TORCH_ON, ActionType.TORCH_OFF, ActionType.TORCH_TOGGLE,
        ActionType.AUTO_BRIGHTNESS_ON, ActionType.AUTO_BRIGHTNESS_OFF,
        ActionType.LOCK_SCREEN, ActionType.LOCATION_ON, ActionType.LOCATION_OFF,
    )

    @Test fun allActionTypesAreClassified() {
        val parameterized = setOf(
            ActionType.SHOW_NOTIFICATION, ActionType.VIBRATE, ActionType.PLAY_SOUND,
            ActionType.SET_MEDIA_VOLUME, ActionType.SET_NOTIFICATION_VOLUME, ActionType.SET_ALARM_VOLUME, ActionType.MEDIA_PLAY, ActionType.MEDIA_PAUSE,
            ActionType.MEDIA_TOGGLE, ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS,
            ActionType.MUSIC_SEARCH, ActionType.MUSIC_PLAY_LINK, ActionType.SPEAK_TEXT,
            ActionType.CREATE_ALARM, ActionType.START_TIMER, ActionType.CREATE_CALENDAR_EVENT,
            ActionType.LAUNCH_APP, ActionType.OPEN_URL, ActionType.HTTP_WEBHOOK, ActionType.COPY_TO_CLIPBOARD,
            ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER, ActionType.SEND_SMS,
            ActionType.DRAFT_SMS, ActionType.SET_SCREEN_BRIGHTNESS, ActionType.FORCE_STOP_APP,
        )
        assertThat(noParameters.intersect(parameterized)).isEmpty()
        assertThat(noParameters + parameterized + ActionType.EXECUTE_JSON_COMMAND)
            .containsExactlyElementsIn(ActionType.entries)
    }

    @Test fun parameterlessActionsParseAndRejectExtraFields() {
        for (action in noParameters) {
            val parsed = JsonCommandParser.parse(command(action, "{}"))
            assertThat(parsed).isInstanceOf(JsonCommandParser.Result.Success::class.java)
            parsed as JsonCommandParser.Result.Success
            assertThat(parsed.action).isEqualTo(action)
            assertThat(parsed.parameters).isEqualTo(ActionParameters())
            assertThat(JsonCommandParser.parse(command(action, """{"unexpected":true}""")))
                .isInstanceOf(JsonCommandParser.Result.Error::class.java)
        }
    }

    @Test fun parameterizedActionsRejectWrongFieldTypes() {
        val cases = listOf(
            ActionType.SET_MEDIA_VOLUME to """{"percent":"50"}""",
            ActionType.SET_NOTIFICATION_VOLUME to """{"percent":"50"}""",
            ActionType.SET_ALARM_VOLUME to """{"percent":"50"}""",
            ActionType.SET_SCREEN_BRIGHTNESS to """{"percent":"50"}""",
            ActionType.MEDIA_PLAY to """{"targetPackage":123}""",
            ActionType.MEDIA_PAUSE to """{"targetPackage":123}""",
            ActionType.MEDIA_TOGGLE to """{"targetPackage":123}""",
            ActionType.MEDIA_NEXT to """{"targetPackage":123}""",
            ActionType.MEDIA_PREVIOUS to """{"targetPackage":123}""",
            ActionType.VIBRATE to """{"pattern":123}""",
            ActionType.VIBRATE to """{"durationMs":"220"}""",
            ActionType.VIBRATE to """{"amplitude":true}""",
            ActionType.PLAY_SOUND to """{"preset":123}""",
            ActionType.PLAY_SOUND to """{"durationMs":"3000"}""",
            ActionType.PLAY_SOUND to """{"uri":false}""",
            ActionType.SPEAK_TEXT to """{"text":123}""",
            ActionType.SPEAK_TEXT to """{"text":"hello","voiceName":false}""",
            ActionType.SPEAK_TEXT to """{"text":"hello","speechRate":"1"}""",
            ActionType.CREATE_ALARM to """{"hour":"7","minute":0}""",
            ActionType.CREATE_ALARM to """{"hour":7,"minute":false}""",
            ActionType.CREATE_ALARM to """{"hour":7,"minute":0,"days":"1"}""",
            ActionType.CREATE_ALARM to """{"hour":7,"minute":0,"message":1}""",
            ActionType.START_TIMER to """{"durationSeconds":"300"}""",
            ActionType.START_TIMER to """{"durationSeconds":300,"message":1}""",
            ActionType.LAUNCH_APP to """{"package":123}""",
            ActionType.FORCE_STOP_APP to """{"package":123}""",
            ActionType.OPEN_URL to """{"url":123}""",
            ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","method":1}""",
            ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","headers":1}""",
            ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","body":1}""",
            ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","timeoutSeconds":"10"}""",
            ActionType.HTTP_WEBHOOK to """{"url":123}""",
            ActionType.DIAL_NUMBER to """{"number":123}""",
            ActionType.CALL_NUMBER to """{"number":123}""",
            ActionType.SEND_SMS to """{"recipient":123,"message":"hello"}""",
            ActionType.SEND_SMS to """{"recipient":"1234567890","message":123}""",
            ActionType.DRAFT_SMS to """{"recipient":123,"message":"hello"}""",
            ActionType.DRAFT_SMS to """{"recipient":"1234567890","message":123}""",
            ActionType.MUSIC_SEARCH to """{"appPackage":123,"query":"song"}""",
            ActionType.MUSIC_PLAY_LINK to """{"appPackage":"com.spotify.music","link":123}""",
            ActionType.SHOW_NOTIFICATION to """{"title":"hello","body":123}""",
            ActionType.CREATE_CALENDAR_EVENT to """{"title":"event","accountName":"a","accountType":"b","calendarName":"c","startMillis":"1","endMillis":2}""",
        )
        for ((action, params) in cases) {
            assertThat(JsonCommandParser.parse(command(action, params)))
                .isInstanceOf(JsonCommandParser.Result.Error::class.java)
        }
    }

    @Test fun commonDeviceParametersMapToExecutorFields() {
        val volume = JsonCommandParser.parse(command(ActionType.SET_MEDIA_VOLUME, """{"percent":37}""")) as JsonCommandParser.Result.Success
        assertThat(volume.parameters.mediaVolumePercent).isEqualTo(37)
        val notification = JsonCommandParser.parse(command(ActionType.SET_NOTIFICATION_VOLUME, """{"percent":23}""")) as JsonCommandParser.Result.Success
        assertThat(notification.parameters.notificationVolumePercent).isEqualTo(23)
        val alarm = JsonCommandParser.parse(command(ActionType.SET_ALARM_VOLUME, """{"percent":81}""")) as JsonCommandParser.Result.Success
        assertThat(alarm.parameters.alarmVolumePercent).isEqualTo(81)
        val brightness = JsonCommandParser.parse(command(ActionType.SET_SCREEN_BRIGHTNESS, """{"percent":63}""")) as JsonCommandParser.Result.Success
        assertThat(brightness.parameters.screenBrightnessPercent).isEqualTo(63)
        for (action in listOf(ActionType.MEDIA_PLAY, ActionType.MEDIA_PAUSE, ActionType.MEDIA_TOGGLE, ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS)) {
            val parsed = JsonCommandParser.parse(command(action, """{"targetPackage":"com.example.player"}""")) as JsonCommandParser.Result.Success
            assertThat(parsed.action).isEqualTo(action)
            assertThat(parsed.parameters.mediaTargetPackage).isEqualTo("com.example.player")
        }
    }

    @Test fun parameterizedActionsMapAllProvidedFields() {
        val cases = listOf(
            Triple(ActionType.SHOW_NOTIFICATION, """{"title":"Title","body":"Body"}""", ActionParameters(notificationTitle = "Title", notificationBody = "Body")),
            Triple(ActionType.MUSIC_SEARCH, """{"appPackage":"com.spotify.music","query":"song"}""", ActionParameters(musicAppPackage = "com.spotify.music", musicQuery = "song")),
            Triple(ActionType.MUSIC_PLAY_LINK, """{"appPackage":"com.spotify.music","link":"https://open.spotify.com/track/abc123"}""", ActionParameters(musicAppPackage = "com.spotify.music", musicLink = "https://open.spotify.com/track/abc123")),
            Triple(ActionType.CREATE_CALENDAR_EVENT, """{"title":"Meeting","accountName":"account","accountType":"type","calendarName":"Work","startMillis":1000,"endMillis":2000,"description":"Details","location":"Room","repeat":"NONE","reminderMinutes":10}""", ActionParameters(calendarTitle = "Meeting", calendarAccountName = "account", calendarAccountType = "type", calendarName = "Work", calendarStartMillis = 1000L, calendarEndMillis = 2000L, calendarDescription = "Details", calendarLocation = "Room", calendarRepeat = "NONE", calendarReminderMinutes = 10)),
            Triple(ActionType.VIBRATE, """{"pattern":"ALERT","durationMs":800,"amplitude":255}""", ActionParameters(vibrationPattern = com.flowpilot.app.data.model.VibrationPattern.ALERT, vibrationDurationMs = 800, vibrationAmplitude = 255)),
            Triple(ActionType.PLAY_SOUND, """{"preset":"CUSTOM","uri":"content://media/audio/1","durationMs":60000}""", ActionParameters(soundPreset = com.flowpilot.app.data.model.SoundPreset.CUSTOM, soundUri = "content://media/audio/1", soundDurationMs = 60000)),
            Triple(ActionType.SPEAK_TEXT, """{"text":"Hello","voiceName":"voice-1","speechRate":1.5}""", ActionParameters(ttsText = "Hello", ttsVoiceName = "voice-1", ttsSpeechRate = 1.5f)),
            Triple(ActionType.CREATE_ALARM, """{"hour":23,"minute":59,"days":[7,1,3],"message":"Wake"}""", ActionParameters(alarmHour = 23, alarmMinute = 59, alarmDays = listOf(7, 1, 3), alarmMessage = "Wake")),
            Triple(ActionType.START_TIMER, """{"durationSeconds":86400,"message":"Done"}""", ActionParameters(timerDurationSeconds = 86400, timerMessage = "Done")),
            Triple(ActionType.HTTP_WEBHOOK, """{"url":"https://example.com/hook","method":"POST","headers":"X-Test: value","body":"payload","timeoutSeconds":60}""", ActionParameters(webhookUrl = "https://example.com/hook", webhookMethod = "POST", webhookHeaders = "X-Test: value", webhookBody = "payload", webhookTimeoutSeconds = 60)),
            Triple(ActionType.LAUNCH_APP, """{"package":"com.example.app"}""", ActionParameters(launchPackage = "com.example.app")),
            Triple(ActionType.FORCE_STOP_APP, """{"package":"com.example.app"}""", ActionParameters(forceStopPackage = "com.example.app")),
            Triple(ActionType.OPEN_URL, """{"url":"https://example.com/path"}""", ActionParameters(url = "https://example.com/path")),
            Triple(ActionType.DIAL_NUMBER, """{"number":"+15551234567"}""", ActionParameters(phoneNumber = "+15551234567")),
            Triple(ActionType.CALL_NUMBER, """{"number":"+15551234567"}""", ActionParameters(phoneNumber = "+15551234567")),
            Triple(ActionType.SEND_SMS, """{"recipient":"+15551234567","message":"Hello"}""", ActionParameters(smsRecipient = "+15551234567", smsMessage = "Hello")),
            Triple(ActionType.DRAFT_SMS, """{"recipient":"+15551234567","message":"Hello"}""", ActionParameters(smsRecipient = "+15551234567", smsMessage = "Hello")),
        )
        for ((action, params, expected) in cases) {
            val result = JsonCommandParser.parse(command(action, params)) as JsonCommandParser.Result.Success
            assertThat(result.action).isEqualTo(action)
            assertThat(result.parameters).isEqualTo(expected)
        }
    }

    @Test fun optionalParametersAndBoundaryValuesAreAccepted() {
        val cases = listOf(
            Triple(ActionType.VIBRATE, "{}", ActionParameters()),
            Triple(ActionType.VIBRATE, """{"durationMs":80,"amplitude":1}""", ActionParameters(vibrationDurationMs = 80, vibrationAmplitude = 1)),
            Triple(ActionType.PLAY_SOUND, "{}", ActionParameters()),
            Triple(ActionType.PLAY_SOUND, """{"durationMs":1000}""", ActionParameters(soundDurationMs = 1000)),
            Triple(ActionType.SPEAK_TEXT, """{"text":"a","speechRate":0.5}""", ActionParameters(ttsText = "a", ttsSpeechRate = 0.5f)),
            Triple(ActionType.SPEAK_TEXT, """{"text":"a","speechRate":2}""", ActionParameters(ttsText = "a", ttsSpeechRate = 2f)),
            Triple(ActionType.CREATE_ALARM, """{"hour":0,"minute":0,"days":[1,2,3,4,5,6,7]}""", ActionParameters(alarmHour = 0, alarmDays = listOf(1, 2, 3, 4, 5, 6, 7))),
            Triple(ActionType.START_TIMER, """{"durationSeconds":1}""", ActionParameters(timerDurationSeconds = 1)),
            Triple(ActionType.HTTP_WEBHOOK, """{"url":"https://example.com","timeoutSeconds":1}""", ActionParameters(webhookUrl = "https://example.com", webhookTimeoutSeconds = 1)),
            Triple(ActionType.DRAFT_SMS, """{"recipient":"123"}""", ActionParameters(smsRecipient = "123")),
            Triple(ActionType.DRAFT_SMS, """{"message":"Hello"}""", ActionParameters(smsMessage = "Hello")),
        )
        for ((action, params, expected) in cases) {
            val result = JsonCommandParser.parse(command(action, params)) as JsonCommandParser.Result.Success
            assertThat(result.action).isEqualTo(action)
            assertThat(result.parameters).isEqualTo(expected)
        }
        for (action in listOf(ActionType.SET_MEDIA_VOLUME, ActionType.SET_NOTIFICATION_VOLUME, ActionType.SET_ALARM_VOLUME, ActionType.SET_SCREEN_BRIGHTNESS)) {
            for (value in listOf(0, 100)) {
                assertThat(JsonCommandParser.parse(command(action, """{"percent":$value}""")))
                    .isInstanceOf(JsonCommandParser.Result.Success::class.java)
            }
        }
    }

    @Test fun parameterizedActionsRejectInvalidRangesAndValues() {
        val cases = listOf(
            ActionType.VIBRATE to """{"pattern":"unknown"}""", ActionType.VIBRATE to """{"durationMs":79}""",
            ActionType.VIBRATE to """{"durationMs":801}""", ActionType.VIBRATE to """{"amplitude":0}""", ActionType.VIBRATE to """{"amplitude":256}""",
            ActionType.PLAY_SOUND to """{"preset":"unknown"}""", ActionType.PLAY_SOUND to """{"durationMs":999}""",
            ActionType.PLAY_SOUND to """{"durationMs":60001}""", ActionType.PLAY_SOUND to """{"preset":"CUSTOM","uri":"https://example.com/a"}""",
            ActionType.PLAY_SOUND to """{"preset":"CUSTOM","uri":"content:///missing-authority"}""", ActionType.PLAY_SOUND to """{"uri":"content://media/a"}""",
            ActionType.SPEAK_TEXT to """{"text":" "}""", ActionType.SPEAK_TEXT to """{"text":"a","speechRate":0.49}""",
            ActionType.SPEAK_TEXT to """{"text":"a","speechRate":2.01}""", ActionType.SPEAK_TEXT to """{"text":"${"a".repeat(2001)}"}""",
            ActionType.CREATE_ALARM to """{"hour":-1,"minute":0}""", ActionType.CREATE_ALARM to """{"hour":24,"minute":0}""",
            ActionType.CREATE_ALARM to """{"hour":0,"minute":-1}""", ActionType.CREATE_ALARM to """{"hour":0,"minute":60}""",
            ActionType.CREATE_ALARM to """{"hour":0,"minute":0,"days":[0]}""", ActionType.CREATE_ALARM to """{"hour":0,"minute":0,"days":[8]}""",
            ActionType.CREATE_ALARM to """{"hour":0,"minute":0,"days":[1,1]}""", ActionType.CREATE_ALARM to """{"hour":0,"minute":0,"days":[1,2,3,4,5,6,7,1]}""",
            ActionType.CREATE_ALARM to """{"hour":0,"minute":0,"days":["1"]}""", ActionType.CREATE_ALARM to """{"hour":0,"minute":0,"days":[1.5]}""",
            ActionType.START_TIMER to """{"durationSeconds":0}""", ActionType.START_TIMER to """{"durationSeconds":86401}""",
            ActionType.SET_MEDIA_VOLUME to """{"percent":-1}""", ActionType.SET_MEDIA_VOLUME to """{"percent":101}""",
            ActionType.SET_NOTIFICATION_VOLUME to """{"percent":-1}""", ActionType.SET_NOTIFICATION_VOLUME to """{"percent":101}""",
            ActionType.SET_ALARM_VOLUME to """{"percent":-1}""", ActionType.SET_ALARM_VOLUME to """{"percent":101}""",
            ActionType.SET_SCREEN_BRIGHTNESS to """{"percent":-1}""", ActionType.SET_SCREEN_BRIGHTNESS to """{"percent":101}""",
            ActionType.HTTP_WEBHOOK to """{"url":"http://example.com"}""", ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","method":"INVALID"}""",
            ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","method":"GET","body":"x"}""", ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","headers":"bad header"}""",
            ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","timeoutSeconds":0}""", ActionType.HTTP_WEBHOOK to """{"url":"https://example.com","timeoutSeconds":61}""",
            ActionType.SEND_SMS to """{"recipient":"123","message":" "}""", ActionType.SEND_SMS to """{"recipient":" ","message":"hello"}""",
            ActionType.SEND_SMS to """{"recipient":"123"}""", ActionType.DRAFT_SMS to "{}",
            ActionType.OPEN_URL to """{"url":"file:///tmp/file"}""", ActionType.LAUNCH_APP to """{"package":" "}""",
            ActionType.FORCE_STOP_APP to """{"package":""}""", ActionType.DIAL_NUMBER to """{"number":" "}""", ActionType.CALL_NUMBER to """{"number":""}""",
        )
        for ((action, params) in cases) {
            assertThat(JsonCommandParser.parse(command(action, params)))
                .isNotInstanceOf(JsonCommandParser.Result.Success::class.java)
        }
    }

    @Test fun nestedJsonCommandIsRejectedWithoutDispatch() = runBlocking {
        for (params in listOf("{}", """{"source":"secret-nested-command"}""")) {
            val input = command(ActionType.EXECUTE_JSON_COMMAND, params)
            assertThat(JsonCommandParser.parse(input)).isInstanceOf(JsonCommandParser.Result.Error::class.java)
            var dispatched = false
            val outcome = JsonCommandExecution.execute(input) { _, _ ->
                dispatched = true
                ActionResult(true, "unexpected")
            }
            assertThat(dispatched).isFalse()
            assertThat(outcome.status).isEqualTo("invalid_command")
            assertThat(outcome.toString()).doesNotContain("secret-nested-command")
        }
    }

    @Test fun invalidSensitivePayloadNeverReachesDispatchOrOutcome() = runBlocking {
        val secret = "private-payload-5f7e"
        val input = command(ActionType.SHOW_NOTIFICATION, """{"title":"$secret","body":42}""")
        val parsed = JsonCommandParser.parse(input)
        assertThat(parsed).isInstanceOf(JsonCommandParser.Result.Error::class.java)
        assertThat(parsed.toString()).doesNotContain(secret)
        var dispatched = false
        val outcome = JsonCommandExecution.execute(input) { _, _ ->
            dispatched = true
            ActionResult(true, secret)
        }
        assertThat(dispatched).isFalse()
        assertThat(outcome.status).isEqualTo("invalid_command")
        assertThat(outcome.toString()).doesNotContain(secret)
        assertThat(outcome.applyTo(TemplateContext()).values.toString()).doesNotContain(secret)
    }

    @Test fun executorMessagesCannotLeakPayloadOnSuccessOrFailure() = runBlocking {
        val secret = "private-payload-5f7e"
        val input = command(ActionType.SHOW_NOTIFICATION, """{"title":"$secret","body":"body"}""")
        for (successful in listOf(true, false)) {
            var calls = 0
            val outcome = JsonCommandExecution.execute(input) { action, parameters ->
                calls++
                assertThat(action).isEqualTo(ActionType.SHOW_NOTIFICATION)
                assertThat(parameters.notificationTitle).isEqualTo(secret)
                ActionResult(successful, secret)
            }
            assertThat(calls).isEqualTo(1)
            assertThat(outcome.toString()).doesNotContain(secret)
            assertThat(outcome.applyTo(TemplateContext()).values.toString()).doesNotContain(secret)
        }
    }
}
