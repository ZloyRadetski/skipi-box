// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

import androidx.compose.ui.text.font.FontWeight
import app.AppState
import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeDefault
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import app.modes.FontFamilyModeOnest
import app.modes.FontFamilyModeUnbounded
import app.modes.FontSizeModeLarge
import app.modes.FontWeightModeBold
import app.modes.FontWeightModeDefault
import app.modes.FontWeightModeLight
import app.modes.FontWeightModeMedium
import app.modes.FontWeightModeNormal
import app.modes.FontWeightModeSemiBold
import data.backup.toAppBackupFile
import data.backup.toRestorePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

import app.modes.FontSizeModeDefault
import app.modes.FontSizeModeExtraLarge
import app.modes.FontSizeModeExtraSmall
import app.modes.FontSizeModeMedium
import app.modes.FontSizeModeSmall
import app.modes.FontSizeModeTiny
import app.modes.FontSizeModeVerySmall
import app.modes.resolveFontSizeScale

class FontFamilyModeTest {

    @Test
    fun testDefaultFontFamilyResolvesToNull() {
        assertNull(resolveFontFamily(FontFamilyModeDefault))
    }

    @Test
    fun testCustomFontFamiliesResolveNonNull() {
        assertNotNull(resolveFontFamily(FontFamilyModeInter))
        assertNotNull(resolveFontFamily(FontFamilyModeGolosText))
        assertNotNull(resolveFontFamily(FontFamilyModeManrope))
        assertNotNull(resolveFontFamily(FontFamilyModeJetBrainsMono))
        assertNotNull(resolveFontFamily(FontFamilyModeClimateCrisis))
        assertNotNull(resolveFontFamily(FontFamilyModeUnbounded))
        assertNotNull(resolveFontFamily(FontFamilyModeOnest))
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
    }

    @Test
    fun testResolveFontWeight() {
        assertNull(resolveFontWeight(FontWeightModeDefault))
        assertEquals(FontWeight.Light, resolveFontWeight(FontWeightModeLight))
        assertEquals(FontWeight.Normal, resolveFontWeight(FontWeightModeNormal))
        assertEquals(FontWeight.Medium, resolveFontWeight(FontWeightModeMedium))
        assertEquals(FontWeight.SemiBold, resolveFontWeight(FontWeightModeSemiBold))
        assertEquals(FontWeight.Bold, resolveFontWeight(FontWeightModeBold))
    }

    @Test
    fun testBackupRestorePreservesFontSettings() {
        val state = AppState(
            fontFamilyMode = FontFamilyModeJetBrainsMono,
            fontSizeMode = FontSizeModeLarge,
            fontWeightMode = FontWeightModeBold,
        )
        val backup = state.toAppBackupFile(
            createdAtMillis = 1000L,
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )
        val restored = backup.toRestorePreview().restoredState
        assertEquals(FontFamilyModeJetBrainsMono, restored.fontFamilyMode)
        assertEquals(FontSizeModeLarge, restored.fontSizeMode)
        assertEquals(FontWeightModeBold, restored.fontWeightMode)
    }
}

