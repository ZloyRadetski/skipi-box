// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.settings_category_subscriptions
import app.skipi.ui.resources.settings_deletion_confirmation
import app.skipi.ui.resources.settings_deletion_confirmation_summary
import app.skipi.ui.resources.settings_header_subscriptions
import app.skipi.ui.resources.settings_subscription_device_headers
import app.skipi.ui.resources.settings_subscription_device_headers_disable_action
import app.skipi.ui.resources.settings_subscription_device_headers_disable_summary
import app.skipi.ui.resources.settings_subscription_device_headers_disable_title
import app.skipi.ui.resources.settings_subscription_device_headers_summary
import app.skipi.ui.resources.settings_subscription_fetch_timeout
import app.skipi.ui.resources.settings_subscription_fetch_timeout_summary
import app.skipi.ui.resources.settings_user_agents
import app.skipi.ui.resources.settings_user_agents_summary
import app.skipi.ui.resources.subscription_expiry_notifications_enable
import app.skipi.ui.resources.subscription_expiry_notifications_enable_summary
import app.skipi.ui.resources.subscription_expiry_notifications_section
import app.skipi.ui.resources.subscription_expiry_reminders_summary
import app.skipi.ui.resources.subscription_expiry_reminders_title
import app.skipi.ui.resources.subscription_ping_settings
import app.skipi.ui.resources.unit_seconds_short
import app.skipi.ui.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppOverlayDropdownPreference
import app.skipi.ui.components.WarningConfirmDialog
import app.skipi.ui.subscription.SubscriptionExpiryReminderList
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import features.subscription.SubscriptionExpiryReminder
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SubscriptionSettingsState(
    val fetchTimeoutSeconds: Int,
    val deviceHeadersEnabled: Boolean,
    val deletionConfirmationEnabled: Boolean,
    val expiryNotificationsEnabled: Boolean,
    val expiryReminders: List<SubscriptionExpiryReminder>,
    val pingSummary: String,
)

data class SubscriptionSettingsLabels(
    val screenTitle: String,
    val generalSectionTitle: String,
    val fetchTimeoutTitle: String,
    val fetchTimeoutSummary: String,
    val timeoutOptions: List<String>,
    val userAgentsTitle: String,
    val userAgentsSummary: String,
    val deviceHeadersTitle: String,
    val deviceHeadersSummary: String,
    val deletionConfirmationTitle: String,
    val deletionConfirmationSummary: String,
    val expirySectionTitle: String,
    val expiryEnabledTitle: String,
    val expiryEnabledSummary: String,
    val expiryRemindersTitle: String,
    val expiryRemindersSummary: String,
    val pingSectionTitle: String,
    val pingTitle: String,
    val disableHeadersDialogTitle: String,
    val disableHeadersDialogSummary: String,
    val cancelText: String,
    val disableHeadersAction: String,
)

data class SubscriptionSettingsCapabilities(
    val fetchTimeout: Boolean = true,
    val userAgents: Boolean = true,
    val deviceHeaders: Boolean = true,
    val deletionConfirmation: Boolean = true,
    val expiryNotifications: Boolean = true,
    val pingSettings: Boolean = true,
)

