// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.AppearanceSettings
import androidx.compose.ui.graphics.Color
import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import app.modes.FontFamilyModeOnest
import app.modes.FontFamilyModeUnbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopVisualThemeContractTest {
    @Test
    fun desktopThemeAppliesTheSharedAndroidCustomPalettePolicy() {
        val appearance = AppearanceSettings(
            dynamicColors = false,
            customColorsEnabled = true,
            customAccentColor = 0xFF111213L,
            customBackgroundColor = 0xFF212223L,
            customSurfaceColor = 0xFF313233L,
            customSurfaceVariantColor = 0xFF414243L,
            customTextColor = 0xFF515253L,
            customTextSecondaryColor = 0xFF616263L,
        )

        val colors = desktopAppearanceColors(DesktopThemeMode.Dark, appearance)

        assertEquals(Color(0xFF111213), colors.accent)
        assertEquals(Color(0xFF212223), colors.background)
        assertEquals(Color(0xFF313233), colors.surface)
        assertEquals(Color(0xFF414243), colors.surfaceVariant)
        assertEquals(Color(0xFF515253), colors.onBackground)
        assertEquals(Color(0xFF515253), colors.onSurface)
        assertEquals(Color(0xFF515253), colors.onAccent)
        assertEquals(Color(0xFF616263), colors.onSurfaceVariant)
    }

    @Test
    fun eachAndroidCustomFontModeLoadsItsBundledFontOnDesktop() {
        val expectedAssets = listOf(
            FontAsset(FontFamilyModeInter, "inter.ttf"),
            FontAsset(FontFamilyModeGolosText, "golos_text.ttf"),
            FontAsset(FontFamilyModeManrope, "manrope.ttf"),
            FontAsset(FontFamilyModeJetBrainsMono, "jetbrains_mono.ttf"),
            FontAsset(FontFamilyModeClimateCrisis, "climate_crisis.ttf"),
            FontAsset(FontFamilyModeUnbounded, "unbounded.ttf"),
            FontAsset(FontFamilyModeOnest, "onest.ttf"),
        )

        expectedAssets.forEach { expected ->
            val resolved = resolveDesktopFontFamily(expected.mode)

            assertNotNull(resolved, "Font mode ${expected.mode} must resolve")
            assertEquals(expected.resourceName, resolved.resourceName)
            assertNotNull(javaClass.getResource("/font/${expected.resourceName}"))
            assertTrue(resolved.family.fontList.isNotEmpty())
        }
        assertNull(resolveDesktopFontFamily(0))
        assertNull(resolveDesktopFontFamily(99))
    }

    private data class FontAsset(val mode: Int, val resourceName: String)
}
