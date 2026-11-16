// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingIconColorsTest {
    @Test
    fun theUsersGlyphColorIsUsedAsPickedOnAnyBackground() {
        val picked = Color(0xFF12AB34)

        assertEquals(picked, settingIconGlyphColor(background = Color.Black, picked = picked))
        assertEquals(picked, settingIconGlyphColor(background = Color.White, picked = picked))
    }

    @Test
    fun withoutAPickedColorTheGlyphReadsOnItsBackground() {
        assertEquals(Color.White, settingIconGlyphColor(background = Color(0xFF101010), picked = null))
        assertEquals(Color.Black, settingIconGlyphColor(background = Color(0xFFF5F5F5), picked = null))
    }

    @Test
    fun theTileBackgroundIsTheAccentWhateverTheGlyphColorIs() {
        val accent = Color(0xFF3B82F6)

        assertEquals(accent, settingIconBackgroundColor(accent))
        assertEquals(Color.White, settingIconGlyphColor(settingIconBackgroundColor(Color(0xFF101010)), picked = null))
    }
}
