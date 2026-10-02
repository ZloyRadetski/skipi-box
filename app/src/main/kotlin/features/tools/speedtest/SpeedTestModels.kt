// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import features.tools.speedtest.SpeedTestMetrics

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
internal object SpeedTestMath {
    /**
     * Mean absolute difference between consecutive round-trip times (RFC 3550
     * style jitter), in the same unit as the input samples.
     */
    fun jitter(roundTripTimes: List<Double>): Double {
        return SpeedTestMetrics.jitter(roundTripTimes)
    }

    /**
     * Median of [values]; returns null for an empty list. Used for ping so a
     * single cold-connection outlier does not skew the reported latency.
     */
    fun median(values: List<Double>): Double? {
        return SpeedTestMetrics.median(values)
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
        return SpeedTestMetrics.smoothedMbps(previousEma, sampleBytes, sampleMillis, alpha)
    }
}
