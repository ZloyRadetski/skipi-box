// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.ProxyServerState
import app.AppState
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.store.AppStateStore
import data.AndroidAppStateStore
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Bridges the shared proxy catalog to the existing Room backed AppState format. */
class AndroidProxyServerRepository(
    private val stateStore: AppStateStore,
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
