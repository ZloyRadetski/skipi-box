// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.backup.AppBackupSettings
import features.backup.customColors
import features.backup.withCustomColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MASQUE has a colour of its own that the user may change, like the other protocols. */
class CustomColorsMasqueTest {
    private val colors = CustomColors(protocolMasque = 0xFF123456, protocolHysteria2 = 0xFF654321)

    @Test
    fun noMasqueColorIsTheDefault() {
        assertNull(CustomColors().protocolMasque)
        assertNull(AppState().customProtocolMasqueColor)
        assertNull(AppBackupSettings().customProtocolMasqueColor)
    }

    @Test
    fun theAppStateKeepsTheMasqueColorApartFromTheOthers() {
        val state = AppState().withCustomColors(colors)

        assertEquals(0xFF123456, state.customProtocolMasqueColor)
        assertEquals(0xFF654321, state.customProtocolHysteria2Color)
        assertEquals(colors, state.customColors())
    }

    @Test
    fun theBackupKeepsTheMasqueColor() {
        val settings = AppBackupSettings().withCustomColors(colors)

        assertEquals(0xFF123456, settings.customProtocolMasqueColor)
        assertEquals(colors, settings.customColors())
    }
}
