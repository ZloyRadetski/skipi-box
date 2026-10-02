// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

/** Pure aggregation and display math shared by diagnostic clients. */
object SpeedTestMetrics {
    /** Mean absolute difference between consecutive round-trip times. */
    fun jitter(roundTripTimes: List<Double>): Double {
        if (roundTripTimes.size < 2) return 0.0
        var sum = 0.0
        for (index in 1 until roundTripTimes.size) {
            sum += kotlin.math.abs(roundTripTimes[index] - roundTripTimes[index - 1])
        }
        return sum / (roundTripTimes.size - 1)
    }

    /** Median latency, robust against a single cold-connection sample. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    /** Exponential moving average of throughput in megabits per second. */
    fun smoothedMbps(
        previousEma: Double?,
        sampleBytes: Long,
        sampleMillis: Long,
        alpha: Double = 0.25,
    ): Double {
        if (sampleMillis <= 0) return previousEma ?: 0.0
        val instantMbps = sampleBytes * 8.0 * 1000.0 / sampleMillis / 1_000_000.0
        return if (previousEma == null) instantMbps else previousEma + alpha * (instantMbps - previousEma)
    }
}
