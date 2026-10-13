// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.SubscriptionServerCollectionUpdate
import app.skipi.app.proxy.reconcileSubscriptionServerCollection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The canonical Android eligibility rule for a bulk subscription refresh. */
fun <FetchOptions> eligibleSubscriptionRefreshRequests(
    requests: List<SubscriptionRefreshLoadRequest<FetchOptions>>,
): List<SubscriptionRefreshLoadRequest<FetchOptions>> = requests.filter { request ->
    request.subscription.enabled && request.subscription.url.isNotBlank()
}

/** Aggregate result for one catalog transform and one metadata/profile state transform. */
data class SubscriptionRefreshBatchReconciliationResult(
    val applicable: Boolean,
    val snapshot: SubscriptionRefreshSnapshot,
    /** Accepted source IDs in the original request order. */
    val appliedSubscriptionIds: List<Int>,
    /** Accepted embedded profiles in the same order as their source requests. */
    val resolvedEmbeddedConfigs: List<ResolvedEmbeddedSubscriptionConfig>,
)

/**
 * Applies all successful responses to one latest host snapshot. Identity checks happen before
 * either aggregate is changed; the catalog reconciler receives one ordered update list and
 * remaps its composite references and selection once for the whole batch.
 */
fun reconcileSubscriptionRefreshBatch(
    snapshot: SubscriptionRefreshSnapshot,
    loaded: List<LoadedSubscriptionRefresh>,
    refreshedAtMillis: Long,
): SubscriptionRefreshBatchReconciliationResult {
    val applicable = loaded.filter { update ->
        snapshot.subscriptions.any { current ->
            current.id == update.sourceIdentity.id &&
                subscriptionRefreshRequestIdentity(current) == update.sourceIdentity
        }
    }
    if (applicable.isEmpty()) {
        return SubscriptionRefreshBatchReconciliationResult(
            applicable = false,
            snapshot = snapshot,
            appliedSubscriptionIds = emptyList(),
            resolvedEmbeddedConfigs = emptyList(),
        )
    }

    val subscriptionsById = snapshot.subscriptions.associateBy(SubscriptionRecord::id)
    val collection = reconcileSubscriptionServerCollection(
        servers = snapshot.servers,
        updates = applicable.map { update ->
            val current = checkNotNull(subscriptionsById[update.sourceIdentity.id])
            SubscriptionServerCollectionUpdate(
                groupId = current.id,
                servers = update.servers,
                autoOverrideRules = current.autoOverrideRules,
            )
        },
        nextServerId = snapshot.nextServerId,
        selectedServerId = snapshot.selectedServerId,
        retainedServerIds = snapshot.retainedServerIds,
    )
    val updatesById = applicable.associateBy { it.sourceIdentity.id }
    val nextSnapshot = snapshot.copy(
        subscriptions = snapshot.subscriptions.map { current ->
            val update = updatesById[current.id]
            if (update == null) current else current.withRefreshedMetadata(
                response = update.metadata,
                refreshedAtMillis = refreshedAtMillis,
            )
        },
        servers = collection.servers,
        nextServerId = collection.nextServerId,
        selectedServerId = collection.selectedServerId,
    )
    return SubscriptionRefreshBatchReconciliationResult(
        applicable = true,
        snapshot = nextSnapshot,
        appliedSubscriptionIds = applicable.map { it.sourceIdentity.id },
        resolvedEmbeddedConfigs = applicable.mapNotNull(LoadedSubscriptionRefresh::resolvedEmbeddedConfig),
    )
}

/**
 * Host persistence port for an Android-compatible batch. Implementations must perform one
 * whole-catalog transform, then one latest-state metadata/profile transform. The profile list
 * passed through the second transform is already ordered and filtered against its latest state.
 */
interface SubscriptionRefreshBatchCommitPort {
    suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult

