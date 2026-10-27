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
    /** Signals selected-server transitions to the host runtime callback; it must not throw. */
    private val onCommittedLibrariesChanged: () -> Unit = {},
) {
    private var mutableServerLibraryChanged = false
    private var mutableProfileLibraryChanged = false
    private var mutablePreviousSelectedServerId: Int? = null
    private var mutableCurrentSelectedServerId: Int? = null

    val serverLibraryChanged: Boolean get() = mutableServerLibraryChanged
    val profileLibraryChanged: Boolean get() = mutableProfileLibraryChanged
    val previousSelectedServerId: Int? get() = mutablePreviousSelectedServerId
    val currentSelectedServerId: Int? get() = mutableCurrentSelectedServerId

    private val operation = SubscriptionRefreshBatchOperation(
        captureRequests = {
            // This lambda runs inside the common operation's re-entry gate.
            mutableServerLibraryChanged = false
            mutableProfileLibraryChanged = false
            mutablePreviousSelectedServerId = null
            mutableCurrentSelectedServerId = null
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
            val selectedServerIds = when {
                batchAdapter.serverLibraryChanged ->
                    batchAdapter.previousSelectedServerId to batchAdapter.currentSelectedServerId
                singleAdapter.serverLibraryChanged ->
                    singleAdapter.previousSelectedServerId to singleAdapter.currentSelectedServerId
                else -> null
            }
            mutablePreviousSelectedServerId = selectedServerIds?.first
            mutableCurrentSelectedServerId = selectedServerIds?.second
            val selectedServerChanged = mutablePreviousSelectedServerId != mutableCurrentSelectedServerId
            if (selectedServerChanged) {
                onCommittedLibrariesChanged()
            }
        }
    }
}
