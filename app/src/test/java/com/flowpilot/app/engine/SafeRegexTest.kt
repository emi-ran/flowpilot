package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeRegexTest {

    @Test
    fun containsMatch_finds_case_insensitive_match() {
        assertThat(SafeRegex.containsMatch("""\b\d{6}\b""", "Kod 654321 geçerli")).isTrue()
        assertThat(SafeRegex.containsMatch("onay kodu", "ONAY KODU 1")).isTrue()
        assertThat(SafeRegex.containsMatch("""\b\d{6}\b""", "Telefon güncellendi")).isFalse()
    }

    @Test
    fun containsMatch_invalid_pattern_is_no_match() {
        assertThat(SafeRegex.containsMatch("[", "Anything [")).isFalse()
    }

    @Test
    fun containsMatch_catastrophic_pattern_returns_quickly_as_no_match() {
        val input = "a".repeat(40) + "b"

        val started = System.nanoTime()
        val matched = SafeRegex.containsMatch("(a+)+$", input)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertThat(matched).isFalse()
        // Budget is 100 ms; allow generous slack for slow CI machines.
        assertThat(elapsedMs).isLessThan(2_000L)
    }

    @Test
    fun containsMatch_input_beyond_length_cap_is_ignored() {
        val input = "x".repeat(SafeRegex.MAX_INPUT_CHARS) + "needle"

        assertThat(SafeRegex.containsMatch("needle", input)).isFalse()
        assertThat(SafeRegex.containsMatch("needle", "needle " + "x".repeat(SafeRegex.MAX_INPUT_CHARS))).isTrue()
    }
}
