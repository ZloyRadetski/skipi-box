// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.model.ProxyServer
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.server.createProxyServerDraft
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.SubscriptionServerCollectionUpdate
import app.skipi.app.proxy.deleteProxyServerRecords
import app.skipi.app.proxy.importProxyServerRecords
import app.skipi.app.proxy.reconcileSubscriptionServerCollection
import app.skipi.app.proxy.saveProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshBatchCommitPort
import app.skipi.app.subscription.SubscriptionRefreshBatchReconciliationResult
import app.skipi.app.subscription.SubscriptionRefreshCommitPort
import app.skipi.app.subscription.SubscriptionRefreshReconciliationResult
import app.skipi.app.subscription.SubscriptionRefreshRequestIdentity
import app.skipi.app.subscription.SubscriptionRefreshSnapshot
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.commitSubscriptionRefreshBatch
import app.skipi.app.subscription.commitSubscriptionRefresh
import app.skipi.app.subscription.reconcileSubscriptionRefresh
import app.skipi.app.subscription.eligibleSubscriptionRefreshRequests
import app.skipi.app.subscription.withRefreshedMetadata
import data.repository.toAndroidGroup
import data.repository.toSubscriptionRecord
import data.repository.withProxyServerCatalog
import features.config.withImportedTrafficConfig
import features.subscription.SubscriptionMetadata
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.CancellationException
import app.skipi.app.store.AppStateStore
import app.skipi.app.repository.ProxyServerRepository

suspend fun applyProxySubscriptionUpdates(
    stateStore: AppStateStore,
    proxyServers: ProxyServerRepository,
    reconcileTrafficConfigProxyGroups: suspend () -> Unit,
    updates: List<ProxyServerListSubscriptionUpdate>,
    updatedAtMillis: Long,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    if (updates.isEmpty()) return
    if (updates.size == 1) {
        val receipt = commitSubscriptionRefresh(
            loaded = updates.single().toLoadedSubscriptionRefresh(),
            refreshedAtMillis = updatedAtMillis,
            port = SubscriptionRefreshCommitPortImpl(stateStore, proxyServers, reconcileTrafficConfigProxyGroups, updateAppState),
        )
        receipt.failure?.let { failure -> throw failure.error }
        return
    }
    applyProxySubscriptionUpdatesBatch(stateStore, proxyServers, reconcileTrafficConfigProxyGroups, updates, updatedAtMillis, updateAppState)
}

private suspend fun applyProxySubscriptionUpdatesBatch(
    stateStore: AppStateStore,
    proxyServers: ProxyServerRepository,
    reconcileTrafficConfigProxyGroups: suspend () -> Unit,
    updates: List<ProxyServerListSubscriptionUpdate>,
    updatedAtMillis: Long,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    val result = commitSubscriptionRefreshBatch(
        loaded = updates.map(ProxyServerListSubscriptionUpdate::toLoadedSubscriptionRefresh),
        refreshedAtMillis = updatedAtMillis,
        singlePort = SubscriptionRefreshCommitPortImpl(stateStore, proxyServers, reconcileTrafficConfigProxyGroups, updateAppState),
        batchPort = SubscriptionRefreshBatchCommitPortImpl(stateStore, proxyServers, reconcileTrafficConfigProxyGroups, updateAppState),
    )
    result.singleCommitResult?.failure?.let { failure -> throw failure.error }
}

