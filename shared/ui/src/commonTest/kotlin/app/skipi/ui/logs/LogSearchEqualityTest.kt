// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LogSearchEqualityTest {
    @Test
    fun parsingTheSameQueryTwiceGivesAnEqualSearch() {
        assertEquals(LogSearch.parse("youtube -ads"), LogSearch.parse("youtube -ads"))
        assertEquals(LogSearch.parse("youtube -ads").hashCode(), LogSearch.parse("youtube -ads").hashCode())
    }

    @Test
    fun differentQueriesAreNotEqual() {
        assertNotEquals(LogSearch.parse("youtube"), LogSearch.parse("youtube -ads"))
    }
}
