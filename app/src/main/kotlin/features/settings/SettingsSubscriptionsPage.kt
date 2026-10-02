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
import app.skipi.ui.settings.SubscriptionSettingsLabels
import app.skipi.ui.settings.SubscriptionSettingsState

@Composable
fun SettingsSubscriptionsPage(padding: PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val timeoutValues = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    val secondsUnit = stringResource(R.string.unit_seconds_short)
    val labels = SubscriptionSettingsLabels(
        screenTitle = stringResource(R.string.settings_category_subscriptions),
        generalSectionTitle = stringResource(R.string.settings_header_subscriptions),
        fetchTimeoutTitle = stringResource(R.string.settings_subscription_fetch_timeout),
        fetchTimeoutSummary = stringResource(R.string.settings_subscription_fetch_timeout_summary),
        timeoutOptions = timeoutValues.map { "$it $secondsUnit" },
        userAgentsTitle = stringResource(R.string.settings_user_agents),
        userAgentsSummary = stringResource(R.string.settings_user_agents_summary, appState.subscriptionUserAgents.size),
        deviceHeadersTitle = stringResource(R.string.settings_subscription_device_headers),
        deviceHeadersSummary = stringResource(R.string.settings_subscription_device_headers_summary),
        deletionConfirmationTitle = stringResource(R.string.settings_deletion_confirmation),
        deletionConfirmationSummary = stringResource(R.string.settings_deletion_confirmation_summary),
        expirySectionTitle = stringResource(R.string.subscription_expiry_notifications_section),
        expiryEnabledTitle = stringResource(R.string.subscription_expiry_notifications_enable),
        expiryEnabledSummary = stringResource(R.string.subscription_expiry_notifications_enable_summary),
        expiryRemindersTitle = stringResource(R.string.subscription_expiry_reminders_title),
        expiryRemindersSummary = stringResource(R.string.subscription_expiry_reminders_summary),
        pingSectionTitle = stringResource(R.string.subscription_ping_settings),
        pingTitle = stringResource(R.string.subscription_ping_settings),
        disableHeadersDialogTitle = stringResource(R.string.settings_subscription_device_headers_disable_title),
        disableHeadersDialogSummary = stringResource(R.string.settings_subscription_device_headers_disable_summary),
        cancelText = stringResource(R.string.common_cancel),
        disableHeadersAction = stringResource(R.string.settings_subscription_device_headers_disable_action),
    )

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
