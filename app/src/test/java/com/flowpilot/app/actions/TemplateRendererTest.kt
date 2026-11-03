package com.flowpilot.app.actions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemplateRendererTest {

    private fun render(
        template: String,
        context: TemplateContext? = TemplateContext(),
        mode: RenderMode = RenderMode.LENIENT,
    ): String = TemplateRenderer.render(template, context, mode)

    private fun fullContext() = TemplateContext(
        trigger = "CHARGER_CONNECTED",
        timestamp = 1717200000000L,
        timeProvider = { "2024-06-01T00:00:00Z" },
        batteryPercent = 85,
        isCharging = true,
        wifiSsid = "Home_5G",
        smsSender = "+905551112233",
        smsBody = "Your code is 123456",
        smsOtp = "999999",
        locationLat = 41.0082,
        locationLng = 28.9784,
        triggerSourceIndex = 2,
    )

    @Test
    fun render_resolvesEveryLegacyAndDotAlias() {
        val context = fullContext()
        val expected = linkedMapOf(
            "trigger" to "CHARGER_CONNECTED",
            "trigger.index" to "2",
            "time" to "2024-06-01T00:00:00Z",
            "timestamp" to "1717200000000",
            "batteryPercent" to "85",
            "battery.percent" to "85",
            "isCharging" to "true",
            "charger.connected" to "true",
            "wifiSsid" to "Home_5G",
            "wifi.ssid" to "Home_5G",
            "smsSender" to "+905551112233",
            "sms.sender" to "+905551112233",
            "smsBody" to "Your code is 123456",
            "sms.body" to "Your code is 123456",
            "smsOtp" to "999999",
            "sms.otp" to "999999",
            "locationLat" to "41.008200",
            "location.lat" to "41.008200",
            "locationLng" to "28.978400",
            "location.lng" to "28.978400",
            "location.maps_url" to "https://maps.google.com/?q=41.008200,28.978400",
            "maps_url" to "https://maps.google.com/?q=41.008200,28.978400",
            "location.coords" to "41.008200,28.978400",
            "coords" to "41.008200,28.978400",
        )

        for ((name, value) in expected) {
            assertThat(render("\${$name}", context)).isEqualTo(value)
        }
    }

    @Test
    fun render_derivesOtpFromSmsBodyWhenNotProvided() {
        val context = TemplateContext(smsBody = "Your verification code is 4821, do not share")
        assertThat(render("\${smsOtp}", context)).isEqualTo("4821")
        assertThat(render("\${sms.otp}", context)).isEqualTo("4821")
    }

    @Test
    fun render_nullValuesBecomeEmptyStringsNeverNull() {
        val context = TemplateContext(trigger = "")
        val template = listOf(
            "batteryPercent", "isCharging", "wifiSsid", "smsSender", "smsBody", "smsOtp",
            "locationLat", "locationLng", "location.maps_url", "location.coords", "trigger.index",
        ).joinToString("|") { "\${$it}" }

        assertThat(render(template, context)).isEqualTo("|".repeat(10))
        assertThat(render(template, context)).doesNotContain("null")
    }

    @Test
    fun render_unknownVariableLenientKeepsTokenAndStrictReportsIt() {
        val template = "a\${unknownVar}b"

        val lenient = TemplateRenderer.renderResult(template, TemplateContext(), RenderMode.LENIENT)
        assertThat(lenient.text).isEqualTo("a\${unknownVar}b")
        assertThat(lenient.unknownVariables).isEmpty()
        assertThat(lenient.parseErrors).isEmpty()

        val strict = TemplateRenderer.renderResult(template, TemplateContext(), RenderMode.STRICT)
        assertThat(strict.text).isEqualTo("a\${unknownVar}b")
        assertThat(strict.unknownVariables).containsExactly("unknownVar")
        assertThat(strict.parseErrors).isEmpty()
    }

    @Test
    fun render_unknownFilterAndMalformedArgumentsAreNotPartiallyRendered() {
        val context = TemplateContext(smsBody = "hello")
        val unknownFilter = TemplateRenderer.renderResult("\${smsBody|bogus}", context)
        assertThat(unknownFilter.text).isEqualTo("\${smsBody|bogus}")
        assertThat(unknownFilter.parseErrors).hasSize(1)
        assertThat(unknownFilter.parseErrors.single()).contains("unknown filter")

        val missingArgument = TemplateRenderer.renderResult("\${smsBody|truncate}", context)
        assertThat(missingArgument.text).isEqualTo("\${smsBody|truncate}")
        assertThat(missingArgument.parseErrors).isNotEmpty()

        val badNumber = TemplateRenderer.renderResult("\${smsBody|drop:abc}", context)
        assertThat(badNumber.text).isEqualTo("\${smsBody|drop:abc}")
        assertThat(badNumber.parseErrors).isNotEmpty()

        val extraArgument = TemplateRenderer.renderResult("\${smsBody|trim:2}", context)
        assertThat(extraArgument.text).isEqualTo("\${smsBody|trim:2}")
        assertThat(extraArgument.parseErrors).isNotEmpty()

        val unterminated = TemplateRenderer.renderResult("x \${smsBody", context)
        assertThat(unterminated.text).isEqualTo("x \${smsBody")
        assertThat(unterminated.parseErrors).isNotEmpty()
    }

    @Test
    fun render_malformedAndLaterWellFormedTokenStillRendersLaterToken() {
        // Historical behavior: a missing closing brace must not swallow a following valid token.
        val context = TemplateContext(trigger = "APP_OPENED")
        val result = TemplateRenderer.renderResult(
            "\${unknownVar} \${malformed \$trigger \${trigger}",
            context,
        )
        assertThat(result.text).isEqualTo("\${unknownVar} \${malformed \$trigger APP_OPENED")
        assertThat(result.parseErrors).isNotEmpty()
    }

    @Test
    fun render_doubleDollarProducesLiteralToken() {
        val context = TemplateContext(smsBody = "hello")

        assertThat(render("\$\${smsBody}", context)).isEqualTo("\${smsBody}")
        assertThat(render("A\$\${smsBody}B\${smsBody}", context)).isEqualTo("A\${smsBody}Bhello")

        val result = TemplateRenderer.renderResult("\$\${smsBody}", context, RenderMode.STRICT)
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.unknownVariables).isEmpty()
    }

    @Test
    fun render_quotedArgumentsMayContainSpacesBracesPipesAndEscapes() {
        val context = TemplateContext(values = mapOf("x" to ""))
        assertThat(render("""${'$'}{x|default:"a b}c|d"}""", context)).isEqualTo("a b}c|d")
        assertThat(render("""${'$'}{x|default:"say \"hi\" \\ ok"}""", context))
            .isEqualTo("""say "hi" \ ok""")
    }

    @Test
    fun render_regexLikeQuantifierInsideQuotedArgumentDoesNotEndExpression() {
        val context = TemplateContext(values = mapOf("notification.text" to ""))
        val result = TemplateRenderer.renderResult(
            """${'$'}{notification.text|default:"\d{4,6}"}""",
            context,
        )
        assertThat(result.text).isEqualTo("""\d{4,6}""")
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.unknownVariables).isEmpty()
    }

    @Test
    fun render_appliesEveryFilter() {
        val context = TemplateContext(
            values = mapOf(
                "padded" to "  hello  ",
                "prefixed" to "TTS bugün nasılsın",
                "other" to "please TTS later",
                "short" to "abcdef",
                "emoji" to "😀😀😀",
                "mixed" to "MiXeD I",
                "empty" to "",
                "url" to "a b&c",
                "json" to "a\"b\\c\nd\te",
            ),
        )

        assertThat(render("\${padded|trim}", context)).isEqualTo("hello")
        assertThat(render("""${'$'}{prefixed|stripPrefix:"tts"}""", context))
            .isEqualTo("bugün nasılsın")
        assertThat(render("""${'$'}{other|stripPrefix:"tts"}""", context))
            .isEqualTo("please TTS later")
        assertThat(render("\${short|drop:4}", context)).isEqualTo("ef")
        assertThat(render("\${emoji|drop:1}", context)).isEqualTo("😀😀")
        assertThat(render("\${short|truncate:3}", context)).isEqualTo("abc")
        assertThat(render("\${emoji|truncate:2}", context)).isEqualTo("😀😀")
        assertThat(render("\${mixed|lower}", context)).isEqualTo("mixed i")
        assertThat(render("\${mixed|upper}", context)).isEqualTo("MIXED I")
        assertThat(render("""${'$'}{empty|default:"fallback"}""", context)).isEqualTo("fallback")
        assertThat(render("""${'$'}{padded|default:"fallback"}""", context)).isEqualTo("  hello  ")
        assertThat(render("\${url|urlencode}", context)).isEqualTo("a%20b%26c")
        assertThat(render("\${json|jsonescape}", context))
            .isEqualTo("a\\\"b\\\\c\\nd\\te")
    }

    @Test
    fun render_filterChainsApplyLeftToRight() {
        val context = TemplateContext(values = mapOf("notification.text" to "TTS   bugün nasılsın   "))
        val result = TemplateRenderer.renderResult(
            """${'$'}{notification.text|stripPrefix:"tts"|trim|upper}""",
            context,
        )
        assertThat(result.text).isEqualTo("BUGÜN NASILSIN")
        assertThat(result.parseErrors).isEmpty()
    }

    @Test
    fun render_doesNotRecursivelyRenderReplacementValues() {
        val context = TemplateContext(
            smsBody = "secret",
            values = mapOf("x" to "\${smsBody}"),
        )

        assertThat(render("\${x}", context)).isEqualTo("\${smsBody}")
        assertThat(render("\${x}\${smsBody}", context)).isEqualTo("\${smsBody}secret")
    }

    @Test
    fun render_valuesMapTakesPrecedenceForDotNamesAndFutureFields() {
        val context = TemplateContext(
            wifiSsid = "Home",
            values = mapOf(
                "wifi.ssid" to "Override",
                "notification.title" to "Başlık",
                "notification.text" to "Metin",
                "call.number" to "+905551112233",
                "rule.name" to "Kural",
                "screen.on" to "true",
            ),
        )

        assertThat(render("\${wifi.ssid}", context)).isEqualTo("Override")
        assertThat(render("\${notification.title}|\${notification.text}", context))
            .isEqualTo("Başlık|Metin")
        assertThat(render("\${call.number}|\${rule.name}|\${screen.on}", context))
            .isEqualTo("+905551112233|Kural|true")
    }

    @Test
    fun render_nullContextLeavesTemplateUnchanged() {
        val result = TemplateRenderer.renderResult("\${trigger}", null, RenderMode.STRICT)
        assertThat(result.text).isEqualTo("\${trigger}")
        assertThat(result.parseErrors).isEmpty()
    }

    @Test
    fun render_enforcesTemplateLengthLimit() {
        val template = "\${trigger}" + "a".repeat(TemplateRenderer.MAX_TEMPLATE_LENGTH)
        val result = TemplateRenderer.renderResult(template, TemplateContext(trigger = "X"))

        assertThat(template.length).isGreaterThan(TemplateRenderer.MAX_TEMPLATE_LENGTH)
        assertThat(result.text).isEqualTo(template)
        assertThat(result.parseErrors).hasSize(1)
    }

    @Test
    fun render_enforcesFilterCountLimit() {
        val eightFilters = (1..TemplateRenderer.MAX_FILTERS_PER_EXPRESSION)
            .joinToString(separator = "") { "|trim" }
        val atLimit = TemplateRenderer.renderResult(
            "\${x$eightFilters}",
            TemplateContext(values = mapOf("x" to "  value  ")),
        )
        assertThat(atLimit.text).isEqualTo("value")
        assertThat(atLimit.parseErrors).isEmpty()

        val nineFilters = "$eightFilters|trim"
        val overLimit = TemplateRenderer.renderResult(
            "\${x$nineFilters}",
            TemplateContext(values = mapOf("x" to "  value  ")),
        )
        assertThat(overLimit.text).isEqualTo("\${x$nineFilters}")
        assertThat(overLimit.parseErrors).hasSize(1)
        assertThat(overLimit.parseErrors.single()).contains("too many filters")
    }

    @Test
    fun render_enforcesVariableNameLengthLimit() {
        val longName = "a".repeat(TemplateRenderer.MAX_VARIABLE_NAME_LENGTH + 1)
        val result = TemplateRenderer.renderResult(
            "\${$longName}",
            TemplateContext(values = mapOf(longName to "value")),
        )

        assertThat(result.text).isEqualTo("\${$longName}")
        assertThat(result.parseErrors).hasSize(1)
    }

    @Test
    fun extractOtp_findsFirstFourToEightDigitSequence() {
        assertThat(TemplateRenderer.extractOtp("Your code is 4821.")).isEqualTo("4821")
        assertThat(TemplateRenderer.extractOtp("12345678 then 9999")).isEqualTo("12345678")
        assertThat(TemplateRenderer.extractOtp("no digits here")).isNull()
        assertThat(TemplateRenderer.extractOtp("only 123")).isNull()
        assertThat(TemplateRenderer.extractOtp(null)).isNull()
        assertThat(TemplateRenderer.extractOtp("   ")).isNull()
    }
}
