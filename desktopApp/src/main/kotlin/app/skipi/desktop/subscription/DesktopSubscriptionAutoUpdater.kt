// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionWorkerOutcome
import features.subscription.runtime.SubscriptionRefreshTarget
import features.subscription.runtime.SubscriptionWorkSpec
import features.subscription.runtime.subscriptionRefreshWorkSpecOrNull

/**
 * Desktop owner of the periodic subscription refresh jobs. A refresh still running when the network goes
 * away is cancelled, and a worker outcome of "nothing to do" records nothing.
 */
internal class DesktopSubscriptionAutoUpdater(
    readLatestTargets: () -> List<SubscriptionRefreshTarget>,
    isNetworkConnected: () -> Boolean,
    updateAutomatically: suspend (Int) -> SubscriptionWorkerOutcome?,
    nowMillis: () -> Long,
) {
    private val scheduler = DesktopPeriodicRefreshScheduler<SubscriptionRefreshTarget, SubscriptionWorkSpec>(
        specOf = SubscriptionRefreshTarget::subscriptionRefreshWorkSpecOrNull,
        idOf = SubscriptionWorkSpec::groupId,
        readLatestTargets = readLatestTargets,
        isNetworkConnected = isNetworkConnected,
        cancelRunningWhenDisconnected = true,
        refresh = { groupId ->
            updateAutomatically(groupId)?.let { outcome -> outcome == SubscriptionWorkerOutcome.RETRY }
        },
        nowMillis = nowMillis,
    )

    /** Creates, updates, or removes eligible subscription schedules while retaining current state. */
    fun observeTargets(targets: List<SubscriptionRefreshTarget>) = scheduler.observeTargets(targets)

    /** Reconciles latest eligibility and runs due connected groups as structured children. */
    suspend fun runDue() = scheduler.runDue()

    /** The earliest deadline among currently eligible subscriptions, if any. */
    fun nextWakeupAtMillis(): Long? = scheduler.nextWakeupAtMillis()

    /** When the refresh loop should look again: no sooner than a minute while an item runs or the network is gone. */
    fun nextCheckAtMillis(): Long? = scheduler.nextCheckAtMillis()
}
