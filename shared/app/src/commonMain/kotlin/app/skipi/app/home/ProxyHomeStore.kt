// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import app.skipi.app.store.FeatureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.TunnelPhase
import platform.TunnelSnapshot

enum class ProxyConnectionPhase {
    Disconnected,
    Connecting,
    Connected,
}

data class ProxyGroupSummary(
    val id: String,
    val title: String,
    val serverCount: Int,
    val enabled: Boolean,
)

data class ProxyServerSummary(
    val id: String,
    val title: String,
    val address: String,
    val protocol: String,
    val transport: String? = null,
    val flag: String? = null,
    val selected: Boolean = false,
    val latencyMs: Long? = null,
    val latencyTesting: Boolean = false,
    val latencyError: Boolean = false,
    val canTest: Boolean = true,
)

data class ProxySubscriptionSummary(
    val id: String,
    val title: String,
    val serverCount: Int,
    val enabled: Boolean,
    val refreshing: Boolean,
    val updateIntervalHours: String? = null,
    val usedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val expireAtSeconds: Long? = null,
    val description: String? = null,
    val announcement: String? = null,
    val announcementUrl: String? = null,
    val supportUrl: String? = null,
    val siteUrl: String? = null,
    val lastUpdatedAtMillis: Long? = null,
)

data class ProxyHomeUiState(
    val tunnelSnapshot: TunnelSnapshot = TunnelSnapshot(),
    val selectedServerTitle: String = "",
    val activeProfileName: String? = null,
    val canToggleTunnel: Boolean = true,
    val groups: List<ProxyGroupSummary> = emptyList(),
    val selectedGroupId: String? = null,
    val subscription: ProxySubscriptionSummary? = null,
    val servers: List<ProxyServerSummary> = emptyList(),
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val isTestingLatency: Boolean = false,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false,
) {
    /** Compatibility projection for existing UI components. */
    val connectionPhase: ProxyConnectionPhase
        get() = tunnelSnapshot.phase.toProxyConnectionPhase()

    val isRunning: Boolean get() = tunnelSnapshot.phase == TunnelPhase.Connected
    val isConnecting: Boolean get() = tunnelSnapshot.phase == TunnelPhase.Connecting
}

private fun TunnelPhase.toProxyConnectionPhase(): ProxyConnectionPhase = when (this) {
    TunnelPhase.Connected -> ProxyConnectionPhase.Connected
    TunnelPhase.Connecting,
    TunnelPhase.Disconnecting -> ProxyConnectionPhase.Connecting
    TunnelPhase.Disconnected,
    TunnelPhase.Failed -> ProxyConnectionPhase.Disconnected
}

sealed interface ProxyHomeAction {
    data object ToggleTunnel : ProxyHomeAction
    data class SelectServer(val serverId: String) : ProxyHomeAction
    data class TestServer(val serverId: String) : ProxyHomeAction
    data object TestAllVisibleServers : ProxyHomeAction
    data class SelectGroup(val groupId: String) : ProxyHomeAction
    data class SetSearchQuery(val query: String) : ProxyHomeAction
    data class SetSearchVisible(val visible: Boolean) : ProxyHomeAction
    data class RefreshSubscription(val subscriptionId: String) : ProxyHomeAction
    data class ToggleSubscriptionEnabled(val subscriptionId: String) : ProxyHomeAction
    data class DismissMessage(val message: String? = null) : ProxyHomeAction
}

class ProxyHomeStore(
    initialState: ProxyHomeUiState = ProxyHomeUiState(),
) : FeatureStore<ProxyHomeUiState, ProxyHomeAction> {
    private val _uiState = MutableStateFlow(initialState)
    override val state: StateFlow<ProxyHomeUiState> = _uiState.asStateFlow()
    val uiState: StateFlow<ProxyHomeUiState> get() = state

    fun updateState(transform: (ProxyHomeUiState) -> ProxyHomeUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun dispatch(action: ProxyHomeAction) {
        when (action) {
            is ProxyHomeAction.ToggleTunnel -> {
                val nextPhase = when (_uiState.value.tunnelSnapshot.phase) {
                    TunnelPhase.Connected,
                    TunnelPhase.Connecting,
                    TunnelPhase.Disconnecting -> TunnelPhase.Disconnected
                    TunnelPhase.Disconnected,
                    TunnelPhase.Failed -> TunnelPhase.Connecting
                }
                _uiState.value = _uiState.value.copy(
                    tunnelSnapshot = _uiState.value.tunnelSnapshot.copy(phase = nextPhase),
                )
            }
            is ProxyHomeAction.SelectServer -> {
                _uiState.value = _uiState.value.copy(
                    servers = _uiState.value.servers.map { server ->
                        server.copy(selected = server.id == action.serverId)
                    },
                    selectedServerTitle = _uiState.value.servers.firstOrNull { it.id == action.serverId }?.title.orEmpty(),
                )
            }
            is ProxyHomeAction.SelectGroup -> {
                _uiState.value = _uiState.value.copy(selectedGroupId = action.groupId)
            }
            is ProxyHomeAction.SetSearchQuery -> {
                _uiState.value = _uiState.value.copy(searchQuery = action.query)
            }
            is ProxyHomeAction.SetSearchVisible -> {
                _uiState.value = _uiState.value.copy(isSearchVisible = action.visible)
            }
            is ProxyHomeAction.DismissMessage -> {
                _uiState.value = _uiState.value.copy(statusMessage = null, isStatusError = false)
            }
            else -> {
                // Handled or extended by platform effect handlers
            }
        }
    }
}
