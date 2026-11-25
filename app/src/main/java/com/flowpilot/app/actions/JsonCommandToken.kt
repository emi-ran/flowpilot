package com.flowpilot.app.actions

import java.security.MessageDigest

/**
 * Optional shared-secret check for JSON commands.
 *
 * The secret lives in the rule's encrypted source template as a first line `@token=<secret>`; it is
 * split off before rendering, so incoming notification or SMS text can never define or alter it.
 * Senders include the same value as the root `"token"` string of the command.
 */
object JsonCommandToken {
    const val PREFIX = "@token="
    const val MAX_LENGTH = 128

    data class Split(val token: String, val template: String)

    /** Separates a leading `@token=` line from the template. Sources without it are returned unchanged. */
    fun split(rawSource: String): Split {
        if (!rawSource.startsWith(PREFIX)) return Split("", rawSource)
        val end = rawSource.indexOfFirst { it == '\n' || it == '\r' }.let { if (it < 0) rawSource.length else it }
        val token = rawSource.substring(PREFIX.length, end).trim()
        var rest = end
        if (rest < rawSource.length && rawSource[rest] == '\r') rest++
        if (rest < rawSource.length && rawSource[rest] == '\n') rest++
        return Split(token, rawSource.substring(rest))
    }

    /** True when no token is required, or when [provided] equals [expected] (constant-time comparison). */
    fun isAuthorized(expected: String, provided: String?): Boolean {
        if (expected.isBlank()) return true
        if (provided == null) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val a = digest.digest(expected.toByteArray(Charsets.UTF_8))
        val b = digest.digest(provided.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(a, b)
    }
}
