package com.flowpilot.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleSelectionTest {

    @Test
    fun `resolveLocaleLanguage maps supported language tags and defaults cleanly`() {
        assertEquals("tr", resolveLocaleLanguage("tr"))
        assertEquals("tr", resolveLocaleLanguage("TR"))
        assertEquals("tr", resolveLocaleLanguage("Tr"))
        assertEquals("en", resolveLocaleLanguage("en"))
        assertEquals("en", resolveLocaleLanguage("EN"))
        assertEquals("system", resolveLocaleLanguage("system"))
        assertEquals("system", resolveLocaleLanguage(null))
        assertEquals("system", resolveLocaleLanguage(""))
        assertEquals("system", resolveLocaleLanguage("de"))
        assertEquals("system", resolveLocaleLanguage("fr"))
    }

    @Test
    fun `targetLocaleForLanguage returns explicit locale for supported tags and fallback for others`() {
        val fallback = Locale("en", "US")

        val trLocale = targetLocaleForLanguage("tr", fallback)
        assertEquals("tr", trLocale.language)

        val enLocale = targetLocaleForLanguage("en", fallback)
        assertEquals("en", enLocale.language)

        val systemLocale = targetLocaleForLanguage("system", fallback)
        assertEquals(fallback, systemLocale)

        val unknownLocale = targetLocaleForLanguage("unknown", fallback)
        assertEquals(fallback, unknownLocale)
    }

    @Test
    fun `targetLocaleForLanguage with system uses supplied system locale regardless of mutated Locale default`() {
        val previousDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val systemLocale = Locale("de", "DE")
            val resolved = targetLocaleForLanguage("system", systemLocale)
            assertEquals("de", resolved.language)
            assertEquals(systemLocale, resolved)
        } finally {
            Locale.setDefault(previousDefault)
        }
    }

    @Test
    fun `systemResourcesLocale safely returns non-null fallback in test environment`() {
        val locale = systemResourcesLocale()
        org.junit.Assert.assertNotNull(locale)
    }

    @Test
    fun `targetLocaleForLanguage with system defaults to systemResourcesLocale`() {
        val expected = systemResourcesLocale()
        val actual = targetLocaleForLanguage("system")
        assertEquals(expected, actual)
    }
}
