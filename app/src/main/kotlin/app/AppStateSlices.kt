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
import data.AndroidAppStateStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val LocalAppStateStore = staticCompositionLocalOf<AndroidAppStateStore> {
    error("No AndroidAppStateStore provided!")
}

val LocalUpdateAppState = staticCompositionLocalOf<((AppState) -> AppState) -> Unit> {
    error("No AppState updater provided!")
}

val LocalAppChromeState = compositionLocalOf<AppChromeState> {
    error("No AppChromeState provided!")
}

@Composable
fun AndroidAppStateStore.collectAppState(): State<AppState> {
    return state.collectAsState()
}

@Composable
fun AndroidAppStateStore.collectAppChromeState(): State<AppChromeState> {
    val appState = state.collectAsState()
    return remember {
        derivedStateOf { appState.value.toAppChromeState() }
    }
}

@Composable
fun AndroidAppStateStore.collectProxyServerListState(): State<ProxyServerListState> {
    val appState = state.collectAsState()
    return remember {
        derivedStateOf { appState.value.toProxyServerListState() }
    }
}

@Composable
fun AndroidAppStateStore.collectProxyServerLatency(
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
        enableMaterialYou = enableMaterialYou,
        seedIndex = seedIndex,
        customMaterialYouSeed = customMaterialYouSeed,
        enableCustomColors = enableCustomColors,
        customAccentColor = customAccentColor,
        customBackgroundColor = customBackgroundColor,
        customSurfaceColor = customSurfaceColor,
        customSurfaceVariantColor = customSurfaceVariantColor,
        customTextColor = customTextColor,
        customTextSecondaryColor = customTextSecondaryColor,
        customStatusRunningColor = customStatusRunningColor,
        customStatusStoppedColor = customStatusStoppedColor,
        customPingFastColor = customPingFastColor,
        customPingMediumColor = customPingMediumColor,
        customPingSlowColor = customPingSlowColor,
        customCategoryAppearanceColor = customCategoryAppearanceColor,
        customCategoryVpnColor = customCategoryVpnColor,
        customCategoryProxyColor = customCategoryProxyColor,
        customCategorySubscriptionsColor = customCategorySubscriptionsColor,
        customCategoryIntegrationColor = customCategoryIntegrationColor,
        customCategoryLogsColor = customCategoryLogsColor,
        customCategoryBackupColor = customCategoryBackupColor,
        customCategoryAboutColor = customCategoryAboutColor,
        customProtocolVlessColor = customProtocolVlessColor,
        customProtocolVmessColor = customProtocolVmessColor,
        customProtocolHysteria2Color = customProtocolHysteria2Color,
        customProtocolTrojanColor = customProtocolTrojanColor,
        customProtocolShadowsocksColor = customProtocolShadowsocksColor,
        customProtocolWireguardColor = customProtocolWireguardColor,
        customProtocolSocksColor = customProtocolSocksColor,
        customProtocolHttpColor = customProtocolHttpColor,
        customProtocolStrategyColor = customProtocolStrategyColor,
        customProtocolChainColor = customProtocolChainColor,
        customProtocolJsonColor = customProtocolJsonColor,
        backgroundStyle = backgroundStyle,
        backgroundPhotoDimPercent = backgroundPhotoDimPercent,
        bottomBarSize = bottomBarSize,
    )
}

internal fun AppState.toProxyServerListState(): ProxyServerListState {
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
        showServerSearch = showServerSearch,
        connectionDisplayMode = connectionDisplayMode,
        classicShowFloatingPowerButton = classicShowFloatingPowerButton,
        showTunnelMemoryOnHome = showTunnelMemoryOnHome,
        availableAppUpdate = availableAppUpdate,
        dismissedUpdateVersion = dismissedUpdateVersion,
        activeTrafficConfigId = activeTrafficConfigId,
    )
}
