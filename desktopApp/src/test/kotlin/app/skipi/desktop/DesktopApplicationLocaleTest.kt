// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.settings_category_general
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopApplicationLocaleTest {
    @Test
    fun languageChangesSelectLocalizedComposeResourcesAndNullRestoresStartupLocale() = runBlocking {
        val processLocale = Locale.getDefault()
        try {
            val locale = DesktopApplicationLocale(
                systemLocale = Locale.US,
                applyLocale = Locale::setDefault,
            )

            locale.apply("ru")
            assertEquals("Общие настройки", getString(Res.string.settings_category_general))

            locale.apply("en")
            assertEquals("General", getString(Res.string.settings_category_general))

            locale.apply(null)
            assertEquals(Locale.US, Locale.getDefault())
            assertEquals("General", getString(Res.string.settings_category_general))
        } finally {
            Locale.setDefault(processLocale)
        }
    }

    @Test
    fun languageAdapterRestoresTheCapturedSystemLocaleAfterLaterChanges() {
        val applied = mutableListOf<Locale>()
        val adapter = DesktopApplicationLocale(
            systemLocale = Locale.forLanguageTag("fa-IR"),
            applyLocale = applied::add,
        )

        adapter.apply("ru")
        adapter.apply("en")
        adapter.apply(null)

        assertEquals(
            listOf(Locale.forLanguageTag("ru"), Locale.forLanguageTag("en"), Locale.forLanguageTag("fa-IR")),
            applied,
        )
    }
}
