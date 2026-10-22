// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.SubscriptionRefreshBatchCommitResult
import app.skipi.app.subscription.SubscriptionRefreshBatchOperation
import app.skipi.app.subscription.SubscriptionRefreshBatchOperationResult
import app.skipi.app.subscription.SubscriptionRefreshBatchProgress
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshLoader
import app.skipi.app.subscription.commitSubscriptionRefreshBatch
import kotlinx.coroutines.flow.StateFlow

/** One retained Desktop batch operation, including loading and the real latest-state commit. */
internal class DesktopSubscriptionRefreshBatchRunner<FetchOptions>(
    captureRequests: suspend () -> List<SubscriptionRefreshLoadRequest<FetchOptions>>,
    loader: SubscriptionRefreshLoader<SubscriptionRefreshLoadRequest<FetchOptions>, LoadedSubscriptionRefresh>,
    refreshedAtMillis: () -> Long,
    private val subscriptionRepository: DesktopSubscriptionRepository,
    private val proxyServerRepository: DesktopProxyServerRepository,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
    /** Signals captured persisted changes, including partial failure; the host callback must not throw. */
    private val onCommittedLibrariesChanged: () -> Unit = {},
) {
    private var mutableServerLibraryChanged = false
    private var mutableProfileLibraryChanged = false

    val serverLibraryChanged: Boolean get() = mutableServerLibraryChanged
    val profileLibraryChanged: Boolean get() = mutableProfileLibraryChanged

    private val operation = SubscriptionRefreshBatchOperation(
        captureRequests = {
            // This lambda runs inside the common operation's re-entry gate.
            mutableServerLibraryChanged = false
            mutableProfileLibraryChanged = false
            captureRequests()
        },
        loader = loader,
        refreshedAtMillis = refreshedAtMillis,
        commitSingle = { loaded, refreshedAt -> commit(listOf(loaded), refreshedAt) },
        commitBatch = { loaded, refreshedAt -> commit(loaded, refreshedAt) },
    )

    val progress: StateFlow<SubscriptionRefreshBatchProgress> = operation.progress

    suspend fun run(): SubscriptionRefreshBatchOperationResult<
        SubscriptionRefreshLoadRequest<FetchOptions>,
        LoadedSubscriptionRefresh,
        SubscriptionRefreshBatchCommitResult,
    > = operation.run()

    private suspend fun commit(
        loaded: List<LoadedSubscriptionRefresh>,
        refreshedAtMillis: Long,
    ): SubscriptionRefreshBatchCommitResult {
        val singleAdapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = loaded.first().sourceIdentity.id,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = readConfigs,
            saveConfigs = saveConfigs,
            publishConfigs = publishConfigs,
            readLatencyByServerId = readLatencyByServerId,
            publishLatencyByServerId = publishLatencyByServerId,
        )
        val batchAdapter = DesktopSubscriptionRefreshBatchCommitAdapter(
            loaded = loaded,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = readConfigs,
            saveConfigs = saveConfigs,
            publishConfigs = publishConfigs,
            readLatencyByServerId = readLatencyByServerId,
            publishLatencyByServerId = publishLatencyByServerId,
        )
        return try {
            commitSubscriptionRefreshBatch(
                loaded = loaded,
                refreshedAtMillis = refreshedAtMillis,
                singlePort = singleAdapter,
                batchPort = batchAdapter,
            )
        } finally {
            mutableServerLibraryChanged = singleAdapter.serverLibraryChanged || batchAdapter.serverLibraryChanged
            mutableProfileLibraryChanged = singleAdapter.profileLibraryChanged || batchAdapter.profileLibraryChanged
            if (mutableServerLibraryChanged || mutableProfileLibraryChanged) {
                onCommittedLibrariesChanged()
            }
        }
    }
}
