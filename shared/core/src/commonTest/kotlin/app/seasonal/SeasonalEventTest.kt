// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.seasonal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeasonalEventTest {
    @Test
    fun newYearStartsOnDecemberFirst() {
        assertEquals(SeasonalEvent.NewYear, activeSeasonalEvent(month = 12, dayOfMonth = 1, enabled = true))
        assertNull(activeSeasonalEvent(month = 11, dayOfMonth = 30, enabled = true))
    }

    @Test
    fun newYearEndsOnJanuaryFourteenth() {
        assertEquals(SeasonalEvent.NewYear, activeSeasonalEvent(month = 1, dayOfMonth = 14, enabled = true))
        assertNull(activeSeasonalEvent(month = 1, dayOfMonth = 15, enabled = true))
    }

    @Test
    fun newYearCoversTheYearTurn() {
        assertEquals(SeasonalEvent.NewYear, activeSeasonalEvent(month = 12, dayOfMonth = 31, enabled = true))
        assertEquals(SeasonalEvent.NewYear, activeSeasonalEvent(month = 1, dayOfMonth = 1, enabled = true))
    }

    @Test
    fun noEventOutsideTheSeason() {
        for (month in 2..11) {
            assertNull(activeSeasonalEvent(month = month, dayOfMonth = 15, enabled = true), "month $month")
        }
    }

    @Test
    fun disabledSettingSuppressesEveryEvent() {
        assertNull(activeSeasonalEvent(month = 12, dayOfMonth = 25, enabled = false))
        assertNull(activeSeasonalEvent(month = 1, dayOfMonth = 3, enabled = false))
    }
}
