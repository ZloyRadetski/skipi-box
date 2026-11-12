// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSystemAccentTest {
    @Test
    fun windowsAccentIsReadFromTheAbgrValueOfTheRegistryQuery() {
        // 0xAABBGGRR: this is the Windows default blue, R=0x00 G=0x78 B=0xD4.
        val output = """
            HKEY_CURRENT_USER\Software\Microsoft\Windows\DWM
                AccentColor    REG_DWORD    0xffd47800
        """.trimIndent()

        assertEquals(Color(0xFF0078D4), parseWindowsAccentColor(output))
    }

    @Test
    fun windowsOutputWithoutTheValueGivesNoAccent() {
        assertNull(parseWindowsAccentColor("ERROR: The system was unable to find the specified registry key or value."))
    }

    @Test
    fun macAccentIndexMapsToItsColourAndAMissingValueIsTheDefaultBlue() {
        assertEquals(Color(0xFFFF5257), macAccentColor(0))
        assertEquals(Color(0xFF989898), macAccentColor(-1))
        assertEquals(Color(0xFF007AFF), macAccentColor(null))
        assertNull(macAccentColor(99))
    }

    @Test
    fun gnomeAccentNameMapsToItsColour() {
        assertEquals(Color(0xFF3584E4), gnomeAccentColor("'blue'\n"))
        assertEquals(Color(0xFF6F8396), gnomeAccentColor("'slate'"))
        assertNull(gnomeAccentColor("'unknown'"))
    }
}
