// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTestMathTest {

    @Test
    fun jitter_is_zero_for_fewer_than_two_samples() {
        assertEquals(0.0, SpeedTestMath.jitter(emptyList()), 1e-9)
        assertEquals(0.0, SpeedTestMath.jitter(listOf(10.0)), 1e-9)
    }

    @Test
    fun jitter_is_mean_absolute_consecutive_difference() {
        val jitter = SpeedTestMath.jitter(listOf(10.0, 14.0, 12.0))
        assertEquals((4.0 + 2.0) / 2, jitter, 1e-9)
    }

    @Test
    fun median_handles_odd_and_even_sizes() {
        assertEquals(3.0, SpeedTestMath.median(listOf(5.0, 1.0, 3.0))!!, 1e-9)
        assertEquals(2.5, SpeedTestMath.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(SpeedTestMath.median(emptyList()))
    }

    @Test
    fun smoothedMbps_first_sample_becomes_the_ema() {
        // 1 MB in exactly one second = 8 Mbps.
        val ema = SpeedTestMath.smoothedMbps(null, sampleBytes = 1_000_000, sampleMillis = 1000)
        assertEquals(8.0, ema, 1e-6)
    }

    @Test
    fun smoothedMbps_blends_new_sample_with_alpha() {
        val first = SpeedTestMath.smoothedMbps(null, sampleBytes = 1_000_000, sampleMillis = 1000)
        val second = SpeedTestMath.smoothedMbps(first, sampleBytes = 500_000, sampleMillis = 1000)
        // EMA moves 25% of the way from 8 towards 4.
        assertEquals(7.0, second, 1e-6)
    }

    @Test
    fun smoothedMbps_zero_duration_keeps_previous_value() {
        assertEquals(5.0, SpeedTestMath.smoothedMbps(5.0, sampleBytes = 100, sampleMillis = 0), 1e-9)
        assertEquals(0.0, SpeedTestMath.smoothedMbps(null, sampleBytes = 100, sampleMillis = 0), 1e-9)
    }
}
