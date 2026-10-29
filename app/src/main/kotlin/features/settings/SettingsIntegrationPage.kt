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
import app.navigation.Route
import app.skipi.ui.settings.rememberIntegrationSettingsLabels
import app.skipi.ui.settings.IntegrationSettingsState
import app.skipi.ui.settings.SkipiIntegrationSettingsScreen

@Composable
fun SettingsIntegrationPage(padding: PaddingValues) {
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current

    SkipiIntegrationSettingsScreen(
        state = IntegrationSettingsState(broadcastControlEnabled = appState.enableBroadcastControl),
        labels = rememberIntegrationSettingsLabels(),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onOpenUrlSchemes = { navigator.push(Route.SkipiUrlSchemes) },
        onBroadcastControlChange = { enabled ->
            updateAppState { it.copy(enableBroadcastControl = enabled) }
        },
    )
}
