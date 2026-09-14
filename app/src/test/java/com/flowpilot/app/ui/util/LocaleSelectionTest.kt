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
}
