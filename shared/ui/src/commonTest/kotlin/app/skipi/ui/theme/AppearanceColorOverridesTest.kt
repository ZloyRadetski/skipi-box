// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import app.skipi.app.model.AppearanceSettings
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
            AppearanceSettings(customAccentColor = 0xFF919293L),
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
}
