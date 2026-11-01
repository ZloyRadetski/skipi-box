// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop.home

import app.skipi.desktop.DesktopProxyHomeTopContentPadding
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared home screen defaults to a 40dp top gap that keeps the content clear of the phone's status
 * bar. A desktop window has no status bar, so its proxy screen starts at the very top (as on Android,
 * where the system bar inset is applied elsewhere and the screen is given 0dp).
 */
class DesktopProxyHomeLayoutTest {
    @Test
    fun theDesktopProxyHomeHasNoStatusBarGapAtTheTop() {
        assertEquals(0.dp, DesktopProxyHomeTopContentPadding)
    }
}
