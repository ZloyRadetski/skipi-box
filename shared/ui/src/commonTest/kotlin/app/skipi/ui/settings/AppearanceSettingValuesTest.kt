// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceSettingValuesTest {
    @Test
    fun legacyAndCurrentAppearanceValuesStayCompatible() {
        assertEquals(AppearanceSettingValues.ColorModeSystem, AppearanceSettingValues.normalizeColorMode(3))
        assertEquals(AppearanceSettingValues.ColorModeLight, AppearanceSettingValues.normalizeColorMode(4))
        assertEquals(AppearanceSettingValues.ColorModeDark, AppearanceSettingValues.normalizeColorMode(5))
        assertEquals(AppearanceSettingValues.ColorModeAurora, AppearanceSettingValues.normalizeColorMode(7))
        assertTrue(AppearanceSettingValues.isNamedColorTheme(AppearanceSettingValues.ColorModeForest))
        assertFalse(AppearanceSettingValues.isNamedColorTheme(AppearanceSettingValues.ColorModeDark))
    }

    @Test
    fun fontSizeKeepsLegacyIndicesAndValidSliderPercentages() {
        assertEquals(55, AppearanceSettingValues.normalizeFontSizeMode(0))
        assertEquals(85, AppearanceSettingValues.normalizeFontSizeMode(3))
        assertEquals(100, AppearanceSettingValues.normalizeFontSizeMode(4))
        assertEquals(145, AppearanceSettingValues.normalizeFontSizeMode(7))
        assertEquals(120, AppearanceSettingValues.normalizeFontSizeMode(120))
        assertEquals(100, AppearanceSettingValues.normalizeFontSizeMode(121))
    }

    @Test
    fun photoAndCustomSeedKeepTheirPersistedSlots() {
        assertEquals(1, AppearanceSettingValues.normalizeBackgroundStyle(1))
        assertEquals(AppearanceSettingValues.BackgroundStyleClassic, AppearanceSettingValues.normalizeBackgroundStyle(99))
        assertEquals(8, AppearanceSettingValues.CustomMaterialYouSeedIndex)
    }
}
