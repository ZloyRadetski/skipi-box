// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

/** Result of the portable policy for one scheduled subscription refresh. */
enum class SubscriptionWorkerOutcome {
    SUCCESS,
    RETRY,
    FAILURE,
}

/** Whether a scheduled request has a current, enabled subscription with a usable URL. */
fun shouldFetchScheduledSubscription(
    groupFound: Boolean,
    enabled: Boolean,
    url: String,
): Boolean = groupFound && enabled && url.isNotBlank()

/**
 * Converts host-classified failures into the Android-compatible scheduled outcome. A transient
 * failure anywhere in a partial result keeps the whole scheduled work retryable.
 */
fun subscriptionWorkerOutcome(transientFailureFlags: List<Boolean>): SubscriptionWorkerOutcome = when {
    transientFailureFlags.isEmpty() -> SubscriptionWorkerOutcome.SUCCESS
    transientFailureFlags.any { it } -> SubscriptionWorkerOutcome.RETRY
    else -> SubscriptionWorkerOutcome.FAILURE
}
