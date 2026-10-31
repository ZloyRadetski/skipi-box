// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.FontLoadResult
import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import app.modes.FontFamilyModeOnest
import app.modes.FontFamilyModeUnbounded
import org.jetbrains.skia.Data as SkiaData
import org.jetbrains.skia.FontMgr as SkiaFontMgr
import org.jetbrains.skia.Typeface as SkiaTypeface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs

class DesktopVisualThemeContractTest {
    @Test
    fun desktopDarkLightAmoledAndNamedPalettesMatchAndroidBaseRoles() {
        val noCustomColors = AppearanceSettings(dynamicColors = false)

        assertEquals(
            app.skipi.ui.theme.AppColors(
                background = Color(0xFF16171A),
                onBackground = Color(0xFFEDEDEF),
                accent = app.skipi.ui.theme.DefaultAppAccentColor,
                onAccent = Color(0xFFEDEDEF),
                surface = Color(0xFF202227),
                onSurface = Color(0xFFEDEDEF),
                surfaceVariant = Color(0xFF282A31),
                onSurfaceVariant = Color(0xFF9EA3AE),
                isDark = true,
            ),
            desktopAppearanceColors(DesktopThemeMode.Dark, noCustomColors),
        )
        assertEquals(
            app.skipi.ui.theme.AppColors(
                background = Color(0xFFF5F6F8),
                onBackground = Color(0xFF1B1C1E),
                accent = app.skipi.ui.theme.DefaultAppAccentColor,
                onAccent = Color(0xFF1B1C1E),
                surface = Color.White,
                onSurface = Color(0xFF1B1C1E),
                surfaceVariant = Color(0xFFE8EAEE),
                onSurfaceVariant = Color(0xFF6B7280),
                isDark = false,
            ),
            desktopAppearanceColors(DesktopThemeMode.Light, noCustomColors),
        )
        assertEquals(
            app.skipi.ui.theme.AppColors(
                background = Color.Black,
                onBackground = Color(0xFFEDEDEF),
                accent = app.skipi.ui.theme.DefaultAppAccentColor,
                onAccent = Color(0xFFEDEDEF),
                surface = Color(0xFF0A0A0A),
                onSurface = Color(0xFFEDEDEF),
                surfaceVariant = Color(0xFF141414),
                onSurfaceVariant = Color(0xFF9EA3AE),
                isDark = true,
            ),
            desktopAppearanceColors(DesktopThemeMode.Amoled, noCustomColors),
        )
        assertEquals(
            app.skipi.ui.theme.namedThemePaletteFor("aurora")!!.let { palette ->
                app.skipi.ui.theme.AppColors(
                    background = palette.background,
                    onBackground = palette.onBackground,
                    accent = palette.accent,
                    onAccent = palette.onAccent,
                    surface = palette.surface,
                    onSurface = palette.onSurface,
                    surfaceVariant = palette.surfaceVariant,
                    onSurfaceVariant = palette.onSurfaceVariant,
                    isDark = true,
                )
            },
            desktopAppearanceColors(
                DesktopThemeMode.Aurora,
                noCustomColors.copy(themeMode = ThemeMode.Named, themeVariant = "aurora"),
            ),
        )
    }

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
        val fontFamilyResolver = createFontFamilyResolver()

        expectedAssets.forEach { expected ->
            val resolved = resolveDesktopFontFamily(expected.mode)

            assertNotNull(resolved, "Font mode ${expected.mode} must resolve")
            assertEquals(expected.resourceName, resolved.resourceName)
            val fontResource = assertNotNull(javaClass.getResource("/font/${expected.resourceName}"))
            val expectedFamilyName = fontResource.openStream().use { stream ->
                val fontData = SkiaData.makeFromBytes(stream.readBytes())
                try {
                    val typeface = assertNotNull(
                        SkiaFontMgr.default.makeFromData(fontData),
                        "Skia must parse ${expected.resourceName}",
                    )
                    try {
                        typeface.familyName
                    } finally {
                        typeface.close()
                    }
                } finally {
                    fontData.close()
                }
            }
            val loadResult = assertIs<FontLoadResult>(
                fontFamilyResolver.resolve(
                    fontFamily = resolved.family,
                    fontWeight = FontWeight.Normal,
                ).value,
            )
            val loadedTypeface = assertNotNull(loadResult.typeface, "Compose must load ${expected.resourceName}")
            assertEquals(expectedFamilyName, loadedTypeface.familyName)
        }
        assertNull(resolveDesktopFontFamily(0))
        assertNull(resolveDesktopFontFamily(99))
    }

    private data class FontAsset(val mode: Int, val resourceName: String)
}
