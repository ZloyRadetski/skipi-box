// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import data.AppStateStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AppChromeState(
    val colorMode: Int,
    val languageMode: Int,
    val seedIndex: Int,
)

data class ProxyServerListState(
    val subscriptionGroups: List<SubscriptionGroupState>,
    val enableAllProxyGroup: Boolean,
    val enableDeletionConfirmation: Boolean,
    val proxyServers: List<ProxyServerState>,
    val nextProxyServerId: Int,
    val selectedProxyServerId: Int,
    val proxyServerListLayout: Int,
    val proxyServerListSort: Int,
    val subscriptionPingMode: Int,
    val proxyRunning: Boolean,
    val showTunnelMemoryOnHome: Boolean,
)

val LocalAppStateStore = staticCompositionLocalOf<AppStateStore> {
    error("No AppStateStore provided!")
}

val LocalUpdateAppState = staticCompositionLocalOf<((AppState) -> AppState) -> Unit> {
    error("No AppState updater provided!")
}

val LocalAppChromeState = compositionLocalOf<AppChromeState> {
    error("No AppChromeState provided!")
}

@Composable
fun AppStateStore.collectAppState(): State<AppState> {
    return state.collectAsState()
}

@Composable
fun AppStateStore.collectAppChromeState(): State<AppChromeState> {
    val appState = state.collectAsState()
    return remember {
        derivedStateOf { appState.value.toAppChromeState() }
    }
}

@Composable
fun AppStateStore.collectProxyServerListState(): State<ProxyServerListState> {
    val appState = state.collectAsState()
    return remember {
        derivedStateOf { appState.value.toProxyServerListState() }
    }
}

@Composable
fun AppStateStore.collectProxyServerLatency(
    serverId: Int,
    initialLatency: String = "",
): State<String> {
    val latencyFlow = remember(this, serverId) {
        state
            .map { appState: AppState -> appState.proxyServers }
            .distinctUntilChanged()
            .map { servers: List<ProxyServerState> ->
                servers.firstOrNull { server -> server.id == serverId }?.latency.orEmpty()
            }
            .distinctUntilChanged()
    }
    return latencyFlow.collectAsState(initial = initialLatency)
}

private fun AppState.toAppChromeState(): AppChromeState {
    return AppChromeState(
        colorMode = colorMode,
        languageMode = languageMode,
        seedIndex = seedIndex,
    )
}

private fun AppState.toProxyServerListState(): ProxyServerListState {
    return ProxyServerListState(
        subscriptionGroups = subscriptionGroups,
        enableAllProxyGroup = enableAllProxyGroup,
        enableDeletionConfirmation = enableDeletionConfirmation,
        proxyServers = proxyServers,
        nextProxyServerId = nextProxyServerId,
        selectedProxyServerId = selectedProxyServerId,
        proxyServerListLayout = proxyServerListLayout,
        proxyServerListSort = proxyServerListSort,
        subscriptionPingMode = subscriptionPingMode,
        proxyRunning = proxyRunning,
        showTunnelMemoryOnHome = showTunnelMemoryOnHome,
    )
}
