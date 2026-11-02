package com.flowpilot.app.update

/** Keeps release notes readable without pulling a full Markdown renderer into the app. */
internal object UpdateNotesFormatter {
    private val heading = Regex("^#{1,6}\\s+")
    private val leadingSymbols = Regex("^[^\\p{L}\\p{N}]+")
    private val bullet = Regex("^[-*+]\\s+")
    private val horizontalRule = Regex("^([-*_])\\1{2,}$")
    private val link = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val inlineCode = Regex("`([^`]+)`")
    private val boldAsterisk = Regex("\\*\\*([^*]+)\\*\\*")
    private val boldUnderscore = Regex("__([^_]+)__")
    private val italicAsterisk = Regex("\\*([^*]+)\\*")
    private val italicUnderscore = Regex("_([^_]+)_")

    fun toPlainText(markdown: String): String {
        val output = StringBuilder()
        markdown.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> appendBlankLine(output)
                horizontalRule.matches(line) -> Unit
                heading.containsMatchIn(line) -> {
                    appendBlankLine(output)
                    val title = heading.replace(line, "").trim()
                    output.append(leadingSymbols.replace(title, "")).append('\n')
                }
                else -> {
                    val text = if (bullet.containsMatchIn(line)) {
                        "• ${bullet.replace(line, "").trim()}"
                    } else {
                        line
                    }
                    output.append(cleanInline(text)).append('\n')
                }
            }
        }
        return output.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanInline(text: String): String {
        var result = text
        result = inlineCode.replace(result, "$1")
        result = link.replace(result, "$1")
        result = boldUnderscore.replace(result, "$1")
        result = boldAsterisk.replace(result, "$1")
        result = italicUnderscore.replace(result, "$1")
        result = italicAsterisk.replace(result, "$1")
        return result.replace(Regex("<[^>]+>"), "").trimEnd()
    }

    private fun appendBlankLine(output: StringBuilder) {
        if (output.isNotEmpty() && !output.endsWith("\n\n")) {
            if (output.endsWith("\n")) output.append('\n') else output.append("\n\n")
        }
    }
}
