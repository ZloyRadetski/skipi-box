// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.ProxyServerState
import app.skipi.app.model.ProxyServerRecord
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
        stateStore.update { state ->
            require(state.proxyServers.any { it.id == serverId }) { "Unknown proxy server ID: $serverId" }
            if (state.selectedProxyServerId == serverId) state else state.copy(selectedProxyServerId = serverId)
        }
    }

    override suspend fun upsert(server: ProxyServerRecord) {
        stateStore.update { state ->
            val previous = state.proxyServers.firstOrNull { it.id == server.id }
            val nextServers = if (previous == null) {
                state.proxyServers + server.toAndroidState()
            } else {
                state.proxyServers.map { current ->
                    if (current.id == server.id) {
                        server.toAndroidState().copy(latency = current.latency)
                    } else {
                        current
                    }
                }
            }
            state.copy(
                proxyServers = nextServers,
                nextProxyServerId = maxOf(state.nextProxyServerId, server.id + 1),
            )
        }
    }

    override suspend fun remove(serverId: Int) {
        stateStore.update { state ->
            val nextServers = state.proxyServers.filterNot { it.id == serverId }
            if (nextServers.size == state.proxyServers.size) {
                state
            } else {
                state.copy(
                    proxyServers = nextServers,
                    selectedProxyServerId = if (state.selectedProxyServerId == serverId) {
                        nextServers.firstOrNull()?.id ?: state.selectedProxyServerId
                    } else {
                        state.selectedProxyServerId
                    },
                )
            }
        }
    }
}

private fun ProxyServerState.toRecord(): ProxyServerRecord = ProxyServerRecord(
    id = id,
    server = server,
    sourceSubscriptionId = groupId.takeIf { it != DefaultSubscriptionGroupId },
)

private fun ProxyServerRecord.toAndroidState(
    groupId: Int = sourceSubscriptionId ?: DefaultSubscriptionGroupId,
): ProxyServerState = ProxyServerState(
    id = id,
    server = server,
    groupId = groupId,
)
