// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWindowChromeTest {
    @Test
    fun windowsColorsAreBlueGreenRedNotRedGreenBlue() {
        // COLORREF is 0x00BBGGRR.
        assertEquals(0x00_33_22_11, windowsColorRef(Color(red = 0x11, green = 0x22, blue = 0x33)))
        assertEquals(0x00_FF_FF_FF, windowsColorRef(Color.White))
        assertEquals(0x00_00_00_00, windowsColorRef(Color.Black))
    }

    @Test
    fun theTranslucencyOfAColorDoesNotLeakIntoTheWindowsColor() {
        assertEquals(0x00_00_00_FF, windowsColorRef(Color(red = 255, green = 0, blue = 0, alpha = 10)))
    }

    @Test
    fun titleTextIsLightOnDarkBarsAndDarkOnLightBars() {
        assertEquals(Color.White, readableTitleTextColor(Color(0xFF0F1114)))
        assertEquals(Color.White, readableTitleTextColor(Color.Black))
        assertEquals(Color.Black, readableTitleTextColor(Color(0xFFF6F6F6)))
        assertEquals(Color.Black, readableTitleTextColor(Color.White))
    }

    @Test
    fun theMinimumWindowIsAboutAPhoneAndFitsInsideTheStartingWindow() {
        assertTrue(DesktopMinimumWindowWidth in 320..420, "phone-like width, got $DesktopMinimumWindowWidth")
        assertTrue(DesktopMinimumWindowHeight in 560..720, "phone-like height, got $DesktopMinimumWindowHeight")
        assertTrue(DesktopMinimumWindowWidth < DesktopInitialWindowWidth)
        assertTrue(DesktopMinimumWindowHeight < DesktopInitialWindowHeight)
    }
}
