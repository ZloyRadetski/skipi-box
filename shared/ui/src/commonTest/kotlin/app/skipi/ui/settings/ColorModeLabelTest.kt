// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorModeLabelTest {
    @Test
    fun withCustomColorsOffTheModeIsShownAsItIs() {
        assertEquals("Dark", colorModeLabel("Dark", customColorsOn = false, customWord = "Custom"))
    }

    @Test
    fun withCustomColorsOnTheModeSaysItIsCustomAndKeepsTheBaseMode() {
        assertEquals("Custom · Dark", colorModeLabel("Dark", customColorsOn = true, customWord = "Custom"))
    }
}
