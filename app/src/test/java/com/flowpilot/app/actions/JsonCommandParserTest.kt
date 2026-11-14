package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonCommandParserTest {
    private fun command(action: String, params: String, extra: String = "") =
        """{"version":1,"action":"$action","params":$params$extra}"""

    private fun success(input: String) = JsonCommandParser.parse(input) as JsonCommandParser.Result.Success
    private fun rejected(input: String) = assertThat(JsonCommandParser.parse(input)).isInstanceOf(JsonCommandParser.Result.Error::class.java)

    @Test fun torchCommandsAndWhitespace() {
        for ((name, action) in listOf("TORCH_ON" to ActionType.TORCH_ON, "TORCH_OFF" to ActionType.TORCH_OFF)) {
            val parsed = success(" \n" + command(name, "{}", ",\"id\":\"abc\"") + "\t")
            assertThat(parsed.action).isEqualTo(action)
            assertThat(parsed.id).isEqualTo("abc")
            assertThat(parsed.parameters).isEqualTo(ActionParameters())
        }
    }

    @Test fun musicCommandsMapAndValidate() {
        val app = MusicLinkValidator.SPOTIFY
        val search = success(command("MUSIC_SEARCH", """{"appPackage":"$app","query":"song"}"""))
        assertThat(search.action).isEqualTo(ActionType.MUSIC_SEARCH)
        assertThat(search.parameters.musicAppPackage).isEqualTo(app)
        assertThat(search.parameters.musicQuery).isEqualTo("song")
        val link = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"
        assertThat(success(command("MUSIC_PLAY_LINK", """{"appPackage":"$app","link":"$link"}""")).parameters.musicLink).isEqualTo(link)
        rejected(command("MUSIC_SEARCH", """{"appPackage":"bad","query":"song"}"""))
        rejected(command("MUSIC_SEARCH", """{"appPackage":"$app","query":"  "}"""))
        rejected(command("MUSIC_PLAY_LINK", """{"appPackage":"$app","link":"https://evil.example/track/abc"}"""))
    }

    @Test fun notificationFieldsAreNotRenderedAgain() {
        val parsed = success(command("SHOW_NOTIFICATION", """{"title":"Title","body":"\u0024{sms.body}"}"""))
        assertThat(parsed.action).isEqualTo(ActionType.SHOW_NOTIFICATION)
        assertThat(parsed.parameters.notificationTitle).isEqualTo("Title")
        assertThat(parsed.parameters.notificationBody).isEqualTo("\${sms.body}")
    }

    @Test fun calendarMapsExplicitSelectionAndOptions() {
        val params = """{"title":"Meeting","accountName":"account","accountType":"type","calendarName":"cal","startMillis":100,"endMillis":200,"description":"details","location":"room","repeat":"WEEKLY","reminderMinutes":30}"""
        val parsed = success(command("CREATE_CALENDAR_EVENT", params))
        assertThat(parsed.action).isEqualTo(ActionType.CREATE_CALENDAR_EVENT)
        assertThat(parsed.parameters).isEqualTo(ActionParameters(calendarTitle = "Meeting", calendarDescription = "details", calendarLocation = "room", calendarAccountName = "account", calendarAccountType = "type", calendarName = "cal", calendarStartMillis = 100, calendarEndMillis = 200, calendarRepeat = "WEEKLY", calendarReminderMinutes = 30))
        val minimal = success(command("CREATE_CALENDAR_EVENT", """{"title":"T","accountName":"a","accountType":"b","calendarName":"c","startMillis":1,"endMillis":2}"""))
        assertThat(minimal.parameters.calendarRepeat).isEqualTo("NONE")
        assertThat(minimal.parameters.calendarReminderMinutes).isEqualTo(-1)
    }

    @Test fun calendarRejectsInvalidSelectionTimesAndOptions() {
        val base = """{"title":"T","accountName":"a","accountType":"b","calendarName":"c","startMillis":1,"endMillis":2}"""
        for (params in listOf(base.replace("\"a\"", "\" \""), base.replace("\"T\"", "\"\""),
            base.replace("\"startMillis\":1", "\"startMillis\":0"), base.replace("\"endMillis\":2", "\"endMillis\":1"),
            base.replace("\"endMillis\":2", "\"endMillis\":9223372036854775808"),
            base.replace("\"endMillis\":2", "\"endMillis\":2.0"),
            base.dropLast(1) + ",\"repeat\":\"HOURLY\"}", base.dropLast(1) + ",\"reminderMinutes\":40321}",
            base.dropLast(1) + ",\"reminderMinutes\":-2}", base.dropLast(1) + ",\"location\":null}")) {
            rejected(command("CREATE_CALENDAR_EVENT", params))
        }
    }

    @Test fun rejectsMalformedExtraUnknownDuplicateAndWrongTypes() {
        for (input in listOf("", "{} trailing", "prefix {}", "[]", "{", command("TORCH_ON", "{}") + "{}",
            """{"version":1,"version":1,"action":"TORCH_ON","params":{}}""",
            """{"version":1,"action":"SHOW_NOTIFICATION","params":{"title":"a","\u0074itle":"b","body":"c"}}""",
            """{"version":1,"action":"TORCH_ON","params":{},"extra":true}""",
            command("TORCH_ON", """{"unexpected":1}"""), command("TORCH_ON", "[]"),
            command("TORCH_ON", """{"nested":{"deeper":{}}}"""),
            """{"version":"1","action":"TORCH_ON","params":{}}""",
            """{"version":2,"action":"TORCH_ON","params":{}}""",
            command("EXECUTE_JSON_COMMAND", "{}"), command("SHOW_NOTIFICATION", """{"title":1,"body":"b"}"""),
            command("SHOW_NOTIFICATION", """{"title":"a"}"""), command("TORCH_ON", "{}", ",\"id\":null"))) rejected(input)
    }

    @Test fun boundsAndErrorsDoNotExposePayload() {
        val secret = "sensitive-payload"
        val result = JsonCommandParser.parse(secret) as JsonCommandParser.Result.Error
        assertThat(result.toString()).doesNotContain(secret)
        rejected(" ".repeat(JsonCommandParser.MAX_INPUT_LENGTH + 1))
        rejected(command("TORCH_ON", "{}", ",\"id\":\"${"x".repeat(129)}\""))
        rejected(command("SHOW_NOTIFICATION", """{"title":"${"x".repeat(4097)}","body":"b"}"""))
    }
}
