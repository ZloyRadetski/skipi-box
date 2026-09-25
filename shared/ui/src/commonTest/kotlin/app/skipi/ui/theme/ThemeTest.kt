// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp

class ThemeTest {
    @Test
    fun namedPalettesProvideConsistentTokens() {
        val names = listOf("aurora", "sakura", "forest", "sunset")
        names.forEach { name ->
            val palette = namedThemePaletteFor(name)
            assertNotNull(palette, "Palette $name should exist")
            assertNotNull(palette.accent)
            assertNotNull(palette.surface)
            assertNotNull(palette.background)
        }
        assertNull(namedThemePaletteFor("unknown_theme"))
    }

    @Test
    fun adaptiveWindowClassificationUsesStableBreakpoints() {
        assertEquals(SkipiWindowClass.Compact, classifySkipiWindow(599.dp))
        assertEquals(SkipiWindowClass.Medium, classifySkipiWindow(600.dp))
        assertEquals(SkipiWindowClass.Medium, classifySkipiWindow(839.dp))
        assertEquals(SkipiWindowClass.Expanded, classifySkipiWindow(840.dp))
    }

    @Test
    fun compactSpacingIsDenserThanExpandedSpacing() {
        val compact = SkipiSpacing.forWindowClass(SkipiWindowClass.Compact)
        val expanded = SkipiSpacing.forWindowClass(SkipiWindowClass.Expanded)
        assertEquals(12.dp, compact.medium)
        assertEquals(16.dp, expanded.medium)
        assertEquals(16.dp, compact.large)
        assertEquals(24.dp, expanded.large)
    }
}
