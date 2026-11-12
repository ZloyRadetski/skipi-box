// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class SkipiMenuPositionTest {
    private val window = IntSize(1000, 800)

    // The popup is its surface plus a margin on each side that holds the shadow, so 200x100 surface = 216x116.
    private val popup = IntSize(216, 116)
    private val pad = 8

    @Test
    fun opensBelowTheAnchorAlignedToItsStart() {
        val anchor = IntRect(100, 50, 300, 90)

        assertEquals(
            IntOffset(100 - pad, 90 - pad),
            skipiMenuPosition(anchor, window, popup, rtl = false, shadowPad = pad),
        )
    }

    @Test
    fun opensAboveWhenThereIsNoRoomBelow() {
        val anchor = IntRect(100, 700, 300, 740)

        assertEquals(
            IntOffset(100 - pad, 700 - 100 - pad),
            skipiMenuPosition(anchor, window, popup, rtl = false, shadowPad = pad),
        )
    }

    @Test
    fun alignsToTheAnchorsEndWhenItWouldLeaveTheWindow() {
        val anchor = IntRect(900, 50, 980, 90)

        assertEquals(
            IntOffset(980 - 200 - pad, 90 - pad),
            skipiMenuPosition(anchor, window, popup, rtl = false, shadowPad = pad),
        )
    }

    @Test
    fun staysInsideTheWindowWhenNeitherSideFits() {
        val tall = IntSize(216, 900)
        val anchor = IntRect(100, 300, 300, 340)

        assertEquals(
            IntOffset(100 - pad, 0 - pad),
            skipiMenuPosition(anchor, window, tall, rtl = false, shadowPad = pad),
        )
    }

    @Test
    fun rightToLeftAlignsToTheAnchorsEnd() {
        val anchor = IntRect(500, 50, 700, 90)

        assertEquals(
            IntOffset(700 - 200 - pad, 90 - pad),
            skipiMenuPosition(anchor, window, popup, rtl = true, shadowPad = pad),
        )
    }
}
