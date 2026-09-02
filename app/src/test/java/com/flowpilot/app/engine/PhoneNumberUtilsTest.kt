package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumberUtilsTest {

    @Test
    fun normalize_extracts_digits_and_leading_plus() {
        assertThat(PhoneNumberUtils.normalize("+90 (555) 123-4567")).isEqualTo("+905551234567")
        assertThat(PhoneNumberUtils.normalize("0555 123 45 67")).isEqualTo("05551234567")
        assertThat(PhoneNumberUtils.normalize("  +1-800-FLOW-PILOT  ")).isEqualTo("+1800")
        assertThat(PhoneNumberUtils.normalize("")).isEqualTo("")
    }

    @Test
    fun mask_masks_middle_digits_leaving_safe_context() {
        assertThat(PhoneNumberUtils.mask("+905551234567")).isEqualTo("+905 •••• 567")
        assertThat(PhoneNumberUtils.mask("05551234567")).isEqualTo("055 •••• 567")
        assertThat(PhoneNumberUtils.mask("12345")).isEqualTo("123 •••• 45")
        assertThat(PhoneNumberUtils.mask("123")).isEqualTo("••••")
        assertThat(PhoneNumberUtils.mask("")).isEqualTo("")
    }

    @Test
    fun matches_blank_filter_matches_all_numbers() {
        assertThat(PhoneNumberUtils.matches(filter = "", actual = "+905551234567")).isTrue()
        assertThat(PhoneNumberUtils.matches(filter = "   ", actual = "")).isTrue()
    }

    @Test
    fun matches_exact_or_national_suffix_matches() {
        // Exact international
        assertThat(PhoneNumberUtils.matches("+905551234567", "+905551234567")).isTrue()
        // Formatted filter vs raw incoming
        assertThat(PhoneNumberUtils.matches("+90 (555) 123-4567", "+905551234567")).isTrue()
        // Local leading zero vs international
        assertThat(PhoneNumberUtils.matches("05551234567", "+905551234567")).isTrue()
        assertThat(PhoneNumberUtils.matches("+905551234567", "05551234567")).isTrue()
        // 7-digit local suffix
        assertThat(PhoneNumberUtils.matches("5551234567", "+905551234567")).isTrue()
        // Different numbers
        assertThat(PhoneNumberUtils.matches("05551112233", "05559998877")).isFalse()
    }
}
