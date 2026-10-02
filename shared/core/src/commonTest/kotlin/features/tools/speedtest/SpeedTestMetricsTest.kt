// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeedTestMetricsTest {
    @Test
    fun computesJitterAndMedianWithoutChangingExistingSemantics() {
        assertEquals(0.0, SpeedTestMetrics.jitter(emptyList()), 1e-9)
        assertEquals(3.0, SpeedTestMetrics.jitter(listOf(10.0, 14.0, 12.0)), 1e-9)
        assertEquals(2.5, SpeedTestMetrics.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(SpeedTestMetrics.median(emptyList()))
    }

    @Test
    fun smoothsThroughputInMegabitsAndHandlesZeroDuration() {
        assertEquals(8.0, SpeedTestMetrics.smoothedMbps(null, 1_000_000, 1000), 1e-9)
        assertEquals(5.0, SpeedTestMetrics.smoothedMbps(5.0, 100, 0), 1e-9)
        assertEquals(0.0, SpeedTestMetrics.smoothedMbps(null, 100, 0), 1e-9)
    }
}
