// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import features.config.runtime.workManagerExponentialBackoffDelayMillis

/** In-process state for one subscription periodic request using WorkManager result timing. */
data class SubscriptionPeriodicWorkState(
    val spec: SubscriptionWorkSpec,
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

    /** Updates request fields while retaining the current period and any pending retry. */
    fun withUpdatedSpec(spec: SubscriptionWorkSpec): SubscriptionPeriodicWorkState = copy(spec = spec)

    /**
     * The first periodic request is due at its enqueue anchor. Later non-retry results start a
     * fresh interval at completion; a retry uses WorkManager's exponential backoff deadline.
     */
    val nextRunAtMillis: Long
        get() = retryAtMillis ?: if (completedPeriods == 0L) {
            periodAnchorMillis
        } else {
            periodAnchorMillis + spec.repeatIntervalMillis
        }

    /**
     * Records a worker result. SUCCESS and permanent FAILURE both complete this periodic period;
     * only RETRY increments the attempt count and retains an exponential retry deadline.
     */
    fun recordAttemptResult(
        retryable: Boolean,
        atMillis: Long,
    ): SubscriptionPeriodicWorkState = if (!retryable) {
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
