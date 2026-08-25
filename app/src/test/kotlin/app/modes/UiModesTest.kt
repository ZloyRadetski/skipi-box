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
        assertEquals(FontFamilyModeUnbounded, normalizeFontFamilyMode(FontFamilyModeUnbounded))
        assertEquals(FontFamilyModeOnest, normalizeFontFamilyMode(FontFamilyModeOnest))

        // Invalid values default to FontFamilyModeDefault
        assertEquals(FontFamilyModeDefault, normalizeFontFamilyMode(-1))
        assertEquals(FontFamilyModeDefault, normalizeFontFamilyMode(99))
    }

    @Test
    fun testNormalizeFontSizeMode() {
        assertEquals(FontSizeModeTiny, normalizeFontSizeMode(FontSizeModeTiny))
        assertEquals(FontSizeModeExtraSmall, normalizeFontSizeMode(FontSizeModeExtraSmall))
        assertEquals(FontSizeModeVerySmall, normalizeFontSizeMode(FontSizeModeVerySmall))
        assertEquals(FontSizeModeSmall, normalizeFontSizeMode(FontSizeModeSmall))
        assertEquals(FontSizeModeDefault, normalizeFontSizeMode(FontSizeModeDefault))
        assertEquals(FontSizeModeMedium, normalizeFontSizeMode(FontSizeModeMedium))
        assertEquals(FontSizeModeLarge, normalizeFontSizeMode(FontSizeModeLarge))
        assertEquals(FontSizeModeExtraLarge, normalizeFontSizeMode(FontSizeModeExtraLarge))

        // Invalid values default to FontSizeModeDefault
        assertEquals(FontSizeModeDefault, normalizeFontSizeMode(-1))
        assertEquals(FontSizeModeDefault, normalizeFontSizeMode(99))
    }

    @Test
    fun testResolveFontSizeScale() {
        assertEquals(0.55f, resolveFontSizeScale(FontSizeModeTiny), 0.001f)
        assertEquals(0.65f, resolveFontSizeScale(FontSizeModeExtraSmall), 0.001f)
        assertEquals(0.75f, resolveFontSizeScale(FontSizeModeVerySmall), 0.001f)
        assertEquals(0.85f, resolveFontSizeScale(FontSizeModeSmall), 0.001f)
        assertEquals(1.0f, resolveFontSizeScale(FontSizeModeDefault), 0.001f)
        assertEquals(1.15f, resolveFontSizeScale(FontSizeModeMedium), 0.001f)
        assertEquals(1.30f, resolveFontSizeScale(FontSizeModeLarge), 0.001f)
        assertEquals(1.45f, resolveFontSizeScale(FontSizeModeExtraLarge), 0.001f)
        assertEquals(1.0f, resolveFontSizeScale(-1), 0.001f)
    }

    @Test
    fun testNormalizeFontWeightMode() {
        assertEquals(FontWeightModeDefault, normalizeFontWeightMode(FontWeightModeDefault))
        assertEquals(FontWeightModeLight, normalizeFontWeightMode(FontWeightModeLight))
        assertEquals(FontWeightModeNormal, normalizeFontWeightMode(FontWeightModeNormal))
        assertEquals(FontWeightModeMedium, normalizeFontWeightMode(FontWeightModeMedium))
        assertEquals(FontWeightModeSemiBold, normalizeFontWeightMode(FontWeightModeSemiBold))
        assertEquals(FontWeightModeBold, normalizeFontWeightMode(FontWeightModeBold))

        // Invalid values default to FontWeightModeDefault
        assertEquals(FontWeightModeDefault, normalizeFontWeightMode(-1))
        assertEquals(FontWeightModeDefault, normalizeFontWeightMode(99))
    }

    @Test
    fun testIsFontWeightSupported() {
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeDefault))
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeInter))
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeGolosText))
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeManrope))
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeJetBrainsMono))
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeUnbounded))
        org.junit.Assert.assertTrue(isFontWeightSupported(FontFamilyModeOnest))
        org.junit.Assert.assertFalse(isFontWeightSupported(FontFamilyModeClimateCrisis))
    }
}