private class SubscriptionRefreshBatchCommitPortImpl(
    private val stateStore: AppStateStore,
    private val proxyServers: ProxyServerRepository,
    private val reconcileTrafficConfigProxyGroups: suspend () -> Unit,
    private val updateAppState: ((AppState) -> AppState) -> Unit,
) : SubscriptionRefreshBatchCommitPort {
    override suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult {
        var result: SubscriptionRefreshBatchReconciliationResult? = null
        proxyServers.updateCatalog { current ->
            val latestState = stateStore.currentState
            val reconciled = reconcileLatest(latestState.toSubscriptionRefreshSnapshot(current))
            result = reconciled
            if (!reconciled.applicable) {
                current
            } else {
                ProxyServerCatalog(
                    servers = reconciled.snapshot.servers.map { server ->
                        app.skipi.app.model.ProxyServerRecord(
                            id = server.id,
                            server = server.server,
                            sourceSubscriptionId = server.groupId.takeIf { it != DefaultSubscriptionGroupId },
                        )
                    },
                    nextServerId = reconciled.snapshot.nextServerId,
                    selectedServerId = reconciled.snapshot.selectedServerId,
                    retainedServerIds = current.retainedServerIds,
                )
            }
        }
        return checkNotNull(result)
    }

    override suspend fun updateMetadataAndProfiles(
        applyLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult {
        val previous = stateStore.currentState
        var result: SubscriptionRefreshBatchReconciliationResult? = null
        updateAppState { state ->
            val reconciled = applyLatest(state.toSubscriptionRefreshSnapshot())
            result = reconciled
            if (!reconciled.applicable) {
                state
            } else {
                var nextState = state.withSubscriptionRefreshSnapshot(reconciled.snapshot).copy(
                    proxyServers = state.proxyServers,
                    nextProxyServerId = state.nextProxyServerId,
                    selectedProxyServerId = state.selectedProxyServerId,
                )
                reconciled.resolvedEmbeddedConfigs.forEach { config ->
                    if (config.content.isNotBlank()) {
                        nextState = runCatching {
                            nextState.withImportedTrafficConfig(
                                content = config.content,
                                activate = config.activate,
                                fallbackName = config.fallbackName,
                                sourceUrl = config.sourceUrl,
                            )
                        }.getOrDefault(nextState)
                    }
                }
                nextState
            }
        }
        val reconciled = checkNotNull(result)
        val updated = stateStore.currentState
        if (previous.trafficConfigs != updated.trafficConfigs ||
            previous.activeTrafficConfigId != updated.activeTrafficConfigId
        ) {
            reconcileTrafficConfigProxyGroups()
        }
        return reconciled
    }
}

private class SubscriptionRefreshCommitPortImpl(
    private val stateStore: AppStateStore,
    private val proxyServers: ProxyServerRepository,
    private val reconcileTrafficConfigProxyGroups: suspend () -> Unit,
    private val updateAppState: ((AppState) -> AppState) -> Unit,
) : SubscriptionRefreshCommitPort {
    override suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshReconciliationResult,
    ): SubscriptionRefreshReconciliationResult {
        var result: SubscriptionRefreshReconciliationResult? = null
        stateStore.update { state ->
            val reconciled = reconcileLatest(state.toSubscriptionRefreshSnapshot())
            result = reconciled
            if (!reconciled.applicable) {
                state
            } else {
                state.withProxyServerCatalog(
                    ProxyServerCatalog(
                        servers = reconciled.snapshot.servers.map { server ->
                            app.skipi.app.model.ProxyServerRecord(
                                id = server.id,
                                server = server.server,
                                sourceSubscriptionId = server.groupId.takeIf {
                                    it != DefaultSubscriptionGroupId
                                },
                            )
                        },
                        nextServerId = reconciled.snapshot.nextServerId,
                        selectedServerId = reconciled.snapshot.selectedServerId,
                    ),
                )
            }
        }
        return checkNotNull(result)
    }

    override suspend fun updateSubscription(
        subscriptionId: Int,
        transform: (SubscriptionRecord?) -> SubscriptionRecord?,
    ): SubscriptionRecord? {
        var updated: SubscriptionRecord? = null
        updateAppState { state ->
            val target = state.subscriptionGroups.firstOrNull { group -> group.id == subscriptionId }
            val refreshed = transform(target?.toRefreshSubscriptionRecord())
            updated = refreshed
            if (target == null || refreshed == null) {
                state
            } else {
                state.copy(
                    subscriptionGroups = state.subscriptionGroups.map { group ->
                        if (group.id == subscriptionId) refreshed.toAndroidGroup(group) else group
                    },
                )
            }
        }
        return updated
    }

    override suspend fun applyEmbeddedConfig(config: ResolvedEmbeddedSubscriptionConfig): Boolean {
        if (config.content.isBlank()) return false
        val previous = stateStore.currentState
        var imported = false
        updateAppState { state ->
            try {
                state.withImportedTrafficConfig(
                    content = config.content,
                    activate = config.activate,
                    fallbackName = config.fallbackName,
                    sourceUrl = config.sourceUrl,
                )
                    .also { imported = true }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                state
            }
        }
        if (!imported) return false
        val updated = stateStore.currentState
        if (previous.trafficConfigs != updated.trafficConfigs ||
            previous.activeTrafficConfigId != updated.activeTrafficConfigId
        ) {
            reconcileTrafficConfigProxyGroups()
        }
        return true
    }
}
