// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkipiConfigDocumentTest {
    @Test
    fun parsesTheEffectivePortablePerAppPolicyAndDeduplicatesPackages() {
        val settings = """
            [SKIPI]
            per-app-mode = blacklist
            per-app-package = com.example.one
            per-app-package = com.example.two
            per-app-package = com.example.one
            per-app-mode = whitelist
        """.trimIndent().parseSkipiPerAppSettings()

        assertEquals(SkipiPerAppModeWhitelist, settings.mode)
        assertEquals(listOf("com.example.one", "com.example.two"), settings.selectedApps)
    }

    @Test
    fun normalizesLegacyPerAppCommentsWithoutDroppingOtherSkipiValues() {
        val original = """
            # SKIPI-PER-APP-MODE: whitelist
            # SKIPI-PER-APP: com.legacy.one
            # SKIPI-PER-APP: com.legacy.one
            [SKIPI]
            enable-fake-dns = true
        """.trimIndent()

        val updated = original.withSkipiPerAppSettings(
            mode = SkipiPerAppModeBlacklist,
            selectedApps = listOf("com.example.one", "com.example.one", " ", "com.example.two"),
        )

        assertFalse(updated.contains(SkipiPerAppModeComment))
        assertFalse(updated.contains(SkipiPerAppItemComment))
        assertTrue(updated.contains("enable-fake-dns = true"))
        assertEquals("blacklist", updated.skipiSectionValues()[SkipiPerAppMode]?.single())
        assertEquals(
            listOf("com.example.one", "com.example.two"),
            updated.skipiSectionValues()[SkipiPerAppPackage],
        )
    }

    @Test
    fun retainsRepeatedValuesAndUsesDocumentedBooleanSpellings() {
        val values = """
            [SKIPI]
            profile-update-url = https://first.example/config
            profile-update-url = https://effective.example/config
        """.trimIndent().skipiSectionValues()

        assertEquals(
            listOf("https://first.example/config", "https://effective.example/config"),
            values[SkipiProfileUpdateUrl],
        )
        assertTrue("yes".toSkipiConfigBoolean(false))
        assertFalse("0".toSkipiConfigBoolean(true))
        assertTrue("unexpected".toSkipiConfigBoolean(true))
    }
}
