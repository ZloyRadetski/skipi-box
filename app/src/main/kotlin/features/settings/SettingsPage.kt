// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

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
import app.skipi.ui.settings.SkipiSettingsDestination
import app.skipi.ui.settings.SkipiSettingsHomeScreen
import app.skipi.ui.settings.SkipiSettingsHomeState
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val categoryIconColor = appState.customCategoryIconColor?.let(::Color) ?: MiuixTheme.colorScheme.primary
    SkipiSettingsHomeScreen(
        state = SkipiSettingsHomeState(
            versionLabel = versionLabel,
            tunMtu = appState.tunMtu,
            localProxyPort = appState.localProxyPort,
            coreLogLevel = SettingsLogLevelOptions.getOrNull(appState.coreLogLevel)?.uppercase(),
            categoryIconColor = categoryIconColor,
        ),
        padding = padding,
        onNavigate = { destination ->
            val route = when (destination) {
                SkipiSettingsDestination.Appearance -> Route.SettingsAppearance
                SkipiSettingsDestination.Vpn -> Route.SettingsVpn
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
    )
}
