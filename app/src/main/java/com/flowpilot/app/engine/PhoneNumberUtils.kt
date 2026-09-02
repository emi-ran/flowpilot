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
     * Evaluates whether an incoming/outgoing call's actual number matches a configured filter.
     * - If [filter] is blank -> matches any call (even when [actual] is null/empty).
     * - If [filter] is filled -> requires [actual] to be non-empty and matching when normalized.
     */
    fun matches(filter: String, actual: String?): Boolean {
        val normFilter = normalize(filter)
        if (normFilter.isEmpty()) return true
        if (actual.isNullOrBlank()) return false
        val normActual = normalize(actual)
        if (normActual.isEmpty()) return false

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
