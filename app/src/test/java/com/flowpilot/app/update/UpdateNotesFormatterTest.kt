package com.flowpilot.app.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateNotesFormatterTest {

    @Test
    fun strips_headings_rules_bullets_and_inline_markdown() {
        val notes = """
            First paragraph with `code` and **bold**.

            ---

            ## What changed
            - Added `HOTSPOT_ON` support.
            - Fixed *slow* start.

            [Full notes](https://example.test)
        """.trimIndent()

        assertThat(UpdateNotesFormatter.toPlainText(notes)).isEqualTo(
            """
                First paragraph with code and bold.

                What changed
                • Added HOTSPOT_ON support.
                • Fixed slow start.

                Full notes
            """.trimIndent(),
        )
    }

    @Test
    fun keeps_placeholder_when_notes_have_no_visible_text() {
        assertThat(UpdateNotesFormatter.toPlainText("\n---\n\n")).isEmpty()
        assertThat(UpdateNotesFormatter.toPlainText("## 🔀 Multi-Trigger Rules")).isEqualTo("Multi-Trigger Rules")
    }
}
