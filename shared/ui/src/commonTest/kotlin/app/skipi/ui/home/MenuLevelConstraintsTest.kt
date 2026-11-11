// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuLevelConstraintsTest {
    @Test
    fun aLevelIsAlwaysMeasuredAtItsOwnNaturalWidthWhateverTheMenuIsPinnedTo() {
        val natural = 540
        // The menu surface glides from one level's width to the next; the level must not follow it.
        for (pinned in listOf(220, 300, 420, 540, 760)) {
            val measured = menuLevelConstraints(Constraints(minWidth = pinned, maxWidth = pinned, maxHeight = 900), natural)

            assertEquals(natural, measured.minWidth, "pinned $pinned")
            assertEquals(natural, measured.maxWidth, "pinned $pinned")
        }
    }

    @Test
    fun theHeightStaysUnderTheMenusControl() {
        val measured = menuLevelConstraints(Constraints(minWidth = 0, maxWidth = 300, minHeight = 10, maxHeight = 900), 400)

        assertEquals(10, measured.minHeight)
        assertEquals(900, measured.maxHeight)
    }

    @Test
    fun aLevelWithNoKnownWidthKeepsTheIncomingConstraints() {
        val incoming = Constraints(minWidth = 0, maxWidth = 300, maxHeight = 900)

        assertEquals(incoming, menuLevelConstraints(incoming, naturalWidth = 0))
    }
}
