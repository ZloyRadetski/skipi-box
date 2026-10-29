// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationBarSizeTest {
    @Test
    fun storedBottomBarSettingMapsLikeAndroid() {
        assertEquals(SkipiNavigationBarSize.Small, navigationBarSizeForSetting(0))
        assertEquals(SkipiNavigationBarSize.Medium, navigationBarSizeForSetting(1))
        assertEquals(SkipiNavigationBarSize.Large, navigationBarSizeForSetting(2))
    }

    @Test
    fun unknownSettingFallsBackToLarge() {
        assertEquals(SkipiNavigationBarSize.Large, navigationBarSizeForSetting(7))
        assertEquals(SkipiNavigationBarSize.Large, navigationBarSizeForSetting(-1))
    }
}
