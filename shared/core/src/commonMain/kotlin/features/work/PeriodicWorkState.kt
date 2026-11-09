// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.work

import features.config.runtime.workManagerExponentialBackoffDelayMillis

/** What a periodic request needs to know about its own timing. */
interface PeriodicWorkSpec {
    val repeatIntervalMillis: Long
    val backoffMillis: Long
}

/**
 * In-process state of one periodic request with WorkManager's result timing: the first run is due at its enqueue
 * anchor, a finished period starts a fresh interval at completion, and a retry waits out an exponential backoff.
 * Profile, geo-resource and subscription refresh each keep one of these per item.
 */
data class PeriodicWorkState<Spec : PeriodicWorkSpec>(
    val spec: Spec,
    val periodAnchorMillis: Long,
    val completedPeriods: Long = 0L,
    val runAttemptCount: Int = 0,
    val retryAtMillis: Long? = null,
) {
    init {
        require(spec.repeatIntervalMillis > 0L) { "Periodic work interval must be positive" }
        require(completedPeriods >= 0L) { "Completed periods cannot be negative" }
        require(runAttemptCount >= 0) { "Run attempt count cannot be negative" }
    }

    /** Updates the request fields while keeping the current period and any pending retry. */
    fun withUpdatedSpec(spec: Spec): PeriodicWorkState<Spec> = copy(spec = spec)

    val nextRunAtMillis: Long
        get() = retryAtMillis ?: if (completedPeriods == 0L) {
            periodAnchorMillis
        } else {
            periodAnchorMillis + spec.repeatIntervalMillis
        }

    /** A finished attempt completes the period; a [retry] counts the attempt and waits out the backoff. */
    fun recordAttempt(retry: Boolean, atMillis: Long): PeriodicWorkState<Spec> = if (!retry) {
        copy(
            periodAnchorMillis = atMillis,
            completedPeriods = incrementSaturated(completedPeriods),
            runAttemptCount = 0,
            retryAtMillis = null,
        )
    } else {
        val nextAttempt = incrementSaturated(runAttemptCount)
        val retryDelayMillis = workManagerExponentialBackoffDelayMillis(
            runAttemptCount = nextAttempt,
            baseDelayMillis = spec.backoffMillis,
        )
        copy(
            periodAnchorMillis = atMillis,
            runAttemptCount = nextAttempt,
            retryAtMillis = atMillis + retryDelayMillis,
        )
    }
}

private fun incrementSaturated(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

private fun incrementSaturated(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1
