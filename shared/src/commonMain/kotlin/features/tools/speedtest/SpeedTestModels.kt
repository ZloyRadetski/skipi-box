// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

/**
 * Phases of a single speed test run, executed strictly in order:
 * ping -> download -> upload -> finished.
 */
enum class SpeedTestPhase {
    Idle,
    Ping,
    Download,
    Upload,
    Finished,
    Failed,
}

/** Final measurements of a completed speed test run. */
data class SpeedTestResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

/**
 * Observable state of a running (or completed) speed test.
 * [progress] is 0f..1f within the current phase; [currentMbps] is the live,
 * smoothed throughput while downloading/uploading.
 */
data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.Idle,
    val progress: Float = 0f,
    val currentMbps: Double? = null,
    val result: SpeedTestResult? = null,
    val errorMessage: String? = null,
)

/** Pure measurement math used by [SpeedTestEngine]; unit tested. */
object SpeedTestMath {
    /**
     * Mean absolute difference between consecutive round-trip times (RFC 3550
     * style jitter), in the same unit as the input samples.
     */
    fun jitter(roundTripTimes: List<Double>): Double {
        if (roundTripTimes.size < 2) return 0.0
        var sum = 0.0
        for (index in 1 until roundTripTimes.size) {
            sum += kotlin.math.abs(roundTripTimes[index] - roundTripTimes[index - 1])
        }
        return sum / (roundTripTimes.size - 1)
    }

    /**
     * Median of [values]; returns null for an empty list. Used for ping so a
     * single cold-connection outlier does not skew the reported latency.
     */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    /**
     * Exponential moving average over instantaneous throughput samples,
     * expressed in megabits per second. [sampleBytes] transferred during
     * [sampleMillis] milliseconds updates the previous EMA value with weight
     * [alpha].
     */
    fun smoothedMbps(
        previousEma: Double?,
        sampleBytes: Long,
        sampleMillis: Long,
        alpha: Double = 0.25,
    ): Double {
        if (sampleMillis <= 0) return previousEma ?: 0.0
        val instantMbps = sampleBytes * BITS_PER_BYTE * MILLIS_PER_SECOND / sampleMillis / BITS_PER_MEGABIT
        return if (previousEma == null) instantMbps else previousEma + alpha * (instantMbps - previousEma)
    }

    private const val BITS_PER_BYTE = 8.0
    private const val MILLIS_PER_SECOND = 1000.0
    private const val BITS_PER_MEGABIT = 1_000_000.0
}
