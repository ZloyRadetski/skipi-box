// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.modes

import org.junit.Assert.assertEquals
import org.junit.Test

class UiModesTest {

    @Test
    fun testNormalizeBottomBarSize() {
        assertEquals(BottomBarSizeSmall, normalizeBottomBarSize(BottomBarSizeSmall))
        assertEquals(BottomBarSizeMedium, normalizeBottomBarSize(BottomBarSizeMedium))
        assertEquals(BottomBarSizeLarge, normalizeBottomBarSize(BottomBarSizeLarge))

        // Invalid values default to BottomBarSizeLarge
        assertEquals(BottomBarSizeLarge, normalizeBottomBarSize(-1))
        assertEquals(BottomBarSizeLarge, normalizeBottomBarSize(99))
    }

    @Test
    fun testNormalizeBackgroundStyle() {
        assertEquals(BackgroundStyleClassic, normalizeBackgroundStyle(BackgroundStyleClassic))
        assertEquals(BackgroundStylePhoto, normalizeBackgroundStyle(BackgroundStylePhoto))
        assertEquals(BackgroundStyleConnection, normalizeBackgroundStyle(BackgroundStyleConnection))
        assertEquals(BackgroundStyleAurora, normalizeBackgroundStyle(BackgroundStyleAurora))

        // Invalid values default to BackgroundStyleClassic
        assertEquals(BackgroundStyleClassic, normalizeBackgroundStyle(-1))
        assertEquals(BackgroundStyleClassic, normalizeBackgroundStyle(99))
    }

    @Test
    fun testNormalizeColorModeKeepsAmoledMode() {
        assertEquals(ColorModeAmoled, normalizeColorMode(ColorModeAmoled))
        assertEquals(ColorModeSystem, normalizeColorMode(-1))
    }

    @Test
    fun testNormalizeFontFamilyMode() {
        assertEquals(FontFamilyModeDefault, normalizeFontFamilyMode(FontFamilyModeDefault))
        assertEquals(FontFamilyModeInter, normalizeFontFamilyMode(FontFamilyModeInter))
        assertEquals(FontFamilyModeGolosText, normalizeFontFamilyMode(FontFamilyModeGolosText))
        assertEquals(FontFamilyModeManrope, normalizeFontFamilyMode(FontFamilyModeManrope))
        assertEquals(FontFamilyModeJetBrainsMono, normalizeFontFamilyMode(FontFamilyModeJetBrainsMono))
        assertEquals(FontFamilyModeClimateCrisis, normalizeFontFamilyMode(FontFamilyModeClimateCrisis))

        // Invalid values default to FontFamilyModeDefault
        assertEquals(FontFamilyModeDefault, normalizeFontFamilyMode(-1))
        assertEquals(FontFamilyModeDefault, normalizeFontFamilyMode(99))
    }
}

