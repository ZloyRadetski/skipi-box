// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.anim

import androidx.compose.animation.core.LinearEasing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorativeClockTest {
    @Test
    fun aPulseRisesFromZeroToOneInOneDurationAndFallsBackInTheNext() {
        assertEquals(0f, pulseFraction(0L, 2400), 0.0001f)
        assertEquals(1f, pulseFraction(2400L, 2400), 0.0001f)
        assertEquals(0f, pulseFraction(4800L, 2400), 0.0001f)
        assertEquals(1f, pulseFraction(7200L, 2400), 0.0001f)
    }

    @Test
    fun aPulseStaysInsideZeroAndOneAndMovesTowardsItsPeakInTheFirstHalf() {
        var previous = -1f
        for (elapsed in 0L..2400L step 100L) {
            val value = pulseFraction(elapsed, 2400)
            assertTrue(value in 0f..1f, "value $value at $elapsed")
            assertTrue(value >= previous, "not rising at $elapsed")
            previous = value
        }
    }

    @Test
    fun aWaitingWaveStaysAtZeroUntilItsDelayHasPassedThenRestartsEveryPeriod() {
        // 2400 ms of travel after a 1200 ms delay, repeated: period 3600 ms.
        assertEquals(0f, restartProgress(0L, 2400, 1200, LinearEasing), 0.0001f)
        assertEquals(0f, restartProgress(1199L, 2400, 1200, LinearEasing), 0.0001f)
        assertEquals(0.5f, restartProgress(1200L + 1200L, 2400, 1200, LinearEasing), 0.0001f)
        assertEquals(0f, restartProgress(3600L, 2400, 1200, LinearEasing), 0.0001f)
        assertEquals(0.5f, restartProgress(3600L + 2400L, 2400, 1200, LinearEasing), 0.0001f)
    }

    @Test
    fun aWaveWithoutDelayRestartsEveryDuration() {
        assertEquals(0.25f, restartProgress(600L, 2400, 0, LinearEasing), 0.0001f)
        assertEquals(0.25f, restartProgress(600L + 2400L, 2400, 0, LinearEasing), 0.0001f)
    }

    @Test
    fun theDecorativeClockTicksAtMostTwentyFiveTimesASecond() {
        assertTrue(DecorativeFrameMillis >= 40L, "frame step $DecorativeFrameMillis ms")
    }
}
