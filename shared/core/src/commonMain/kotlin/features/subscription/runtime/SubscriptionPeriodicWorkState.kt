// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import features.work.PeriodicWorkState

/** In-process state for one subscription periodic request using WorkManager result timing. */
typealias SubscriptionPeriodicWorkState = PeriodicWorkState<SubscriptionWorkSpec>

/**
 * Records a worker result. SUCCESS and permanent FAILURE both complete this periodic period;
 * only RETRY increments the attempt count and retains an exponential retry deadline.
 */
fun SubscriptionPeriodicWorkState.recordAttemptResult(
    retryable: Boolean,
    atMillis: Long,
): SubscriptionPeriodicWorkState = recordAttempt(retry = retryable, atMillis = atMillis)
