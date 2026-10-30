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

class NavigationIndicatorGeometryTest {
    private fun geometry(raw: Float, velocity: Float = 0f) = navigationIndicatorGeometry(
        rawOffsetPx = raw,
        maxOffsetPx = 600f,
        velocityPxPerSecond = velocity,
        indicatorWidthPx = 280f,
        edgePaddingPx = 9f,
    )

    @Test
    fun theIndicatorNeverLeavesTheCapsuleWhateverTheFingerDoes() {
        assertEquals(0f, geometry(-500f).offsetPx)
        assertEquals(600f, geometry(5_000f).offsetPx)
        assertEquals(250f, geometry(250f).offsetPx)
    }

    @Test
    fun stretchNeverGrowsPastThePaddingAroundTheIndicator() {
        val limit = 1f + 2f * 9f / 280f
        assertEquals(1f, geometry(300f, velocity = 0f).stretchX)
        val fast = geometry(0f, velocity = 50_000f).stretchX
        assertEquals(true, fast <= limit + 1e-6f)
        assertEquals(true, fast >= 1f)
    }
}
