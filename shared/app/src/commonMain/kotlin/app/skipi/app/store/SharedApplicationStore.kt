// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.store

import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ResourceCatalogRecord
import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.proxy.deleteProxyServerRecords
import app.skipi.app.proxy.importProxyServerRecordBatch
import app.skipi.app.repository.AppRepositories
import app.skipi.app.runtime.AppRuntimeState
import features.proxy.server.model.ProxyServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/** One portable snapshot composed from persisted repositories and volatile runtime data. */
data class SharedApplicationState(
    val settings: PersistedSettings = PersistedSettings(),
    val proxyServers: List<ProxyServerRecord> = emptyList(),
    val subscriptions: List<SubscriptionRecord> = emptyList(),
    val trafficConfigs: List<TrafficConfigRecord> = emptyList(),
    val routing: RoutingConfigRecord = RoutingConfigRecord(),
    val resources: ResourceCatalogRecord = ResourceCatalogRecord(),
    /** Kept separate from persisted settings and never implied to be serializable. */
    val runtime: AppRuntimeState = AppRuntimeState(),
    val inFlightActionIds: Set<Long> = emptySet(),
)

/** Host-specific data and operations are attached outside the common state contract. */
interface HostApplicationExtension {
    val extensionId: String
    val capabilities: Set<String>
}

sealed interface SharedApplicationAction {
    data class UpdateSettings(val transform: (PersistedSettings) -> PersistedSettings) : SharedApplicationAction
    data class SelectProxyServer(val serverId: Int) : SharedApplicationAction
    data class UpdateProxyServers(
        val transform: (List<ProxyServerRecord>) -> List<ProxyServerRecord>,
    ) : SharedApplicationAction
    data class UpdateProxyCatalog(val transform: (ProxyServerCatalog) -> ProxyServerCatalog) : SharedApplicationAction
    /** Imports one ordered batch as manual servers unless a subscription association is supplied. */
    data class ImportProxyServerBatch(
        val servers: List<ProxyServer<*>>,
        val sourceSubscriptionId: Int? = null,
    ) : SharedApplicationAction
    data class UpsertProxyServer(val server: ProxyServerRecord) : SharedApplicationAction
    data class RemoveProxyServer(val serverId: Int) : SharedApplicationAction
    data class RemoveProxyServers(val serverIds: Set<Int>) : SharedApplicationAction
    data class UpsertSubscription(val subscription: SubscriptionRecord) : SharedApplicationAction
    data class RemoveSubscription(val subscriptionId: Int) : SharedApplicationAction
    data class RefreshSubscription(val subscriptionId: Int) : SharedApplicationAction
    data class UpsertTrafficConfig(val config: TrafficConfigRecord) : SharedApplicationAction
    data class RemoveTrafficConfig(val configId: Int) : SharedApplicationAction
    data class UpdateRouting(val transform: (RoutingConfigRecord) -> RoutingConfigRecord) : SharedApplicationAction
    data class UpdateResources(val transform: (ResourceCatalogRecord) -> ResourceCatalogRecord) : SharedApplicationAction
    data class UpdateRuntime(val transform: (AppRuntimeState) -> AppRuntimeState) : SharedApplicationAction
}

sealed interface SharedApplicationActionOutcome {
    data object Completed : SharedApplicationActionOutcome
    data class Rejected(val reason: String) : SharedApplicationActionOutcome
    data class Failed(val reason: String) : SharedApplicationActionOutcome
}

data class SharedApplicationActionResult(
    val actionId: Long,
    val outcome: SharedApplicationActionOutcome,
)

/**
 * Minimal application orchestration over repository contracts. Adapters own persistence and
 * platform services; this store only validates cross-repository operations and publishes state.
 */
