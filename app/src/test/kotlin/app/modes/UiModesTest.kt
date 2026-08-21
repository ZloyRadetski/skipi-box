// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.modes

import org.junit.Assert.assertEquals
import org.junit.Test

class UiModesTest {

    @Test
    fun testNormalizeBottomBarSize() {
        assertEquals(BottomBarSizeSmall, normalizeBottomBarSize(BottomBarSizeSmall))
        assertEquals(BottomBarSizeMedium, normalizeBottomBarSize(BottomBarSizeMedium))
        assertEquals(BottomBarSizeLarge, normalizeBottomBarSize(BottomBarSizeLarge))

        // Invalid values default to BottomBarSizeLarge
        assertEquals(BottomBarSizeLarge, normalizeBottomBarSize(-1))
        assertEquals(BottomBarSizeLarge, normalizeBottomBarSize(99))
    }

    @Test
    fun testNormalizeBackgroundStyle() {
        assertEquals(BackgroundStyleClassic, normalizeBackgroundStyle(BackgroundStyleClassic))
        assertEquals(BackgroundStylePhoto, normalizeBackgroundStyle(BackgroundStylePhoto))
        assertEquals(BackgroundStyleConnection, normalizeBackgroundStyle(BackgroundStyleConnection))

        // Invalid values default to BackgroundStyleClassic
        assertEquals(BackgroundStyleClassic, normalizeBackgroundStyle(-1))
        assertEquals(BackgroundStyleClassic, normalizeBackgroundStyle(99))
    }
}
