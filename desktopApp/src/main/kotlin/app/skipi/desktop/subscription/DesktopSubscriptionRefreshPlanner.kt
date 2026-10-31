// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.runtime.SubscriptionRefreshTarget
import features.subscription.runtime.planSubscriptionRefreshes

/**
 * A pure scheduling decision for desktop subscription refreshes.
 *
 * The caller owns the actual timer and refresh work. Supplying [nowMillis]
 * explicitly makes the decision deterministic and keeps this class safe to use
 * from the desktop UI, a background service, or tests.
 */
data class DesktopSubscriptionRefreshPlan(
    val dueSubscriptions: List<DesktopStoredSubscription>,
    val nextWakeupAtMillis: Long?,
    val nextDelayMillis: Long?,
)

/**
 * Computes which stored providers need an automatic refresh and when the next
 * planning pass should run. It deliberately performs no I/O, networking, or
 * clock access.
 *
 * Its interval rules mirror Android's subscription scheduler: blank and
 * non-positive values disable scheduling, positive values below 0.25 hours or
 * values that cannot fit in milliseconds are ignored.
 */
object DesktopSubscriptionRefreshPlanner {
    fun plan(
        library: DesktopSubscriptionLibrary,
        nowMillis: Long,
    ): DesktopSubscriptionRefreshPlan {
        val subscriptionsById = library.subscriptions.associateBy(DesktopStoredSubscription::id)
        val sharedPlan = planSubscriptionRefreshes(
            targets = library.subscriptions.map(DesktopStoredSubscription::toSubscriptionRefreshTarget),
            nowMillis = nowMillis,
        )
        return DesktopSubscriptionRefreshPlan(
            dueSubscriptions = sharedPlan.dueSubscriptionIds.mapNotNull(subscriptionsById::get),
            nextWakeupAtMillis = sharedPlan.nextWakeupAtMillis,
            nextDelayMillis = sharedPlan.nextDelayMillis,
        )
    }
}

private fun DesktopStoredSubscription.toSubscriptionRefreshTarget(): SubscriptionRefreshTarget =
    SubscriptionRefreshTarget(
        id = id,
        url = url,
        enabled = enabled,
        updateInterval = updateInterval,
        lastUpdatedAtMillis = metadata.lastUpdatedAtMillis,
    )
