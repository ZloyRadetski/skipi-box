// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import app.AppState
import app.skipi.app.subscription.SubscriptionWorkerOutcome
import app.skipi.app.subscription.shouldFetchScheduledSubscription
import app.skipi.app.subscription.subscriptionWorkerOutcome
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdateResult
import features.subscription.isTransientSubscriptionFailure

internal typealias SubscriptionWorkerResult = SubscriptionWorkerOutcome

internal class SubscriptionWorkerRunner(
    private val stateProvider: () -> AppState,
    private val update: suspend (Int) -> ProxyServerListSubscriptionUpdateResult,
) {
    suspend fun run(groupId: Int): SubscriptionWorkerResult {
        val group = stateProvider().subscriptionGroups.firstOrNull { it.id == groupId }
        if (!shouldFetchScheduledSubscription(
                groupFound = group != null,
                enabled = group?.enabled ?: false,
                url = group?.url.orEmpty(),
            )
        ) {
            return SubscriptionWorkerResult.SUCCESS
        }
        val result = update(groupId)
        return subscriptionWorkerOutcome(
            transientFailureFlags = result.failures.map { failure ->
                isTransientSubscriptionFailure(failure.error)
            },
        )
    }
}
