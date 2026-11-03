// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeButtonSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassicButtonSizeTest {
    @Test
    fun largeIsTheFullBaseSizeAndMediumIsEightyPercentOfIt() {
        assertEquals(248.dp, skipiClassicButtonOuterSize(ProxyHomeButtonSize.Large, compact = true))
        assertEquals(280.dp, skipiClassicButtonOuterSize(ProxyHomeButtonSize.Large, compact = false))
        assertEquals(248.dp * 0.8f, skipiClassicButtonOuterSize(ProxyHomeButtonSize.Medium, compact = true))
    }

    @Test
    fun theRingStaysCloseToTheButton() {
        // The ring is 10 percent wider than the button, not 20.
        assertEquals(1.1f, SkipiClassicButtonRingRatio)
    }

    @Test
    fun everySizeIsStrictlyLargerThanThePreviousOne() {
        for (compact in listOf(true, false)) {
            val sizes = ProxyHomeButtonSize.entries.map { size -> skipiClassicButtonOuterSize(size, compact) }
            assertTrue(sizes.zipWithNext().all { (smaller, larger) -> smaller < larger }, "sizes $sizes")
        }
    }

    @Test
    fun theGiantButtonStillFitsAPhoneScreenWithItsSidePadding() {
        // 360 dp is a small phone; the screen keeps 16 dp on each side.
        assertTrue(skipiClassicButtonOuterSize(ProxyHomeButtonSize.Giant, compact = true) <= 328.dp)
    }
}
