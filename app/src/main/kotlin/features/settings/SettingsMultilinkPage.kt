// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.collectAppState
import app.skipi.ui.settings.MultilinkSettingsState
import app.skipi.ui.settings.SkipiMultilinkSettingsScreen
import app.skipi.ui.settings.rememberMultilinkCoreStatus
import engine.vpn.AndroidMultilinkCore

/**
 * Android adapter for the shared link merging page. The tunnel service applies the settings to Core while a tunnel
 * runs (see AndroidMultilink); the page saves them and shows what Core reports.
 */
@Composable
fun SettingsMultilinkPage(padding: PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val status by rememberMultilinkCoreStatus(
        multilink = AndroidMultilinkCore.reader,
        enabled = appState.multilink.enabled,
        tunnelRunning = appState.proxyRunning,
    )
    SkipiMultilinkSettingsScreen(
        state = MultilinkSettingsState(
            settings = appState.multilink,
            status = status,
            tunnelRunning = appState.proxyRunning,
        ),
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onBack = LocalNavigator.current::pop,
        onSettingsChange = { multilink -> updateAppState { it.copy(multilink = multilink) } },
    )
}
