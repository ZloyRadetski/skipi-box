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
import app.skipi.ui.settings.SkipiSubscriptionSettingsScreen
import app.skipi.ui.settings.rememberSubscriptionSettingsLabels
import app.skipi.ui.settings.SubscriptionSettingsState

@Composable
fun SettingsSubscriptionsPage(padding: PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val timeoutValues = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    val secondsUnit = stringResource(R.string.unit_seconds_short)
    val labels = rememberSubscriptionSettingsLabels(appState.subscriptionUserAgents.size)

    SkipiSubscriptionSettingsScreen(
        state = SubscriptionSettingsState(
            fetchTimeoutSeconds = appState.subscriptionFetchTimeoutSeconds,
            deviceHeadersEnabled = appState.enableSubscriptionDeviceHeaders,
            deletionConfirmationEnabled = appState.enableDeletionConfirmation,
            expiryNotificationsEnabled = appState.enableSubscriptionExpiryNotifications,
            expiryReminders = appState.subscriptionExpiryReminders,
            pingSummary = subscriptionPingSettingsSummary(
                url = appState.subscriptionPingUrl,
                timeoutMillis = appState.subscriptionPingTimeoutMillis,
            ),
        ),
        labels = labels,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onFetchTimeoutChange = { seconds -> updateAppState { it.copy(subscriptionFetchTimeoutSeconds = seconds) } },
        onOpenUserAgents = { navigator.push(Route.SubscriptionUserAgents) },
        onDeviceHeadersChange = { enabled -> updateAppState { it.copy(enableSubscriptionDeviceHeaders = enabled) } },
        onDeletionConfirmationChange = { enabled -> updateAppState { it.copy(enableDeletionConfirmation = enabled) } },
        onExpiryNotificationsChange = { enabled -> updateAppState { it.copy(enableSubscriptionExpiryNotifications = enabled) } },
        onExpiryRemindersChange = { reminders -> updateAppState { it.copy(subscriptionExpiryReminders = reminders) } },
        onOpenPingSettings = { navigator.push(Route.SubscriptionPingSettings) },
    )
}
