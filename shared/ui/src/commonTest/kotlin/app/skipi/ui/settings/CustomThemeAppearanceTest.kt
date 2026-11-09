// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.ui.graphics.Color
import app.skipi.ui.theme.AppColors
import features.config.CustomThemeShare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomThemeAppearanceTest {
    private val theme = CustomThemeShare(
        name = "Night harbour",
        dark = true,
        accent = 0xFF3B82F6,
        background = 0xFF081427,
        surface = 0xFF102443,
        surfaceVariant = 0xFF18345E,
        text = 0xFFE7F1FF,
        textSecondary = 0xFFB2C8EA,
    )

    @Test
    fun applyingAThemeTurnsCustomColorsOnAndFillsTheSixColours() {
        val applied = AppearanceSettingsState().withCustomTheme(theme)

        assertTrue(applied.enableCustomColors)
        assertEquals(theme.accent, applied.customAccentColor)
        assertEquals(theme.background, applied.customBackgroundColor)
        assertEquals(theme.surface, applied.customSurfaceColor)
        assertEquals(theme.surfaceVariant, applied.customSurfaceVariantColor)
        assertEquals(theme.text, applied.customTextColor)
        assertEquals(theme.textSecondary, applied.customTextSecondaryColor)
    }

    @Test
    fun theThemesBaseDecidesBetweenTheLightAndTheDarkColourMode() {
        assertEquals(
            AppearanceSettingValues.ColorModeDark,
            AppearanceSettingsState(colorMode = AppearanceSettingValues.ColorModeLight).withCustomTheme(theme).colorMode,
        )
        assertEquals(
            AppearanceSettingValues.ColorModeLight,
            AppearanceSettingsState(colorMode = AppearanceSettingValues.ColorModeDark).withCustomTheme(theme.copy(dark = false)).colorMode,
        )
    }

    @Test
    fun applyingAThemeKeepsTheStatusAndProtocolColoursAndTheLibrary() {
        val before = AppearanceSettingsState(
            customStatusRunningColor = 0xFF00FF00,
            customProtocolVlessColor = 0xFF123456,
            customThemes = "saved",
        )

        val after = before.withCustomTheme(theme)

        assertEquals(0xFF00FF00, after.customStatusRunningColor)
        assertEquals(0xFF123456, after.customProtocolVlessColor)
        assertEquals("saved", after.customThemes)
    }

    @Test
    fun aThemeAppliedIsRecognisedAsTheActiveOne() {
        val applied = AppearanceSettingsState().withCustomTheme(theme)

        assertTrue(applied.isShowing(theme))
        assertTrue(applied.isShowing(theme.copy(name = "Other name")))
        assertFalse(applied.isShowing(theme.copy(accent = 0xFF000001)))
        assertFalse(applied.isShowing(theme.copy(dark = false)))
    }

    @Test
    fun withCustomColorsOffNoThemeIsActive() {
        val applied = AppearanceSettingsState().withCustomTheme(theme).copy(enableCustomColors = false)

        assertFalse(applied.isShowing(theme))
    }

    @Test
    fun theColoursOnScreenBecomeAThemeWithTheTextAndSecondaryTextRoles() {
        val colors = AppColors(
            background = Color(0xFF081427),
            onBackground = Color(0xFFE7F1FF),
            accent = Color(0xFF3B82F6),
            onAccent = Color.White,
            surface = Color(0xFF102443),
            onSurface = Color(0xFFE7F1FF),
            surfaceVariant = Color(0xFF18345E),
            onSurfaceVariant = Color(0xFFB2C8EA),
            isDark = true,
        )

        assertEquals(theme, colors.toCustomTheme(name = "Night harbour"))
    }

    @Test
    fun theLibraryIsReadFromAndWrittenToTheState() {
        val state = AppearanceSettingsState().withCustomThemes(listOf(theme))

        assertEquals(listOf(theme), state.savedThemes())
    }
}
