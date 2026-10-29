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