class SharedApplicationStore(
    private val repositories: AppRepositories,
    private val scope: CoroutineScope,
) {
    private data class RepositorySnapshot(
        val settings: PersistedSettings,
        val proxyServers: List<ProxyServerRecord>,
        val subscriptions: List<SubscriptionRecord>,
        val trafficConfigs: List<TrafficConfigRecord>,
        val runtime: AppRuntimeState,
    )

    private val mutableState = MutableStateFlow(SharedApplicationState())
    val state: StateFlow<SharedApplicationState> = mutableState.asStateFlow()

    private val mutableLastActionResult = MutableStateFlow<SharedApplicationActionResult?>(null)
    val lastActionResult: StateFlow<SharedApplicationActionResult?> = mutableLastActionResult.asStateFlow()

    private val nextActionId = MutableStateFlow(1L)
    private val actionMutex = Mutex()

    init {
        scope.launch {
            val primary = combine(
                repositories.settings.state,
                repositories.proxyServers.servers,
                repositories.subscriptions.subscriptions,
                repositories.trafficConfigs.configs,
                repositories.runtime.state,
            ) { settings, servers, subscriptions, configs, runtime ->
                RepositorySnapshot(settings, servers, subscriptions, configs, runtime)
            }
            combine(
                primary,
                repositories.routing?.state ?: MutableStateFlow(RoutingConfigRecord()),
                repositories.resources?.state ?: MutableStateFlow(ResourceCatalogRecord()),
            ) { snapshot, routing, resources ->
                mutableState.update {
                    it.copy(
                        settings = snapshot.settings,
                        proxyServers = snapshot.proxyServers,
                        subscriptions = snapshot.subscriptions,
                        trafficConfigs = snapshot.trafficConfigs,
                        runtime = snapshot.runtime,
                        routing = routing,
                        resources = resources,
                    )
                }
            }.collect { }
        }
    }

    /** Enqueues an action and returns its correlation ID. Observe [lastActionResult] for completion. */
    fun dispatch(action: SharedApplicationAction): Long {
        val actionId = nextActionId.getAndUpdate { it + 1 }
        mutableState.update { it.copy(inFlightActionIds = it.inFlightActionIds + actionId) }
        scope.launch { performAction(actionId, action) }
        return actionId
    }

    /** Runs an action to completion and returns its result to host flows that must sequence work. */
    suspend fun dispatchAndAwait(action: SharedApplicationAction): SharedApplicationActionResult {
        val actionId = nextActionId.getAndUpdate { it + 1 }
        mutableState.update { it.copy(inFlightActionIds = it.inFlightActionIds + actionId) }
        return performAction(actionId, action)
    }

    /**
     * Enqueues an action in the store scope and awaits its result. The queued action belongs to
     * the store lifetime rather than the host observer that awaits it, so cancelling a screen or
     * result observer does not discard an already-consumed editor result. Cancelling the store
     * scope still cancels the action.
     */
    suspend fun dispatchAndAwaitInStoreScope(
        action: SharedApplicationAction,
    ): SharedApplicationActionResult {
        val actionId = nextActionId.getAndUpdate { it + 1 }
        mutableState.update { it.copy(inFlightActionIds = it.inFlightActionIds + actionId) }
        return scope.async { performAction(actionId, action) }.await()
    }

    private suspend fun performAction(actionId: Long, action: SharedApplicationAction): SharedApplicationActionResult {
        val outcome = try {
            actionMutex.withLock { execute(action) }
            SharedApplicationActionOutcome.Completed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            SharedApplicationActionOutcome.Rejected(failure.message ?: "Action rejected")
        } catch (failure: UnsupportedOperationException) {
            SharedApplicationActionOutcome.Rejected(failure.message ?: "Action unavailable")
        } catch (failure: Exception) {
            SharedApplicationActionOutcome.Failed(failure.message ?: failure::class.simpleName.orEmpty())
        } finally {
            mutableState.update { it.copy(inFlightActionIds = it.inFlightActionIds - actionId) }
        }
        return SharedApplicationActionResult(actionId, outcome).also { result ->
            mutableLastActionResult.value = result
        }
    }

    private suspend fun execute(action: SharedApplicationAction) {
        when (action) {
            is SharedApplicationAction.UpdateSettings -> repositories.settings.update(action.transform)
            is SharedApplicationAction.SelectProxyServer -> {
                require(repositories.proxyServers.servers.value.any { it.id == action.serverId }) {
                    "Unknown proxy server ID: ${action.serverId}"
                }
                repositories.proxyServers.select(action.serverId)
            }
            is SharedApplicationAction.UpdateProxyServers -> repositories.proxyServers.updateCollection { current ->
                val next = action.transform(current)
                require(next.all { it.id > 0 }) { "Proxy server IDs must be positive" }
                require(next.map { it.id }.distinct().size == next.size) { "Proxy server IDs must be unique" }
                next
            }
            is SharedApplicationAction.UpdateProxyCatalog -> repositories.proxyServers.updateCatalog { current ->
                val next = action.transform(current)
                require(next.servers.all { it.id > 0 }) { "Proxy server IDs must be positive" }
                require(next.servers.map { it.id }.distinct().size == next.servers.size) { "Proxy server IDs must be unique" }
                require(next.nextServerId > 0) { "Next proxy server ID must be positive" }
                next
            }
            is SharedApplicationAction.ImportProxyServerBatch -> repositories.proxyServers.updateCatalog { current ->
                importProxyServerRecordBatch(
                    catalog = current,
                    importedServers = action.servers,
                    sourceSubscriptionId = action.sourceSubscriptionId,
                )
            }
            is SharedApplicationAction.UpsertProxyServer -> repositories.proxyServers.upsert(action.server)
            is SharedApplicationAction.RemoveProxyServer -> removeProxyServers(setOf(action.serverId))
            is SharedApplicationAction.RemoveProxyServers -> removeProxyServers(action.serverIds)
            is SharedApplicationAction.UpsertSubscription -> repositories.subscriptions.upsert(action.subscription)
            is SharedApplicationAction.RemoveSubscription -> repositories.subscriptions.remove(action.subscriptionId)
            is SharedApplicationAction.RefreshSubscription -> {
                val result = repositories.subscriptions.refresh(action.subscriptionId)
                result.getOrElse { throw it }
            }
            is SharedApplicationAction.UpsertTrafficConfig -> repositories.trafficConfigs.upsert(action.config)
            is SharedApplicationAction.RemoveTrafficConfig -> repositories.trafficConfigs.remove(action.configId)
            is SharedApplicationAction.UpdateRouting -> requireNotNull(repositories.routing) {
                "Routing repository is not configured"
            }.update(action.transform)
            is SharedApplicationAction.UpdateResources -> requireNotNull(repositories.resources) {
                "Resource repository is not configured"
            }.update(action.transform)
            is SharedApplicationAction.UpdateRuntime -> repositories.runtime.update(action.transform)
        }
    }

    private suspend fun removeProxyServers(serverIds: Set<Int>) {
        if (serverIds.isEmpty()) return
        repositories.proxyServers.updateCatalog { current ->
            deleteProxyServerRecords(current, serverIds)
        }
    }
}
