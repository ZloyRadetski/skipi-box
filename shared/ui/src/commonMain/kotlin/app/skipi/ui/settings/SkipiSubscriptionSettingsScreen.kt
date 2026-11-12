// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.Icons
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.settings_category_subscriptions
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
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppSlider
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
    val expiryNotificationsEnabled: Boolean,
    val expiryReminders: List<SubscriptionExpiryReminder>,
    val pingSummary: String,
)

data class SubscriptionSettingsLabels(
    val screenTitle: String,
    val generalSectionTitle: String,
    val fetchTimeoutTitle: String,
    val fetchTimeoutSummary: String,
    val userAgentsTitle: String,
    val userAgentsSummary: String,
    val deviceHeadersTitle: String,
    val deviceHeadersSummary: String,
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
    onExpiryNotificationsChange: (Boolean) -> Unit,
    onExpiryRemindersChange: (List<SubscriptionExpiryReminder>) -> Unit,
    onOpenPingSettings: () -> Unit,
    capabilities: SubscriptionSettingsCapabilities = SubscriptionSettingsCapabilities(),
    platformContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    var showDisableHeadersConfirmation by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

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
                modifier = Modifier.fadeUnderTopBar(contentPadding.calculateTopPadding()).fillMaxSize(),
                contentPadding = contentPadding.withTopBarGap(),
            ) {
                item(key = "subscriptions_general") {
                    SmallTitle(text = labels.generalSectionTitle)
                    SkipiSettingsSectionCard {
                        if (capabilities.fetchTimeout) {
                            FetchTimeoutSlider(
                                title = labels.fetchTimeoutTitle,
                                summary = labels.fetchTimeoutSummary,
                                seconds = state.fetchTimeoutSeconds,
                                onSecondsChange = onFetchTimeoutChange,
                            )
                        }
                        if (capabilities.userAgents) {
                            ArrowPreference(
                                title = labels.userAgentsTitle,
                                startAction = settingIcon(Icons.Filled.Fingerprint),
                                summary = labels.userAgentsSummary,
                                onClick = onOpenUserAgents,
                            )
                        }
                        if (capabilities.deviceHeaders) {
                            SwitchPreference(
                                title = labels.deviceHeadersTitle,
                                startAction = settingIcon(Icons.Filled.PhoneAndroid),
                                summary = labels.deviceHeadersSummary,
                                checked = state.deviceHeadersEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) onDeviceHeadersChange(true) else showDisableHeadersConfirmation = true
                                },
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
                            startAction = settingIcon(Icons.Filled.NetworkPing),
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
        userAgentsTitle = stringResource(Res.string.settings_user_agents),
        userAgentsSummary = stringResource(Res.string.settings_user_agents_summary, userAgentCount),
        deviceHeadersTitle = stringResource(Res.string.settings_subscription_device_headers),
        deviceHeadersSummary = stringResource(Res.string.settings_subscription_device_headers_summary),
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

/** The range of the subscription fetch timeout, in seconds. */
internal object SubscriptionFetchTimeout {
    const val Min = 1
    const val Max = 60

    /** A value saved before the range was narrowed (up to 120 s) is shown, and next saved, inside it. */
    fun normalize(seconds: Int): Int = seconds.coerceIn(Min, Max)
}

/** A title and summary with the chosen seconds beside them and a one-second-step slider under them. */
@Composable
private fun FetchTimeoutSlider(
    title: String,
    summary: String,
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
) {
    val stored = SubscriptionFetchTimeout.normalize(seconds)
    var sliderValue by androidx.compose.runtime.remember(stored) { androidx.compose.runtime.mutableFloatStateOf(stored.toFloat()) }
    val shown = SubscriptionFetchTimeout.normalize(kotlin.math.round(sliderValue).toInt())
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$shown ${stringResource(Res.string.unit_seconds_short)}",
                fontSize = 14.sp,
                fontWeight = themedFontWeight(FontWeight.Bold),
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
            )
        }
        Text(
            text = summary,
            fontSize = 13.sp,
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        AppSlider(
            value = sliderValue,
            onValueChange = { value -> sliderValue = value },
            onValueChangeFinished = { onSecondsChange(SubscriptionFetchTimeout.normalize(kotlin.math.round(sliderValue).toInt())) },
            valueRange = SubscriptionFetchTimeout.Min.toFloat()..SubscriptionFetchTimeout.Max.toFloat(),
            steps = SubscriptionFetchTimeout.Max - SubscriptionFetchTimeout.Min - 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
