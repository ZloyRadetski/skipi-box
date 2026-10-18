// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class BaseAppearancePaletteTest {
    @Test
    fun defaultDarkPaletteMatchesAndroidTheme() {
        assertEquals(
            AppColors(
                background = Color(0xFF16171A),
                onBackground = Color(0xFFEDEDEF),
                accent = DefaultAppAccentColor,
                onAccent = Color(0xFFEDEDEF),
                surface = Color(0xFF202227),
                onSurface = Color(0xFFEDEDEF),
                surfaceVariant = Color(0xFF282A31),
                onSurfaceVariant = Color(0xFF9EA3AE),
                isDark = true,
            ),
            resolveBaseAppearanceColors(isDark = true),
        )
    }

    @Test
    fun defaultLightPaletteMatchesAndroidTheme() {
        assertEquals(
            AppColors(
                background = Color(0xFFF5F6F8),
                onBackground = Color(0xFF1B1C1E),
                accent = DefaultAppAccentColor,
                onAccent = Color(0xFF1B1C1E),
                surface = Color.White,
                onSurface = Color(0xFF1B1C1E),
                surfaceVariant = Color(0xFFE8EAEE),
                onSurfaceVariant = Color(0xFF6B7280),
                isDark = false,
            ),
            resolveBaseAppearanceColors(isDark = false),
        )
    }

    @Test
    fun amoledPaletteMatchesAndroidBlackSurfaces() {
        assertEquals(
            AppColors(
                background = Color.Black,
                onBackground = Color(0xFFEDEDEF),
                accent = DefaultAppAccentColor,
                onAccent = Color(0xFFEDEDEF),
                surface = Color(0xFF0A0A0A),
                onSurface = Color(0xFFEDEDEF),
                surfaceVariant = Color(0xFF141414),
                onSurfaceVariant = Color(0xFF9EA3AE),
                isDark = true,
            ),
            resolveBaseAppearanceColors(isDark = true, amoledBlack = true),
        )
    }

    @Test
    fun namedPaletteUsesAndroidSharedRolesAndAccent() {
        val named = namedThemePaletteFor("aurora")!!

        assertEquals(
            AppColors(
                background = named.background,
                onBackground = named.onBackground,
                accent = named.accent,
                onAccent = named.onAccent,
                surface = named.surface,
                onSurface = named.onSurface,
                surfaceVariant = named.surfaceVariant,
                onSurfaceVariant = named.onSurfaceVariant,
                isDark = true,
            ),
            resolveBaseAppearanceColors(isDark = true, namedPalette = named),
        )
    }

    @Test
    fun hostDynamicPrimaryIsUsedForNonNamedBasePalette() {
        val dynamicPrimary = Color(0xFF8756D8)

        assertEquals(
            dynamicPrimary,
            resolveBaseAppearanceColors(isDark = true, dynamicAccent = dynamicPrimary).accent,
        )
    }
}
