package com.flowpilot.app.engine

import java.util.Locale

/**
 * Normalizes and validates NFC tag identifiers.
 * Tags are normalized to uppercase hex string without colons or spaces (e.g. "04A1B2C3D4E5F6").
 */
object NfcTagUtils {

    /**
     * Converts a raw byte array from tag discovery intent (NfcAdapter.EXTRA_ID) into normalized hex.
     */
    fun formatTagId(rawId: ByteArray?): String {
        if (rawId == null || rawId.isEmpty()) return ""
        val sb = StringBuilder(rawId.size * 2)
        for (b in rawId) {
            sb.append(String.format(Locale.US, "%02X", b))
        }
        return sb.toString()
    }

    /**
     * Normalizes user-input or scanned string ID.
     * Strips colons, spaces, hyphens, and converts to uppercase hex.
     */
    fun normalizeTagId(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim()
            .replace(":", "")
            .replace("-", "")
            .replace(" ", "")
            .uppercase(Locale.US)
    }

    /**
     * Validates if the normalized tag ID is a non-empty valid hex string with even length.
     */
    fun isValidTagId(id: String): Boolean {
        val normalized = normalizeTagId(id)
        if (normalized.isEmpty() || normalized.length % 2 != 0) return false
        return normalized.all { it in '0'..'9' || it in 'A'..'F' }
    }
}
