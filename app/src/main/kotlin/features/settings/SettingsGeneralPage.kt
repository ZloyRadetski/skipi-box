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
import app.skipi.ui.settings.GeneralSettingsState
import app.skipi.ui.settings.SkipiGeneralSettingsScreen

/** Android persistence and route adapter for the shared general-settings form. */
@Composable
fun SettingsGeneralPage(padding: PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current

    SkipiGeneralSettingsScreen(
        state = appState.toGeneralSettingsState(),
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onSettingsChange = { transform ->
            updateAppState { current ->
                current.withGeneralSettings(transform(current.toGeneralSettingsState()))
            }
        },
        onBack = { navigator.pop() },
        onOpenUrlSchemes = { navigator.push(Route.SkipiUrlSchemes) },
        onOpenBackupReset = { navigator.push(Route.SettingsBackupReset) },
    )
}

private fun app.AppState.toGeneralSettingsState() = GeneralSettingsState(
    languageMode = languageMode,
    enableHaptics = enableHaptics,
    enableTrafficStatsNotification = enableTrafficStatsNotification,
    trafficStatsNotificationRefreshIntervalSeconds = trafficStatsNotificationRefreshIntervalSeconds,
    enableResourceFileNotifications = enableResourceFileNotifications,
    enableDeletionConfirmation = enableDeletionConfirmation,
    enableBroadcastControl = enableBroadcastControl,
)

private fun app.AppState.withGeneralSettings(settings: GeneralSettingsState) = copy(
    languageMode = settings.languageMode,
    enableHaptics = settings.enableHaptics,
    enableTrafficStatsNotification = settings.enableTrafficStatsNotification,
    trafficStatsNotificationRefreshIntervalSeconds = settings.trafficStatsNotificationRefreshIntervalSeconds,
    enableResourceFileNotifications = settings.enableResourceFileNotifications,
    enableDeletionConfirmation = settings.enableDeletionConfirmation,
    enableBroadcastControl = settings.enableBroadcastControl,
)
