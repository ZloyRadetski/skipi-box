// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PickingAThemeTest {
    @Test
    fun pickingAColorModeSwitchesCustomColorsOffAndKeepsThePickedColors() {
        val before = AppearanceSettingsState(
            colorMode = AppearanceSettingValues.ColorModeLight,
            enableCustomColors = true,
            customBackgroundColor = 0xFF010203L,
            customCategoryIconGlyphColor = 0xFF0A0B0CL,
        )

        val after = before.withColorMode(AppearanceSettingValues.ColorModeAmoled)

        assertEquals(AppearanceSettingValues.ColorModeAmoled, after.colorMode)
        assertFalse(after.enableCustomColors)
        assertEquals(0xFF010203L, after.customBackgroundColor)
        assertEquals(0xFF0A0B0CL, after.customCategoryIconGlyphColor)
    }

    @Test
    fun pickingAColorModeLeavesEverythingElseAlone() {
        val before = AppearanceSettingsState(enableCustomColors = false, fontFamilyMode = 3)

        assertEquals(before.copy(colorMode = AppearanceSettingValues.ColorModeDark), before.withColorMode(AppearanceSettingValues.ColorModeDark))
    }
}
