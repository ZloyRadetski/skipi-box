// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.subscription.SubscriptionFetchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A persistence scope completed by a subscription refresh commit. */
enum class SubscriptionRefreshCommitScope {
    SERVERS,
    SUBSCRIPTION,
    PROFILE,
}

/** An ordinary persistence error, including the scopes already applied before it occurred. */
data class SubscriptionRefreshCommitFailure(
    val scope: SubscriptionRefreshCommitScope,
    val error: Throwable,
)

/** The observable result of applying one loaded response to current host state. */
data class SubscriptionRefreshCommitResult(
    val applicable: Boolean,
    val appliedScopes: List<SubscriptionRefreshCommitScope>,
    val failure: SubscriptionRefreshCommitFailure? = null,
)

/** Load details and commit receipt retained for host UI counts and partial-save reporting. */
data class SubscriptionRefreshResult(
    val loaded: LoadedSubscriptionRefresh,
    val commit: SubscriptionRefreshCommitResult,
)

/**
 * Host persistence boundary for a single refresh.
 *
 * `updateServers` must call [reconcileLatest] against the latest aggregate while holding the
 * host's state/repository update lock, then persist its returned server catalog if applicable.
 * `updateSubscription` must call [transform] against the latest full record and persist the
 * returned record atomically; a null input/result means the subscription no longer exists or
 * the loaded request identity is stale and must not be appended. Embedded profile handoff is
 * permitted only after the subscription update returned a record.
 */
interface SubscriptionRefreshCommitPort {
    suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshReconciliationResult,
    ): SubscriptionRefreshReconciliationResult

    suspend fun updateSubscription(
        subscriptionId: Int,
        transform: (SubscriptionRecord?) -> SubscriptionRecord?,
    ): SubscriptionRecord?

    suspend fun applyEmbeddedConfig(config: ResolvedEmbeddedSubscriptionConfig): Boolean
}

/**
 * Runs the concrete single-subscription refresh from fetch through host persistence. The clock
 * is sampled after load completes so the timestamp reflects a successful response, and the
 * loaded parse details remain available to host UI and background reporting.
 */
suspend fun <FetchOptions> refreshSubscription(
    request: SubscriptionRefreshLoadRequest<FetchOptions>,
    fetchResponse: suspend (url: String, userAgent: String, options: FetchOptions) -> SubscriptionFetchResponse,
    parsers: List<ProxyServerPayloadParser>,
    refreshedAtMillis: () -> Long,
    port: SubscriptionRefreshCommitPort,
): SubscriptionRefreshResult {
    val loaded = loadSubscriptionRefresh(
        request = request,
        fetchResponse = fetchResponse,
        parsers = parsers,
    )
    val committed = commitSubscriptionRefresh(
        loaded = loaded,
        refreshedAtMillis = refreshedAtMillis(),
        port = port,
    )
    return SubscriptionRefreshResult(loaded = loaded, commit = committed)
}

/**
 * Reconciles and commits one loaded response in host persistence order: servers, subscription
 * metadata, then the optional embedded profile. Each host callback rebases against latest state.
 * Ordinary failures return the scopes already applied; coroutine cancellation always propagates.
 */
suspend fun commitSubscriptionRefresh(
    loaded: LoadedSubscriptionRefresh,
    refreshedAtMillis: Long,
    port: SubscriptionRefreshCommitPort,
): SubscriptionRefreshCommitResult {
    val appliedScopes = mutableListOf<SubscriptionRefreshCommitScope>()

    val serverResult = try {
        currentCoroutineContext().ensureActive()
        port.updateServers { latest ->
            reconcileSubscriptionRefresh(
                snapshot = latest,
                loaded = loaded,
                refreshedAtMillis = refreshedAtMillis,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        return SubscriptionRefreshCommitResult(
            applicable = true,
            appliedScopes = appliedScopes,
            failure = SubscriptionRefreshCommitFailure(SubscriptionRefreshCommitScope.SERVERS, error),
        )
    }

    if (!serverResult.applicable) {
        return SubscriptionRefreshCommitResult(applicable = false, appliedScopes = appliedScopes)
    }
    appliedScopes += SubscriptionRefreshCommitScope.SERVERS

    val updatedSubscription = try {
        currentCoroutineContext().ensureActive()
        port.updateSubscription(loaded.sourceIdentity.id) { latest ->
            latest
                ?.takeIf { subscriptionRefreshRequestIdentity(it) == loaded.sourceIdentity }
                ?.withRefreshedMetadata(
                    response = loaded.metadata,
                    refreshedAtMillis = refreshedAtMillis,
                )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        return SubscriptionRefreshCommitResult(
            applicable = true,
            appliedScopes = appliedScopes,
            failure = SubscriptionRefreshCommitFailure(SubscriptionRefreshCommitScope.SUBSCRIPTION, error),
        )
    }

    if (updatedSubscription == null) {
        return SubscriptionRefreshCommitResult(applicable = false, appliedScopes = appliedScopes)
    }
    appliedScopes += SubscriptionRefreshCommitScope.SUBSCRIPTION

    val embeddedConfig = loaded.resolvedEmbeddedConfig ?: return SubscriptionRefreshCommitResult(
        applicable = true,
        appliedScopes = appliedScopes,
    )

    try {
        currentCoroutineContext().ensureActive()
        val profileApplied = port.applyEmbeddedConfig(embeddedConfig)
        if (!profileApplied) {
            return SubscriptionRefreshCommitResult(applicable = true, appliedScopes = appliedScopes)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        return SubscriptionRefreshCommitResult(
            applicable = true,
            appliedScopes = appliedScopes,
            failure = SubscriptionRefreshCommitFailure(SubscriptionRefreshCommitScope.PROFILE, error),
        )
    }
    appliedScopes += SubscriptionRefreshCommitScope.PROFILE

    return SubscriptionRefreshCommitResult(applicable = true, appliedScopes = appliedScopes)
}
