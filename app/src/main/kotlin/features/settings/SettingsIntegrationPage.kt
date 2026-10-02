// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.skipi.ui.settings.IntegrationSettingsLabels
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
        labels = IntegrationSettingsLabels(
            screenTitle = stringResource(R.string.settings_category_integration),
            sectionTitle = stringResource(R.string.settings_header_integration),
            urlSchemesTitle = stringResource(R.string.settings_url_schemes),
            urlSchemesSummary = stringResource(R.string.settings_url_schemes_summary),
            broadcastControlTitle = stringResource(R.string.settings_broadcast_control),
            broadcastControlSummary = stringResource(R.string.settings_broadcast_control_summary),
        ),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onOpenUrlSchemes = { navigator.push(Route.SkipiUrlSchemes) },
        onBroadcastControlChange = { enabled ->
            updateAppState { it.copy(enableBroadcastControl = enabled) }
        },
    )
}
