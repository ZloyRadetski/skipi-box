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
import app.skipi.app.store.AppStateStore
import data.AndroidAppStateStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AppChromeState(
    val colorMode: Int,
    val languageMode: Int,
    val fontFamilyMode: Int = app.modes.FontFamilyModeUnbounded,
    val fontSizeMode: Int = app.modes.FontSizeModeSmall,
    val fontWeightMode: Int = app.modes.FontWeightModeDefault,
    val enableMaterialYou: Boolean,
    val seedIndex: Int,
    val customMaterialYouSeed: Long? = null,
    val enableCustomColors: Boolean = false,
    val customAccentColor: Long? = null,
    val customBackgroundColor: Long? = null,
    val customSurfaceColor: Long? = null,
    val customSurfaceVariantColor: Long? = null,
    val customTextColor: Long? = null,
    val customTextSecondaryColor: Long? = null,
    val customStatusRunningColor: Long? = null,
    val customStatusStoppedColor: Long? = null,
    val customPingFastColor: Long? = null,
    val customPingMediumColor: Long? = null,
    val customPingSlowColor: Long? = null,
    val customCategoryIconColor: Long? = null,
    val customProtocolVlessColor: Long? = null,
    val customProtocolVmessColor: Long? = null,
    val customProtocolHysteria2Color: Long? = null,
    val customProtocolTrojanColor: Long? = null,
    val customProtocolShadowsocksColor: Long? = null,
    val customProtocolWireguardColor: Long? = null,
    val customProtocolSocksColor: Long? = null,
    val customProtocolHttpColor: Long? = null,
    val customProtocolStrategyColor: Long? = null,
    val customProtocolChainColor: Long? = null,
    val customProtocolJsonColor: Long? = null,
    val backgroundStyle: Int = app.modes.BackgroundStyleClassic,
    val backgroundPhotoDimPercent: Int = 45,
    val bottomBarSize: Int = app.modes.BottomBarSizeLarge,
    val enableHaptics: Boolean = true,
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
    val showServerSearch: Boolean,
    val connectionDisplayMode: Int,
    val pinConnectionPanelOnHome: Boolean,
    val enableSubscriptionSwipe: Boolean,
    val classicShowFloatingPowerButton: Boolean,
    val showTunnelMemoryOnHome: Boolean,
    val availableAppUpdate: features.updater.AppUpdateInfo?,
    val dismissedUpdateVersion: String,
    val activeTrafficConfigId: Int? = null,
    val unappliedRoutingRules: List<String> = emptyList(),
)

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

private fun AppState.toAppChromeState(): AppChromeState {
    return AppChromeState(
        colorMode = colorMode,
        languageMode = languageMode,
        fontFamilyMode = fontFamilyMode,
        fontSizeMode = fontSizeMode,
        fontWeightMode = fontWeightMode,
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
        customCategoryIconColor = customCategoryIconColor,
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
        enableHaptics = enableHaptics,
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
        pinConnectionPanelOnHome = pinConnectionPanelOnHome,
        enableSubscriptionSwipe = enableSubscriptionSwipe,
        classicShowFloatingPowerButton = classicShowFloatingPowerButton,
        showTunnelMemoryOnHome = showTunnelMemoryOnHome,
        availableAppUpdate = availableAppUpdate,
        dismissedUpdateVersion = dismissedUpdateVersion,
        activeTrafficConfigId = activeTrafficConfigId,
        unappliedRoutingRules = unappliedRoutingRules,
    )
}
