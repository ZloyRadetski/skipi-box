// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingIconColorsTest {
    @Test
    fun theUsersGlyphColorIsUsedAsPickedOnAnyTheme() {
        val picked = Color(0xFF12AB34)

        assertEquals(picked, settingIconGlyphColor(onAccent = Color.Black, picked = picked))
        assertEquals(picked, settingIconGlyphColor(onAccent = Color.White, picked = picked))
    }

    @Test
    fun withoutAPickedColorTheGlyphHasTheThemesColorOnAccent() {
        // The same color as the text on every other accent surface, so a light accent such as orange keeps a
        // light glyph in a dark theme instead of flipping to black by contrast.
        val onAccent = Color(0xFFEDEDEF)

        assertEquals(onAccent, settingIconGlyphColor(onAccent = onAccent, picked = null))
    }

    @Test
    fun theTileBackgroundIsTheAccentWhateverTheGlyphColorIs() {
        val accent = Color(0xFF3B82F6)

        assertEquals(accent, settingIconBackgroundColor(accent))
    }
}
