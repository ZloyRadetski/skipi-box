// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.ProxyServerState
import app.AppState
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.repository.ProxyServerRepository
import data.AndroidAppStateStore
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Bridges the shared proxy catalog to the existing Room backed AppState format. */
class AndroidProxyServerRepository(
    private val stateStore: AndroidAppStateStore,
    scope: CoroutineScope,
) : ProxyServerRepository {
    override val servers: StateFlow<List<ProxyServerRecord>> = stateStore.state
        .map { state -> state.proxyServers.map(ProxyServerState::toRecord) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = stateStore.currentState.proxyServers.map(ProxyServerState::toRecord),
        )

    override suspend fun select(serverId: Int) {
        updateCatalog { current ->
            require(current.servers.any { it.id == serverId }) { "Unknown proxy server ID: $serverId" }
            current.copy(selectedServerId = serverId)
        }
    }

    override suspend fun upsert(server: ProxyServerRecord) {
        updateCollection { current ->
            val index = current.indexOfFirst { it.id == server.id }
            if (index < 0) current + server else current.toMutableList().also { it[index] = server }
        }
    }

    override suspend fun remove(serverId: Int) {
        updateCollection { current -> current.filterNot { it.id == serverId } }
    }

    override suspend fun updateCollection(transform: (List<ProxyServerRecord>) -> List<ProxyServerRecord>) {
        updateCatalog { current -> current.copy(servers = transform(current.servers)) }
    }

    override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
        stateStore.update { state ->
            state.withProxyServerCatalog(transform(state.toProxyServerCatalog()))
        }
    }
}

internal fun AppState.toProxyServerCatalog(): ProxyServerCatalog = ProxyServerCatalog(
    servers = proxyServers.map(ProxyServerState::toRecord),
    nextServerId = nextProxyServerId,
    selectedServerId = selectedProxyServerId,
)

internal fun AppState.withProxyServerCatalog(catalog: ProxyServerCatalog): AppState {
    require(catalog.servers.all { it.id > 0 }) { "Proxy server IDs must be positive" }
    require(catalog.servers.map { it.id }.distinct().size == catalog.servers.size) {
        "Proxy server IDs must be unique"
    }
    require(catalog.nextServerId > 0) { "Next proxy server ID must be positive" }
    val previousById = proxyServers.associateBy { it.id }
    val nextServers = catalog.servers.map { record ->
        record.toAndroidState().copy(latency = previousById[record.id]?.latency.orEmpty())
    }
    val nextServerId = maxOf(
        nextProxyServerId,
        catalog.nextServerId,
        (nextServers.maxOfOrNull { it.id } ?: 0).saturatedIncrement(),
    )
    val selectedId = if (nextServers.any { it.id == catalog.selectedServerId }) {
        catalog.selectedServerId
    } else {
        nextServers.firstOrNull()?.id ?: catalog.selectedServerId
    }
    return copy(
        proxyServers = nextServers,
        nextProxyServerId = nextServerId,
        selectedProxyServerId = selectedId,
    )
}

internal fun ProxyServerState.toRecord(): ProxyServerRecord = ProxyServerRecord(
    id = id,
    server = server,
    sourceSubscriptionId = groupId.takeIf { it != DefaultSubscriptionGroupId },
)

internal fun ProxyServerRecord.toAndroidState(
    groupId: Int = sourceSubscriptionId ?: DefaultSubscriptionGroupId,
): ProxyServerState = ProxyServerState(
    id = id,
    server = server,
    groupId = groupId,
)

private fun Int.saturatedIncrement(): Int = if (this == Int.MAX_VALUE) Int.MAX_VALUE else this + 1
