// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

import app.AppState
import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeDefault
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import data.backup.toAppBackupFile
import data.backup.toRestorePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
    }

    @Test
    fun testBackupRestorePreservesFontFamilyMode() {
        val state = AppState(fontFamilyMode = FontFamilyModeJetBrainsMono)
        val backup = state.toAppBackupFile(
            createdAtMillis = 1000L,
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )
        val restored = backup.toRestorePreview().restoredState
        assertEquals(FontFamilyModeJetBrainsMono, restored.fontFamilyMode)
    }
}
