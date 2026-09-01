// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryFlagTest {
    @Test
    fun extractsAndStripsLeadingRegionalCountryFlag() {
        assertEquals("🇳🇱", "  🇳🇱 Amsterdam 1".extractLeadingCountryFlagOrNull())
        assertEquals("Amsterdam 1", "  🇳🇱 Amsterdam 1".stripLeadingCountryFlag())
    }

    @Test
    fun keepsTitlesThatContainOnlyAFlag() {
        assertEquals("🇫🇮", "🇫🇮".stripLeadingCountryFlag())
    }

    @Test
    fun leavesPlainTextUntouched() {
        assertNull("Amsterdam 1".extractLeadingCountryFlagOrNull())
        assertEquals("Amsterdam 1", "Amsterdam 1".stripLeadingCountryFlag())
    }
}
