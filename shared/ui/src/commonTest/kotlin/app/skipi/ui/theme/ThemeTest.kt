// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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

    @Test
    fun simpleMiuixPaletteUsesTheRequestedLightOrDarkBase() {
        assertEquals(lightColorScheme().surface, miuixBaseColorsFor(isDark = false).surface)
        assertEquals(darkColorScheme().surface, miuixBaseColorsFor(isDark = true).surface)
    }

    @Test
    fun darkAppPaletteMapsMiuixControlAndStateRoles() {
        val appColors = AppColors(
            background = Color(0xFF141519),
            onBackground = Color(0xFFF1F1F3),
            accent = Color(0xFFE7E7EA),
            onAccent = Color(0xFF202126),
            surface = Color(0xFF202126),
            onSurface = Color(0xFFF1F1F3),
            surfaceVariant = Color(0xFF2A2C32),
            onSurfaceVariant = Color(0xFF9A9DA8),
            isDark = true,
            primaryContainer = Color(0xFF303239),
            onPrimaryContainer = Color(0xFFF1F1F3),
            secondary = Color(0xFF24262C),
            onSecondary = Color(0xFFF1F1F3),
            secondaryContainer = Color(0xFF2A2C32),
            onSecondaryContainer = Color(0xFFF1F1F3),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            disabledContainer = Color(0xFF393B42),
            onDisabled = Color(0xFF8B8D95),
        )

        val miuixColors = appColors.toSkipiMiuixColors()

        assertEquals(appColors.accent, miuixColors.primary)
        assertEquals(appColors.onAccent, miuixColors.onPrimary)
        assertEquals(appColors.primaryContainer, miuixColors.primaryContainer)
        assertEquals(appColors.onPrimaryContainer, miuixColors.onPrimaryContainer)
        assertEquals(appColors.secondary, miuixColors.secondary)
        assertEquals(appColors.onSecondary, miuixColors.onSecondary)
        assertEquals(appColors.secondaryContainer, miuixColors.secondaryContainer)
        assertEquals(appColors.onSecondaryContainer, miuixColors.onSecondaryContainer)
        assertEquals(appColors.error, miuixColors.error)
        assertEquals(appColors.onError, miuixColors.onError)
        assertEquals(appColors.errorContainer, miuixColors.errorContainer)
        assertEquals(appColors.onErrorContainer, miuixColors.onErrorContainer)
        assertEquals(appColors.disabledContainer, miuixColors.disabledPrimary)
        assertEquals(appColors.onDisabled, miuixColors.disabledOnPrimary)
        assertEquals(appColors.disabledContainer, miuixColors.disabledPrimaryButton)
        assertEquals(appColors.onDisabled, miuixColors.disabledOnPrimaryButton)
        assertEquals(appColors.onDisabled, miuixColors.disabledOnSurface)
    }

    @Test
    fun lightAppPaletteMapsMiuixSecondaryContainerAndTextRoles() {
        val appColors = AppColors(
            background = Color(0xFFF5F6FA),
            onBackground = Color(0xFF1A1B20),
            accent = Color(0xFF345D9D),
            onAccent = Color.White,
            surface = Color.White,
            onSurface = Color(0xFF1A1B20),
            surfaceVariant = Color(0xFFE7EAF1),
            onSurfaceVariant = Color(0xFF5E6471),
            isDark = false,
        )

        val miuixColors = appColors.toSkipiMiuixColors()

        assertEquals(appColors.surfaceVariant, miuixColors.secondaryContainer)
        assertEquals(appColors.onSurface, miuixColors.onSecondaryContainer)
        assertEquals(appColors.accent, miuixColors.primary)
        assertEquals(appColors.onAccent, miuixColors.onPrimary)
    }

    @Test
    fun namedPaletteColorsReachMiuixSubscriptionFieldRoles() {
        val palette = namedThemePaletteFor("aurora")!!
        val appColors = AppColors(
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

        val miuixColors = appColors.toSkipiMiuixColors()

        assertEquals(palette.surfaceVariant, miuixColors.secondaryContainer)
        assertEquals(palette.onSurface, miuixColors.onSecondaryContainer)
        assertEquals(palette.accent, miuixColors.primary)
        assertEquals(palette.onAccent, miuixColors.onPrimary)
    }

}
