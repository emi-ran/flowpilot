package com.flowpilot.app.data.backup

import com.flowpilot.app.actions.ClipboardExecutor
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertThrows
import org.junit.Test

class ClipboardImportValidationTest {

    private fun rule(clipboardText: String) = Automation(
        id = "rule-clip",
        name = "Clipboard rule",
        triggerEvent = TriggerEvent.SMS_RECEIVED,
        actions = listOf(ActionType.COPY_TO_CLIPBOARD),
        action = ActionType.COPY_TO_CLIPBOARD,
        clipboardText = clipboardText,
        createdAt = 1L,
    )

    @Test fun acceptsTemplateWithAvailableSmsVariable() {
        ImportValidator.validate(listOf(rule("Code: \${sms.otp}")))
    }

    @Test fun rejectsBlankText() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportValidator.validate(listOf(rule("   ")))
        }
    }

    @Test fun rejectsTextOverLengthCap() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportValidator.validate(listOf(rule("x".repeat(ClipboardExecutor.MAX_TEXT_LENGTH + 1))))
        }
    }

    @Test fun rejectsVariableUnavailableForTrigger() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportValidator.validate(listOf(rule("\${unknown.variable}")))
        }
    }
}
