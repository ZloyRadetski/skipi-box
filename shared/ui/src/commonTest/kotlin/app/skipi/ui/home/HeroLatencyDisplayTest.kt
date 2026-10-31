// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * While a latency test runs, the connection panel shows nothing new: a long "testing" line there
 * pushed the whole panel around for the quarter of a second before the first answer.
 */
class HeroLatencyDisplayTest {
    @Test
    fun aRunningTestShowsNoPlaceholder() {
        assertEquals(HeroLatencyDisplay.Hidden, heroLatencyDisplay(latencyMs = null, testing = true, failed = false))
        assertEquals(HeroLatencyDisplay.Hidden, heroLatencyDisplay(latencyMs = null, testing = true, failed = true))
    }

    @Test
    fun theLastKnownValueStaysUntilTheNewOneArrives() {
        assertEquals(HeroLatencyDisplay.Value, heroLatencyDisplay(latencyMs = 87L, testing = true, failed = false))
    }

    @Test
    fun anAnswerOrAFailureIsShownWhenNoTestRuns() {
        assertEquals(HeroLatencyDisplay.Value, heroLatencyDisplay(latencyMs = 87L, testing = false, failed = false))
        assertEquals(HeroLatencyDisplay.Failure, heroLatencyDisplay(latencyMs = null, testing = false, failed = true))
        assertEquals(HeroLatencyDisplay.Hidden, heroLatencyDisplay(latencyMs = null, testing = false, failed = false))
    }
}