    suspend fun updateMetadataAndProfiles(
        applyLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult
}

data class SubscriptionRefreshBatchCommitResult(
    val applicable: Boolean,
    val appliedSubscriptionIds: List<Int>,
    /** Present only when exactly one successful response used the established single path. */
    val singleCommitResult: SubscriptionRefreshCommitResult? = null,
)

/**
 * Uses the established single-refresh commit for one successful response. Multiple successful
 * loads use exactly two aggregate persistence boundaries; ordinary persistence errors propagate
 * so a host can report them separately from isolated fetch failures.
 */
suspend fun commitSubscriptionRefreshBatch(
    loaded: List<LoadedSubscriptionRefresh>,
    refreshedAtMillis: Long,
    singlePort: SubscriptionRefreshCommitPort,
    batchPort: SubscriptionRefreshBatchCommitPort,
): SubscriptionRefreshBatchCommitResult {
    if (loaded.isEmpty()) {
        return SubscriptionRefreshBatchCommitResult(
            applicable = false,
            appliedSubscriptionIds = emptyList(),
        )
    }
    if (loaded.size == 1) {
        val single = commitSubscriptionRefresh(
            loaded = loaded.single(),
            refreshedAtMillis = refreshedAtMillis,
            port = singlePort,
        )
        return SubscriptionRefreshBatchCommitResult(
            applicable = single.applicable,
            appliedSubscriptionIds = if (single.applicable) listOf(loaded.single().sourceIdentity.id) else emptyList(),
            singleCommitResult = single,
        )
    }

    currentCoroutineContext().ensureActive()
    val serverResult = batchPort.updateServers { latest ->
        reconcileSubscriptionRefreshBatch(
            snapshot = latest,
            loaded = loaded,
            refreshedAtMillis = refreshedAtMillis,
        )
    }
    if (!serverResult.applicable) {
        return SubscriptionRefreshBatchCommitResult(
            applicable = false,
            appliedSubscriptionIds = emptyList(),
        )
    }

    val stateResult = batchPort.updateMetadataAndProfiles { latest ->
        reconcileSubscriptionRefreshBatch(
            snapshot = latest,
            loaded = loaded,
            refreshedAtMillis = refreshedAtMillis,
        )
    }
    return SubscriptionRefreshBatchCommitResult(
        applicable = stateResult.applicable,
        appliedSubscriptionIds = stateResult.appliedSubscriptionIds,
    )
}

data class SubscriptionRefreshBatchProgress(
    val refreshingSubscriptionIds: Set<Int> = emptySet(),
)

data class SubscriptionRefreshBatchOperationResult<Request, Update, CommitResult>(
    val refresh: SubscriptionRefreshBatchResult<Request, Update>,
    /** Null when all requests failed or the eligible request set was empty. */
    val commitResult: CommitResult?,
)

/**
 * Serializes bulk refresh invocations, captures and filters current requests inside the gate,
 * and keeps the full eligible ID set visible until loading and the selected commit path finish.
 * The loader may retain a host's per-subscription coordinator and lazily derive native options
 * at the point the request enters that coordinator.
 */
class SubscriptionRefreshBatchOperation<FetchOptions, Update, CommitResult>(
    private val captureRequests: suspend () -> List<SubscriptionRefreshLoadRequest<FetchOptions>>,
    private val loader: SubscriptionRefreshLoader<SubscriptionRefreshLoadRequest<FetchOptions>, Update>,
    private val refreshedAtMillis: () -> Long,
    private val commitSingle: suspend (Update, Long) -> CommitResult,
    private val commitBatch: suspend (List<Update>, Long) -> CommitResult,
) {
    private val operationMutex = Mutex()
    private val mutableProgress = MutableStateFlow(SubscriptionRefreshBatchProgress())

    val progress: StateFlow<SubscriptionRefreshBatchProgress> = mutableProgress.asStateFlow()

    suspend fun run(): SubscriptionRefreshBatchOperationResult<SubscriptionRefreshLoadRequest<FetchOptions>, Update, CommitResult> =
        operationMutex.withLock {
            val requests = eligibleSubscriptionRefreshRequests(captureRequests())
            if (requests.isEmpty()) {
                return@withLock SubscriptionRefreshBatchOperationResult(
                    refresh = refreshSubscriptions(
                        requests = emptyList(),
                        loader = loader,
                        updatedAtMillis = refreshedAtMillis,
                    ),
                    commitResult = null,
                )
            }

            mutableProgress.value = SubscriptionRefreshBatchProgress(
                refreshingSubscriptionIds = requests.mapTo(linkedSetOf()) { it.subscription.id },
            )
            try {
                val result = refreshSubscriptions(
                    requests = requests,
                    loader = loader,
                    updatedAtMillis = refreshedAtMillis,
                )
                val commit = when (result.updates.size) {
                    0 -> null
                    1 -> commitSingle(result.updates.single(), result.updatedAtMillis)
                    else -> commitBatch(result.updates, result.updatedAtMillis)
                }
                SubscriptionRefreshBatchOperationResult(
                    refresh = result,
                    commitResult = commit,
                )
            } finally {
                mutableProgress.value = SubscriptionRefreshBatchProgress()
            }
        }
}
