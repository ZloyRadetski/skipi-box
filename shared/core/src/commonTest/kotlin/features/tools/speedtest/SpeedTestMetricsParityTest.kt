// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SpeedTestMetricsParityTest {

    @Test
    fun jitter_is_zero_for_fewer_than_two_samples() {
        assertEquals(0.0, SpeedTestMetrics.jitter(emptyList()), 1e-9)
        assertEquals(0.0, SpeedTestMetrics.jitter(listOf(10.0)), 1e-9)
    }

    @Test
    fun jitter_is_mean_absolute_consecutive_difference() {
        val jitter = SpeedTestMetrics.jitter(listOf(10.0, 14.0, 12.0))
        assertEquals((4.0 + 2.0) / 2, jitter, 1e-9)
    }

    @Test
    fun median_handles_odd_and_even_sizes() {
        assertEquals(3.0, SpeedTestMetrics.median(listOf(5.0, 1.0, 3.0))!!, 1e-9)
        assertEquals(2.5, SpeedTestMetrics.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(SpeedTestMetrics.median(emptyList()))
    }

    @Test
    fun smoothedMbps_first_sample_becomes_the_ema() {
        // 1 MB in exactly one second = 8 Mbps.
        val ema = SpeedTestMetrics.smoothedMbps(null, sampleBytes = 1_000_000, sampleMillis = 1000)
        assertEquals(8.0, ema, 1e-6)
    }

    @Test
    fun smoothedMbps_blends_new_sample_with_alpha() {
        val first = SpeedTestMetrics.smoothedMbps(null, sampleBytes = 1_000_000, sampleMillis = 1000)
        val second = SpeedTestMetrics.smoothedMbps(first, sampleBytes = 500_000, sampleMillis = 1000)
        // EMA moves 25% of the way from 8 towards 4.
        assertEquals(7.0, second, 1e-6)
    }

    @Test
    fun smoothedMbps_zero_duration_keeps_previous_value() {
        assertEquals(5.0, SpeedTestMetrics.smoothedMbps(5.0, sampleBytes = 100, sampleMillis = 0), 1e-9)
        assertEquals(0.0, SpeedTestMetrics.smoothedMbps(null, sampleBytes = 100, sampleMillis = 0), 1e-9)
    }
}
