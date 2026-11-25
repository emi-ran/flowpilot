package com.flowpilot.app.diagnostics

/**
 * Strict sanitizer for the few free-form diagnostic strings (detail / attempted summary) and for
 * report fields. Pure Kotlin. Anything outside a small allow-list is replaced, and number runs
 * that could be phone numbers, codes or addresses are redacted.
 */
object DiagnosticText {
    const val REDACTED = "[redacted]"
    const val MAX_LENGTH = 160

    private val url = Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S*""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val macLike = Regex("""(?i)\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\b""")
    private val phoneLike = Regex("""\+?\d(?:[\s().-]?\d){6,}""")
    private val secretAssign = Regex("""(?i)\b(token|secret|password|passwd|key|auth|bearer|otp|code)\s*[:=]\s*\S+""")
    /** Stand-alone 4-8 digit numbers (one-time codes); values after '=' such as "percent=40" are kept. */
    private val otpLike = Regex("""(?<![=\w.])\d{4,8}(?![\w.])""")
    private val disallowed = Regex("""[^A-Za-z0-9_.:=,;/+\- \[\]%]""")

    /** Redacts URLs, addresses, e-mails, secret assignments and phone-like numbers; keeps other text. */
    fun redactFreeText(text: String): String {
        var out = url.replace(text, REDACTED)
        out = macLike.replace(out, REDACTED)
        out = email.replace(out, REDACTED)
        out = secretAssign.replace(out) { it.groupValues[1] + "=" + REDACTED }
        return phoneLike.replace(out, REDACTED)
    }

    fun sanitize(text: String?, maxLength: Int = MAX_LENGTH): String? {
        if (text.isNullOrBlank()) return null
        var out = redactFreeText(text)
        out = otpLike.replace(out, REDACTED)
        out = disallowed.replace(out, "?")
        out = out.replace(Regex("""\s+"""), " ").trim()
        if (out.length > maxLength) out = out.take(maxLength)
        return out.ifBlank { null }
    }
}
