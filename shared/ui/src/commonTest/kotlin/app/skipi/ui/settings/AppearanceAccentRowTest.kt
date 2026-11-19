// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceAccentRowTest {
    private val named = AppearanceSettingValues.ColorModeAurora
    private val plain = AppearanceSettingValues.ColorModeDark

    @Test
    fun aPlainThemeOffersTheAccentUnlessDynamicColorsAreOn() {
        assertTrue(AppearanceSettingsState(colorMode = plain, enableMaterialYou = false).showsAccentPicker())
        assertFalse(AppearanceSettingsState(colorMode = plain, enableMaterialYou = true).showsAccentPicker())
    }

    @Test
    fun aNamedThemeOffersTheAccentOnlyWithCustomColorsOn() {
        // Its own accent shows otherwise, so a row there would change nothing.
        assertFalse(AppearanceSettingsState(colorMode = named, enableMaterialYou = false).showsAccentPicker())
        assertTrue(AppearanceSettingsState(colorMode = named, enableCustomColors = true, enableMaterialYou = false).showsAccentPicker())
        // Dynamic colors do nothing on a named theme, so a switch left on from another theme does not hide the row.
        assertTrue(AppearanceSettingsState(colorMode = named, enableCustomColors = true, enableMaterialYou = true).showsAccentPicker())
    }
}
