package com.flowpilot.app.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemplateTextFieldTest {

    @Test
    fun insertVariableAtCursorPreservesSurroundingText() {
        val result = insertVariableAtSelection(
            TextFieldValue("salam", TextRange(2)),
            "\${rule.name}",
        )

        assertThat(result.text).isEqualTo("sa\${rule.name}lam")
        assertThat(result.selection).isEqualTo(TextRange(14))
    }

    @Test
    fun insertVariableReplacesSelectedText() {
        val result = insertVariableAtSelection(
            TextFieldValue("hello world", TextRange(6, 11)),
            "\${sms.sender}",
        )

        assertThat(result.text).isEqualTo("hello \${sms.sender}")
        assertThat(result.selection).isEqualTo(TextRange(19))
    }

    @Test
    fun insertVariableIntoEmptyFieldSelectsTheEnd() {
        val result = insertVariableAtSelection(TextFieldValue(""), "\${time}")

        assertThat(result.text).isEqualTo("\${time}")
        assertThat(result.selection).isEqualTo(TextRange(7))
    }
}
