// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryFlagCodeTest {
    // Regional-indicator pairs written as escapes so the test does not depend on the source encoding.
    private val netherlands = "🇳🇱"
    private val finland = "🇫🇮"
    private val europeanUnion = "🇪🇺"

    @Test
    fun flagEmojiMapsToLowercaseIsoCode() {
        assertEquals("nl", CountryFlagUtils.countryCodeOfFlag(netherlands))
        assertEquals("fi", CountryFlagUtils.countryCodeOfFlag(finland))
        assertEquals("eu", CountryFlagUtils.countryCodeOfFlag(europeanUnion))
    }

    @Test
    fun surroundingTextOrNonFlagsGiveNoCode() {
        assertEquals("nl", CountryFlagUtils.countryCodeOfFlag(" $netherlands Amsterdam"))
        assertNull(CountryFlagUtils.countryCodeOfFlag(null))
        assertNull(CountryFlagUtils.countryCodeOfFlag(""))
        assertNull(CountryFlagUtils.countryCodeOfFlag("Amsterdam"))
        assertNull(CountryFlagUtils.countryCodeOfFlag("🌐")) // globe emoji
    }
}
