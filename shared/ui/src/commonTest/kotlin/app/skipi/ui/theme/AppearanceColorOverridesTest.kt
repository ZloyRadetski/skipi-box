// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.ThemeMode
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceColorOverridesTest {
    @Test
    fun enabledCustomColorsOverrideAndroidPaletteRoles() {
        val base = AppColors(
            background = Color(0xFF101112),
            onBackground = Color(0xFF202122),
            accent = Color(0xFF303132),
            onAccent = Color(0xFF404142),
            surface = Color(0xFF505152),
            onSurface = Color(0xFF606162),
            surfaceVariant = Color(0xFF707172),
            onSurfaceVariant = Color(0xFF808182),
            isDark = true,
        )
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

        val actual = applyAppearanceSettings(base, appearance)

        assertEquals(Color(0xFF111213), actual.accent)
        assertEquals(Color(0xFF212223), actual.background)
        assertEquals(Color(0xFF313233), actual.surface)
        assertEquals(Color(0xFF414243), actual.surfaceVariant)
        assertEquals(Color(0xFF515253), actual.onBackground)
        assertEquals(Color(0xFF515253), actual.onSurface)
        assertEquals(Color(0xFF515253), actual.onAccent)
        assertEquals(Color(0xFF616263), actual.onSurfaceVariant)
    }

    @Test
    fun disabledCustomPaletteOnlyKeepsAndroidAccentFallback() {
        val base = AppColors(
            background = Color(0xFF101112),
            onBackground = Color(0xFF202122),
            accent = Color(0xFF303132),
            onAccent = Color(0xFF404142),
            surface = Color(0xFF505152),
            onSurface = Color(0xFF606162),
            surfaceVariant = Color(0xFF707172),
            onSurfaceVariant = Color(0xFF808182),
            isDark = true,
        )
        val actual = applyAppearanceSettings(
            base,
            AppearanceSettings(dynamicColors = false, customAccentColor = 0xFF919293L),
        )

        assertEquals(Color(0xFF919293), actual.accent)
        assertEquals(base.background, actual.background)
        assertEquals(base.surface, actual.surface)
        assertEquals(base.surfaceVariant, actual.surfaceVariant)
        assertEquals(base.onBackground, actual.onBackground)
        assertEquals(base.onSurface, actual.onSurface)
        assertEquals(base.onAccent, actual.onAccent)
        assertEquals(base.onSurfaceVariant, actual.onSurfaceVariant)
    }

    @Test
    fun namedThemeAccentWinsOverDisabledCustomAccent() {
        val base = AppColors(
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

        val actual = applyAppearanceSettings(
            base,
            AppearanceSettings(
                themeMode = ThemeMode.Named,
                themeVariant = "aurora",
                dynamicColors = false,
                customColorsEnabled = false,
                customAccentColor = 0xFF919293L,
            ),
        )

        assertEquals(base, actual)
    }

    @Test
    fun dynamicColorsPreventDisabledCustomAccentFallback() {
        val base = AppColors(
            background = Color(0xFF101112),
            onBackground = Color(0xFF202122),
            accent = Color(0xFF303132),
            onAccent = Color(0xFF404142),
            surface = Color(0xFF505152),
            onSurface = Color(0xFF606162),
            surfaceVariant = Color(0xFF707172),
            onSurfaceVariant = Color(0xFF808182),
            isDark = true,
        )

        val actual = applyAppearanceSettings(
            base,
            AppearanceSettings(
                dynamicColors = true,
                customColorsEnabled = false,
                customAccentColor = 0xFF919293L,
            ),
        )

        assertEquals(base, actual)
    }

    @Test
    fun enabledCustomColorsOverrideOnlyConfiguredRoles() {
        val base = AppColors(
            background = Color(0xFF101112),
            onBackground = Color(0xFF202122),
            accent = Color(0xFF303132),
            onAccent = Color(0xFF404142),
            surface = Color(0xFF505152),
            onSurface = Color(0xFF606162),
            surfaceVariant = Color(0xFF707172),
            onSurfaceVariant = Color(0xFF808182),
            isDark = true,
        )

        val actual = applyAppearanceSettings(
            base,
            AppearanceSettings(
                dynamicColors = true,
                customColorsEnabled = true,
                customBackgroundColor = 0xFF212223L,
                customSurfaceVariantColor = null,
                customTextColor = 0xFF515253L,
                customTextSecondaryColor = null,
            ),
        )

        assertEquals(base.accent, actual.accent)
        assertEquals(Color(0xFF212223), actual.background)
        assertEquals(base.surface, actual.surface)
        assertEquals(base.surfaceVariant, actual.surfaceVariant)
        assertEquals(Color(0xFF515253), actual.onBackground)
        assertEquals(Color(0xFF515253), actual.onSurface)
        assertEquals(Color(0xFF515253), actual.onAccent)
        assertEquals(base.onSurfaceVariant, actual.onSurfaceVariant)
        assertEquals(base.isDark, actual.isDark)
    }
}
