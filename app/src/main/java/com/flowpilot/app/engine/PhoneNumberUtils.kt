package com.flowpilot.app.engine

/**
 * Pure telephone number normalization, masking, and matching utilities.
 */
object PhoneNumberUtils {

    /**
     * Normalizes a phone number by stripping formatting characters (spaces, dashes, parentheses, dots, slashes)
     * while preserving a leading '+' if present and retaining digits.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Masks a phone number for UI display (e.g. "+90 532 ••• •• 12" or "•••• 1234"),
     * ensuring sensitive personal phone numbers are never displayed in full on summaries.
     */
    fun mask(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val normalized = normalize(trimmed)
        if (normalized.length <= 4) {
            return "••••"
        }
        val prefixLen = if (normalized.startsWith("+")) {
            minOf(4, normalized.length - 2)
        } else {
            minOf(3, normalized.length - 2)
        }
        val suffixLen = minOf(3, normalized.length - prefixLen - 1).coerceAtLeast(2)
        val prefix = normalized.take(prefixLen)
        val suffix = normalized.takeLast(suffixLen)
        val maskedMiddle = "••••"
        return "$prefix $maskedMiddle $suffix".trim()
    }

    /**
     * Evaluates whether an SMS sender matches a configured filter.
     * - A blank [filter] matches any sender.
     * - Sender identities containing letters require a case-insensitive exact match after trimming.
     * - Numeric senders use normalized number and national suffix matching.
     */
    fun matches(filter: String, actual: String?): Boolean {
        if (filter.isBlank()) return true
        if (actual.isNullOrBlank()) return false
        if (filter.any { it.isLetter() } || actual.any { it.isLetter() }) {
            return filter.trim().equals(actual.trim(), ignoreCase = true)
        }
        val normFilter = normalize(filter)
        val normActual = normalize(actual)
        if (normFilter.none { it.isDigit() } || normActual.none { it.isDigit() }) return false

        if (normFilter == normActual) return true

        // Match if one has country code prefix and other is national number (at least 7 digits)
        val digitsFilter = normFilter.filter { it.isDigit() }
        val digitsActual = normActual.filter { it.isDigit() }
        if (digitsFilter.length >= 7 && digitsActual.length >= 7) {
            if (digitsFilter.endsWith(digitsActual) || digitsActual.endsWith(digitsFilter)) {
                return true
            }
        }

        return false
    }
}
