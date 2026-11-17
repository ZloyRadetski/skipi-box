// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeChoiceTest {
    @Test
    fun everyColorModeMapsToItsThemeChoice() {
        assertEquals(ThemeChoice(ThemeMode.System, amoledBlack = false, themeVariant = null), themeChoiceOfColorMode(app.modes.ColorModeSystem))
        assertEquals(ThemeChoice(ThemeMode.Light, false, null), themeChoiceOfColorMode(app.modes.ColorModeLight))
        assertEquals(ThemeChoice(ThemeMode.Dark, false, null), themeChoiceOfColorMode(app.modes.ColorModeDark))
        assertEquals(ThemeChoice(ThemeMode.Dark, amoledBlack = true, themeVariant = null), themeChoiceOfColorMode(app.modes.ColorModeAmoled))
        assertEquals(ThemeChoice(ThemeMode.Named, false, "aurora"), themeChoiceOfColorMode(app.modes.ColorModeAurora))
        assertEquals(ThemeChoice(ThemeMode.Named, false, "sakura"), themeChoiceOfColorMode(app.modes.ColorModeSakura))
        assertEquals(ThemeChoice(ThemeMode.Named, false, "forest"), themeChoiceOfColorMode(app.modes.ColorModeForest))
        assertEquals(ThemeChoice(ThemeMode.Named, false, "sunset"), themeChoiceOfColorMode(app.modes.ColorModeSunset))
    }

    @Test
    fun aThemeChoiceMapsBackToItsColorMode() {
        for (mode in listOf(
            app.modes.ColorModeSystem, app.modes.ColorModeLight, app.modes.ColorModeDark, app.modes.ColorModeAmoled,
            app.modes.ColorModeAurora, app.modes.ColorModeSakura, app.modes.ColorModeForest, app.modes.ColorModeSunset,
        )) {
            val choice = themeChoiceOfColorMode(mode)
            val appearance = AppearanceSettings(themeMode = choice.themeMode, amoledBlack = choice.amoledBlack, themeVariant = choice.themeVariant)
            assertEquals(mode, colorModeOfAppearance(appearance, fallback = -1), "color mode $mode")
        }
    }

    @Test
    fun aNamedThemeWithoutAKnownVariantKeepsTheFallback() {
        assertEquals(
            app.modes.ColorModeSakura,
            colorModeOfAppearance(AppearanceSettings(themeMode = ThemeMode.Named, themeVariant = null), fallback = app.modes.ColorModeSakura),
        )
        assertEquals(
            app.modes.ColorModeForest,
            colorModeOfAppearance(AppearanceSettings(themeMode = ThemeMode.Named, themeVariant = "unknown"), fallback = app.modes.ColorModeForest),
        )
    }

    @Test
    fun amoledWinsOverTheDarkMode() {
        assertEquals(app.modes.ColorModeAmoled, colorModeOfAppearance(AppearanceSettings(themeMode = ThemeMode.Dark, amoledBlack = true), fallback = 0))
    }
}
