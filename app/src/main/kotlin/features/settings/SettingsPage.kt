// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.skipi.ui.settings.search.SettingsSearchPage
import app.LocalIsWideScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import app.LocalAppStateStore
import app.LocalNavigator
import app.LocalUpdateAppState
import app.ProjectInfo
import app.collectAppState
import app.modes.RunModeVpnService
import app.navigation.Route
import app.skipi.ui.settings.AboutSettingsCapabilities
import app.skipi.ui.settings.search.SettingsSearchHost
import features.updater.SelfUpdateEnabled
import app.skipi.ui.settings.SkipiSettingsDestination
import app.skipi.ui.settings.SkipiSettingsHomeScreen
import app.skipi.ui.settings.SkipiSettingsHomeState

/** Android application adapter for the shared settings overview. */
@Composable
fun SettingsPage(
    padding: PaddingValues,
) {
    val appStateStore = LocalAppStateStore.current
    val appState by appStateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current

    LaunchedEffect(appState.runMode) {
        if (appState.runMode != RunModeVpnService) {
            updateAppState { state -> state.copy(runMode = RunModeVpnService, proxyRunning = false) }
        }
    }

    val versionLabel = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})"
    SkipiSettingsHomeScreen(
        state = SkipiSettingsHomeState(
            versionLabel = versionLabel,
            tunMtu = appState.tunMtu,
            localProxyPort = appState.localProxyPort,
            coreLogLevel = SettingsLogLevelOptions.getOrNull(appState.coreLogLevel)?.uppercase(),
            categoryIconGlyphColor = app.skipi.ui.theme.customColorIfEnabled(appState.customCategoryIconGlyphColor, appState.enableCustomColors)?.let(::Color),
            searchHost = SettingsSearchHost(about = AboutSettingsCapabilities(updates = SelfUpdateEnabled)),
        ),
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onNavigate = { destination ->
            val route = when (destination) {
                SkipiSettingsDestination.Appearance -> Route.SettingsAppearance
                SkipiSettingsDestination.Vpn -> Route.SettingsVpn
                SkipiSettingsDestination.NetworkAutomation -> Route.SettingsNetworkAutomation
                SkipiSettingsDestination.LocalProxy -> Route.LocalProxySettings
                SkipiSettingsDestination.Subscriptions -> Route.SettingsSubscriptions
                SkipiSettingsDestination.General -> Route.SettingsGeneral
                SkipiSettingsDestination.BackupReset -> Route.SettingsBackupReset
                SkipiSettingsDestination.SpeedTest -> Route.SpeedTest
                SkipiSettingsDestination.DnsLeakTest -> Route.DnsLeakTest
                SkipiSettingsDestination.IpInfo -> Route.IpInfo
                SkipiSettingsDestination.Logs -> Route.SettingsLogs
                SkipiSettingsDestination.About -> Route.About
            }
            navigator.push(route)
        },
        onOpenSearchResult = { page -> navigator.push(page.route()) },
    )
}

private fun SettingsSearchPage.route(): Route = when (this) {
    SettingsSearchPage.Appearance -> Route.SettingsAppearance
    SettingsSearchPage.Vpn -> Route.SettingsVpn
    SettingsSearchPage.NetworkAutomation -> Route.SettingsNetworkAutomation
    SettingsSearchPage.LocalProxy -> Route.LocalProxySettings
    SettingsSearchPage.Subscriptions -> Route.SettingsSubscriptions
    SettingsSearchPage.SubscriptionPing -> Route.SubscriptionPingSettings
    SettingsSearchPage.General -> Route.SettingsGeneral
    SettingsSearchPage.BackupReset -> Route.SettingsBackupReset
    SettingsSearchPage.SpeedTest -> Route.SpeedTest
    SettingsSearchPage.DnsLeakTest -> Route.DnsLeakTest
    SettingsSearchPage.IpInfo -> Route.IpInfo
    SettingsSearchPage.Logs -> Route.SettingsLogs
    SettingsSearchPage.About -> Route.About
}
