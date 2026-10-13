// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.text

import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeDefault
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import app.modes.FontFamilyModeOnest
import app.modes.FontFamilyModeUnbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontFamilyAssetTest {
    @Test
    fun persistedAndroidModesResolveToTheirExistingFontAssets() {
        assertNull(fontFamilyAssetName(FontFamilyModeDefault))
        assertEquals("inter.ttf", fontFamilyAssetName(FontFamilyModeInter))
        assertEquals("golos_text.ttf", fontFamilyAssetName(FontFamilyModeGolosText))
        assertEquals("manrope.ttf", fontFamilyAssetName(FontFamilyModeManrope))
        assertEquals("jetbrains_mono.ttf", fontFamilyAssetName(FontFamilyModeJetBrainsMono))
        assertEquals("climate_crisis.ttf", fontFamilyAssetName(FontFamilyModeClimateCrisis))
        assertEquals("unbounded.ttf", fontFamilyAssetName(FontFamilyModeUnbounded))
        assertEquals("onest.ttf", fontFamilyAssetName(FontFamilyModeOnest))
        assertNull(fontFamilyAssetName(99))
    }
}