/** Subscription preference UI. Persistence and navigation are provided by the host. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiSubscriptionSettingsScreen(
    state: SubscriptionSettingsState,
    labels: SubscriptionSettingsLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onFetchTimeoutChange: (Int) -> Unit,
    onOpenUserAgents: () -> Unit,
    onDeviceHeadersChange: (Boolean) -> Unit,
    onDeletionConfirmationChange: (Boolean) -> Unit,
    onExpiryNotificationsChange: (Boolean) -> Unit,
    onExpiryRemindersChange: (List<SubscriptionExpiryReminder>) -> Unit,
    onOpenPingSettings: () -> Unit,
    capabilities: SubscriptionSettingsCapabilities = SubscriptionSettingsCapabilities(),
    platformContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    var showDisableHeadersConfirmation by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val timeoutValues = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    val selectedTimeoutIndex = timeoutValues.indexOf(state.fetchTimeoutSeconds).let { index ->
        if (index >= 0) index else timeoutValues.indexOf(30)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(labels.screenTitle) { SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack) }
        },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "subscriptions_general") {
                    SmallTitle(text = labels.generalSectionTitle)
                    SkipiSettingsSectionCard {
                        if (capabilities.fetchTimeout) {
                            AppOverlayDropdownPreference(
                                title = labels.fetchTimeoutTitle,
                                summary = labels.fetchTimeoutSummary,
                                items = labels.timeoutOptions,
                                selectedIndex = selectedTimeoutIndex.coerceIn(labels.timeoutOptions.indices),
                                onSelectedIndexChange = { index -> timeoutValues.getOrNull(index)?.let(onFetchTimeoutChange) },
                            )
                        }
                        if (capabilities.userAgents) {
                            ArrowPreference(
                                title = labels.userAgentsTitle,
                                summary = labels.userAgentsSummary,
                                onClick = onOpenUserAgents,
                            )
                        }
                        if (capabilities.deviceHeaders) {
                            SwitchPreference(
                                title = labels.deviceHeadersTitle,
                                summary = labels.deviceHeadersSummary,
                                checked = state.deviceHeadersEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) onDeviceHeadersChange(true) else showDisableHeadersConfirmation = true
                                },
                            )
                        }
                        if (capabilities.deletionConfirmation) {
                            SwitchPreference(
                                title = labels.deletionConfirmationTitle,
                                summary = labels.deletionConfirmationSummary,
                                checked = state.deletionConfirmationEnabled,
                                onCheckedChange = onDeletionConfirmationChange,
                            )
                        }
                    }
                }
                if (capabilities.expiryNotifications) item(key = "subscriptions_expiry") {
                    SmallTitle(text = labels.expirySectionTitle)
                    SkipiSettingsSectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = labels.expiryEnabledTitle,
                                        style = MiuixTheme.textStyles.body1.copy(fontWeight = themedFontWeight(FontWeight.Medium)),
                                        color = SkipiTheme.colors.onSurface,
                                    )
                                    Text(
                                        text = labels.expiryEnabledSummary,
                                        style = MiuixTheme.textStyles.body2,
                                        color = SkipiTheme.colors.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.expiryNotificationsEnabled,
                                    onCheckedChange = onExpiryNotificationsChange,
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = state.expiryNotificationsEnabled) {
                                Column(Modifier.padding(top = 16.dp)) {
                                    Text(
                                        text = labels.expiryRemindersTitle,
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = themedFontWeight(FontWeight.Medium)),
                                        color = SkipiTheme.colors.onSurface,
                                    )
                                    Text(
                                        text = labels.expiryRemindersSummary,
                                        style = MiuixTheme.textStyles.body2,
                                        color = SkipiTheme.colors.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    SubscriptionExpiryReminderList(
                                        reminders = state.expiryReminders,
                                        onRemindersChange = onExpiryRemindersChange,
                                    )
                                }
                            }
                        }
                    }
                }
                if (capabilities.pingSettings) item(key = "subscriptions_ping") {
                    SmallTitle(text = labels.pingSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = labels.pingTitle,
                            summary = state.pingSummary,
                            onClick = onOpenPingSettings,
                        )
                    }
                }
                platformContent?.let { content -> item(key = "subscriptions_platform_extension") { Column(content = content) } }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
            WarningConfirmDialog(
                show = showDisableHeadersConfirmation,
                title = labels.disableHeadersDialogTitle,
                summary = labels.disableHeadersDialogSummary,
                dismissText = labels.cancelText,
                confirmText = labels.disableHeadersAction,
                onDismissRequest = { showDisableHeadersConfirmation = false },
                onConfirm = {
                    onDeviceHeadersChange(false)
                    showDisableHeadersConfirmation = false
                },
            )
        }
    }
}

/** Localized subscription-settings labels shared by every host. */
@Composable
fun rememberSubscriptionSettingsLabels(userAgentCount: Int): SubscriptionSettingsLabels = SubscriptionSettingsLabels(
        screenTitle = stringResource(Res.string.settings_category_subscriptions),
        generalSectionTitle = stringResource(Res.string.settings_header_subscriptions),
        fetchTimeoutTitle = stringResource(Res.string.settings_subscription_fetch_timeout),
        fetchTimeoutSummary = stringResource(Res.string.settings_subscription_fetch_timeout_summary),
        timeoutOptions = listOf(10, 15, 20, 30, 45, 60, 90, 120).map { "$it ${stringResource(Res.string.unit_seconds_short)}" },
        userAgentsTitle = stringResource(Res.string.settings_user_agents),
        userAgentsSummary = stringResource(Res.string.settings_user_agents_summary, userAgentCount),
        deviceHeadersTitle = stringResource(Res.string.settings_subscription_device_headers),
        deviceHeadersSummary = stringResource(Res.string.settings_subscription_device_headers_summary),
        deletionConfirmationTitle = stringResource(Res.string.settings_deletion_confirmation),
        deletionConfirmationSummary = stringResource(Res.string.settings_deletion_confirmation_summary),
        expirySectionTitle = stringResource(Res.string.subscription_expiry_notifications_section),
        expiryEnabledTitle = stringResource(Res.string.subscription_expiry_notifications_enable),
        expiryEnabledSummary = stringResource(Res.string.subscription_expiry_notifications_enable_summary),
        expiryRemindersTitle = stringResource(Res.string.subscription_expiry_reminders_title),
        expiryRemindersSummary = stringResource(Res.string.subscription_expiry_reminders_summary),
        pingSectionTitle = stringResource(Res.string.subscription_ping_settings),
        pingTitle = stringResource(Res.string.subscription_ping_settings),
        disableHeadersDialogTitle = stringResource(Res.string.settings_subscription_device_headers_disable_title),
        disableHeadersDialogSummary = stringResource(Res.string.settings_subscription_device_headers_disable_summary),
        cancelText = stringResource(Res.string.common_cancel),
        disableHeadersAction = stringResource(Res.string.settings_subscription_device_headers_disable_action),
    )
