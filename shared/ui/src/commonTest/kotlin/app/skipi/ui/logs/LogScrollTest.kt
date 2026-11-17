// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogScrollTest {
    @Test
    fun theFirstLinesOfALogOpenAtItsBottom() {
        assertEquals(41, logScrollTarget(previousNewestId = null, newestId = 90, count = 42, following = false))
        assertEquals(0, logScrollTarget(previousNewestId = null, newestId = 1, count = 1, following = false))
    }

    @Test
    fun anEmptyLogHasNowhereToScroll() {
        assertNull(logScrollTarget(previousNewestId = null, newestId = null, count = 0, following = true))
        assertNull(logScrollTarget(previousNewestId = 5, newestId = null, count = 0, following = true))
    }

    @Test
    fun aReaderAtTheBottomFollowsNewLines() {
        assertEquals(10, logScrollTarget(previousNewestId = 8, newestId = 11, count = 11, following = true))
    }

    @Test
    fun aFullLogThatDropsItsOldestLinesStillFollows() {
        // The log keeps a fixed number of lines: new ones come in, the count stays.
        assertEquals(7999, logScrollTarget(previousNewestId = 9000, newestId = 9003, count = 8000, following = true))
    }

    @Test
    fun aReaderWhoScrolledUpIsLeftWhereTheyAre() {
        assertNull(logScrollTarget(previousNewestId = 8, newestId = 11, count = 11, following = false))
    }

    @Test
    fun noNewLinesNoScroll() {
        assertNull(logScrollTarget(previousNewestId = 8, newestId = 8, count = 8, following = true))
    }

    @Test
    fun aSearchChangeShowsItsResultsFromTheBottom() {
        // A different list is a first look at it, whatever the reader did before.
        assertEquals(2, logScrollTarget(previousNewestId = 8, newestId = 4, count = 3, following = false, listChanged = true))
    }
}
