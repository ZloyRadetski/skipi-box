// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionDurationFormatTest {
    @Test
    fun zeroPadsEveryField() {
        assertEquals("00:00:00", formatSessionDuration(0))
        assertEquals("00:00:09", formatSessionDuration(9))
        assertEquals("00:01:05", formatSessionDuration(65))
        assertEquals("01:01:01", formatSessionDuration(3661))
    }

    @Test
    fun hoursGrowPastTwoDigits() {
        assertEquals("100:00:00", formatSessionDuration(360_000))
    }
}

class SessionDurationSecondsTest {
    private val time = kotlin.time.TestTimeSource()

    @Test
    fun theConnectionsOwnStartIsUsedWhenTheHostProvidesIt() {
        val connectedSince = time.markNow()
        time += kotlin.time.Duration.parse("90s")
        // The screen was opened just now: its own fallback mark must not matter.
        val screenOpened = time.markNow()

        assertEquals(90L, sessionDurationSeconds(connectedSince, screenOpened))
    }

    @Test
    fun withoutAHostStartTheScreenCountsFromWhenItAppeared() {
        val screenOpened = time.markNow()
        time += kotlin.time.Duration.parse("7s")

        assertEquals(7L, sessionDurationSeconds(null, screenOpened))
    }
}
