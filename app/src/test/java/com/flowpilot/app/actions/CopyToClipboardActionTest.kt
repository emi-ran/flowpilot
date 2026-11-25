package com.flowpilot.app.actions

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyToClipboardActionTest {

    private lateinit var app: Application
    private var captured: ClipData? = null

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        captured = null
    }

    private fun executor() = ClipboardExecutor(app, setPrimaryClip = { captured = it })

    private fun rule(clipboardText: String) = Automation(
        id = "rule-clip",
        name = "Clipboard rule",
        createdAt = 1L,
        triggerEvent = TriggerEvent.SMS_RECEIVED,
        action = ActionType.COPY_TO_CLIPBOARD,
        actions = listOf(ActionType.COPY_TO_CLIPBOARD),
        clipboardText = clipboardText,
    )

    // Factory: template rendering

    @Test fun factory_rendersOtpVariableIntoClipboardText() {
        val parameters = ActionParametersFactory.create(
            rule("Code: \${sms.otp}"),
            TemplateContext(trigger = "SMS_RECEIVED", smsOtp = "123456"),
        )

        assertThat(parameters.clipboardText).isEqualTo("Code: 123456")
    }

    @Test fun factory_keepsBlankClipboardTextBlank() {
        val parameters = ActionParametersFactory.create(rule(""))

        assertThat(parameters.clipboardText).isEmpty()
    }

    // Executor: pure validation and clipboard write

    @Test fun validationReason_rejectsBlankAndOverlongText() {
        assertThat(ClipboardExecutor.validationReason("")).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
        assertThat(ClipboardExecutor.validationReason("   ")).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
        assertThat(ClipboardExecutor.validationReason("x".repeat(ClipboardExecutor.MAX_TEXT_LENGTH + 1)))
            .isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
        assertThat(ClipboardExecutor.validationReason("x".repeat(ClipboardExecutor.MAX_TEXT_LENGTH))).isNull()
    }

    @Test fun shouldMarkSensitive_onlyFromAndroid13() {
        assertThat(ClipboardExecutor.shouldMarkSensitive(Build.VERSION_CODES.S_V2)).isFalse()
        assertThat(ClipboardExecutor.shouldMarkSensitive(Build.VERSION_CODES.TIRAMISU)).isTrue()
    }

    @Test fun execute_writesRenderedTextAsSensitiveClip() {
        val result = executor().execute(
            ActionType.COPY_TO_CLIPBOARD,
            ActionParameters(clipboardText = "123456"),
        )

        assertThat(result.success).isTrue()
        assertThat(result.resultCode).isEqualTo(ActionResultCode.COMPLETED)
        val clip = captured
        assertThat(clip).isNotNull()
        assertThat(clip!!.getItemAt(0).text.toString()).isEqualTo("123456")
        assertThat(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) ?: false).isTrue()
    }

    @Test fun execute_resultMessageNeverContainsClippedText() {
        val result = executor().execute(
            ActionType.COPY_TO_CLIPBOARD,
            ActionParameters(clipboardText = "secret-otp-998877"),
        )

        assertThat(result.message).doesNotContain("secret-otp-998877")
        assertThat(result.resultArgs).isEmpty()
    }

    @Test fun execute_rejectsBlankTextWithoutWriting() {
        val result = executor().execute(ActionType.COPY_TO_CLIPBOARD, ActionParameters(clipboardText = "  "))

        assertThat(result.success).isFalse()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
        assertThat(captured).isNull()
    }

    @Test fun execute_reportsWriteFailureGenerically() {
        val failing = ClipboardExecutor(app, setPrimaryClip = { error("clipboard down") })

        val result = failing.execute(ActionType.COPY_TO_CLIPBOARD, ActionParameters(clipboardText = "abc"))

        assertThat(result.success).isFalse()
        assertThat(result.message).doesNotContain("abc")
    }

    // JSON command parsing

    @Test fun jsonCommand_parsesTextParameter() {
        val parsed = JsonCommandParser.parse(
            """{"version":1,"action":"COPY_TO_CLIPBOARD","params":{"text":"hello"}}""",
        )

        assertThat(parsed).isInstanceOf(JsonCommandParser.Result.Success::class.java)
        parsed as JsonCommandParser.Result.Success
        assertThat(parsed.action).isEqualTo(ActionType.COPY_TO_CLIPBOARD)
        assertThat(parsed.parameters.clipboardText).isEqualTo("hello")
    }

    @Test fun jsonCommand_rejectsBlankMissingExtraAndOverlongText() {
        val cases = listOf(
            """{"text":" "}""",
            "{}",
            """{"text":"hello","extra":true}""",
            """{"text":"${"x".repeat(ClipboardExecutor.MAX_TEXT_LENGTH + 1)}"}""",
            """{"text":123}""",
        )
        for (params in cases) {
            val parsed = JsonCommandParser.parse("""{"version":1,"action":"COPY_TO_CLIPBOARD","params":$params}""")
            assertThat(parsed).isInstanceOf(JsonCommandParser.Result.Error::class.java)
        }
    }
}
