// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Platform boundary for loading and decoding one subscription refresh. */
fun interface SubscriptionRefreshLoader<Request, Update> {
    suspend fun load(request: Request): Update
}

data class SubscriptionRefreshFailure<Request>(
    val request: Request,
    val error: Throwable,
)

data class SubscriptionRefreshBatchResult<Request, Update>(
    val updates: List<Update>,
    val failures: List<SubscriptionRefreshFailure<Request>>,
    val updatedAtMillis: Long,
)

/** Loads requests concurrently while keeping failures isolated to their request. */
suspend fun <Request, Update> refreshSubscriptions(
    requests: List<Request>,
    loader: SubscriptionRefreshLoader<Request, Update>,
    updatedAtMillis: () -> Long,
): SubscriptionRefreshBatchResult<Request, Update> = coroutineScope {
    val results = requests.map { request ->
        async {
            request to try {
                Result.success(loader.load(request))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }.awaitAll()

    SubscriptionRefreshBatchResult(
        updates = results.mapNotNull { (_, result) -> result.getOrNull() },
        failures = results.mapNotNull { (request, result) ->
            result.exceptionOrNull()?.let { error -> SubscriptionRefreshFailure(request, error) }
        },
        updatedAtMillis = updatedAtMillis(),
    )
}

enum class SubscriptionRefreshConflict {
    TARGET_CHANGED,
    EMBEDDED_PROFILE_CHANGED,
}

fun <Target> subscriptionRefreshTargetWasChanged(
    baseline: Target?,
    latest: Target?,
): Boolean = baseline != latest

fun <Profile> embeddedSubscriptionProfileWasChanged(
    baseline: Profile?,
    latest: Profile?,
): Boolean = baseline != latest

/** Shared optimistic concurrency policy for the target and embedded profile. */
fun <Target, Profile> subscriptionRefreshConflict(
    baselineTarget: Target?,
    latestTarget: Target?,
    baselineProfile: Profile?,
    latestProfile: Profile?,
): SubscriptionRefreshConflict? = when {
    subscriptionRefreshTargetWasChanged(baselineTarget, latestTarget) ->
        SubscriptionRefreshConflict.TARGET_CHANGED
    embeddedSubscriptionProfileWasChanged(baselineProfile, latestProfile) ->
        SubscriptionRefreshConflict.EMBEDDED_PROFILE_CHANGED
    else -> null
}
