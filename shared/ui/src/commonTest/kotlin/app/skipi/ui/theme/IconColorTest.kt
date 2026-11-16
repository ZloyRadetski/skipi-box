// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.skipi.ui.components.resolveDrawnIconColor
import app.skipi.ui.components.resolveIconTint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconColorTest {
    private val themes = listOf("aurora", "sakura", "forest", "sunset")

    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun everyNamedThemeHasIconsThatReadOnItsBackgroundAndSurface() {
        themes.forEach { name ->
            val palette = namedThemePaletteFor(name)!!
            assertTrue(contrast(palette.icon, palette.background) >= 3f, "$name icons on the background")
            assertTrue(contrast(palette.icon, palette.surface) >= 3f, "$name icons on the surface")
        }
    }

    @Test
    fun aNamedThemeHandsItsIconColorToTheAppColors() {
        themes.forEach { name ->
            val palette = namedThemePaletteFor(name)!!
            assertEquals(palette.icon, resolveBaseAppearanceColors(isDark = true, namedPalette = palette).icon, name)
        }
    }

    @Test
    fun plainThemesLeaveIconsInTheHostsOwnDefaultColor() {
        assertEquals(Color.Unspecified, resolveBaseAppearanceColors(isDark = true).icon)
        assertEquals(Color.Unspecified, resolveBaseAppearanceColors(isDark = false).icon)
    }

    private val none = Color.Unspecified
    private val picked = Color(0xFF12AB34)
    private val theme = Color(0xFF8FC1FF)
    private val status = Color(0xFF22C55E)
    private val onAccent = Color.White

    @Test
    fun theColorTheUserPickedColorsEveryIcon() {
        assertEquals(picked, resolveIconTint(explicit = none, picked = picked, themeIcon = theme, onAccent = null))
        assertEquals(picked, resolveIconTint(explicit = none, picked = picked, themeIcon = none, onAccent = onAccent))
    }

    @Test
    fun withoutAPickedColorTheThemesIconColorIsUsed() {
        assertEquals(theme, resolveIconTint(explicit = none, picked = null, themeIcon = theme, onAccent = null))
        assertEquals(onAccent, resolveIconTint(explicit = none, picked = null, themeIcon = theme, onAccent = onAccent))
    }

    @Test
    fun aPlainThemeLeavesTheHostsDefaultAlone() {
        assertEquals(none, resolveIconTint(explicit = none, picked = null, themeIcon = none, onAccent = null))
    }

    @Test
    fun aStateColorGivesWayToThePickedOneButBeatsTheThemes() {
        val state = Color(0xFFAA5500)
        assertEquals(picked, resolveIconTint(none, picked, theme, null, fallback = state))
        assertEquals(state, resolveIconTint(none, null, theme, null, fallback = state))
    }

    @Test
    fun aDrawnIconTakesThePickedColorAndKeepsItsOwnTransparency() {
        val dim = Color(0xFF101010).copy(alpha = 0.75f)

        assertEquals(picked.copy(alpha = 0.75f), resolveDrawnIconColor(picked, theme, dim))
        assertEquals(theme.copy(alpha = 0.75f), resolveDrawnIconColor(null, theme, dim))
        assertEquals(dim, resolveDrawnIconColor(null, none, dim))
    }

    @Test
    fun aStatusColorIsNeverRepainted() {
        assertEquals(status, resolveIconTint(explicit = status, picked = picked, themeIcon = theme, onAccent = onAccent))
    }
}
