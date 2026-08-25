// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.animation.AnimatedVisibility
import ui.components.AppOverlayDropdownPreference
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.R
import app.modes.ConnectionDisplayModeClassic
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun SettingsThemeSection(
    colorModeOptions: List<String>,
    colorMode: Int,
    enableMaterialYou: Boolean,
    keyColorOptions: List<String>,
    seedIndex: Int,
    languageOptions: List<String>,
    languageMode: Int,
    onColorModeChange: (Int) -> Unit,
    onEnableMaterialYouChange: (Boolean) -> Unit,
    onSeedIndexChange: (Int) -> Unit,
    onLanguageModeChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_theme))
    SettingsSectionCard {
        AppOverlayDropdownPreference(
            title = stringResource(R.string.settings_color_mode),
            summary = stringResource(R.string.settings_color_mode_summary),
            items = colorModeOptions,
            selectedIndex = colorMode,
            onSelectedIndexChange = onColorModeChange,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_enable_material_you),
            summary = stringResource(R.string.settings_enable_material_you_summary),
            checked = enableMaterialYou,
            onCheckedChange = onEnableMaterialYouChange,
        )
        AnimatedVisibility(
            visible = enableMaterialYou,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AppOverlayDropdownPreference(
                title = stringResource(R.string.settings_theme_color),
                summary = stringResource(R.string.settings_theme_color_summary),
                items = keyColorOptions,
                selectedIndex = seedIndex,
                onSelectedIndexChange = onSeedIndexChange,
            )
        }
        AppOverlayDropdownPreference(
            title = stringResource(R.string.settings_language),
            summary = stringResource(R.string.settings_language_summary),
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
        )
    }
}

@Composable
internal fun SettingsSubscriptionsSection(
    enableAllProxyGroup: Boolean,
    showServerSearch: Boolean,
    connectionDisplayModeOptions: List<String>,
    connectionDisplayMode: Int,
    classicShowFloatingPowerButton: Boolean,
    enableDeletionConfirmation: Boolean,
    fetchTimeoutOptions: List<String>,
    fetchTimeoutIndex: Int,
    onEnableAllProxyGroupChange: (Boolean) -> Unit,
    onShowServerSearchChange: (Boolean) -> Unit,
    onConnectionDisplayModeChange: (Int) -> Unit,
    onClassicShowFloatingPowerButtonChange: (Boolean) -> Unit,
    onEnableDeletionConfirmationChange: (Boolean) -> Unit,
    onFetchTimeoutChange: (Int) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_general))
    SettingsSectionCard {
        AppOverlayDropdownPreference(
            title = stringResource(R.string.settings_connection_display_mode),
            summary = stringResource(R.string.settings_connection_display_mode_summary),
            items = connectionDisplayModeOptions,
            selectedIndex = connectionDisplayMode,
            onSelectedIndexChange = onConnectionDisplayModeChange,
        )
        AnimatedVisibility(visible = connectionDisplayMode == ConnectionDisplayModeClassic) {
            SwitchPreference(
                title = stringResource(R.string.settings_classic_show_floating_power_button),
                summary = stringResource(R.string.settings_classic_show_floating_power_button_summary),
                checked = classicShowFloatingPowerButton,
                onCheckedChange = onClassicShowFloatingPowerButtonChange,
            )
        }
        SwitchPreference(
            title = stringResource(R.string.settings_enable_all_proxy_group),
            summary = stringResource(R.string.settings_enable_all_proxy_group_summary),
            checked = enableAllProxyGroup,
            onCheckedChange = onEnableAllProxyGroupChange,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_show_server_search),
            summary = stringResource(R.string.settings_show_server_search_summary),
            checked = showServerSearch,
            onCheckedChange = onShowServerSearchChange,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_deletion_confirmation),
            summary = stringResource(R.string.settings_deletion_confirmation_summary),
            checked = enableDeletionConfirmation,
            onCheckedChange = onEnableDeletionConfirmationChange,
        )
        AppOverlayDropdownPreference(
            title = stringResource(R.string.settings_subscription_fetch_timeout),
            summary = stringResource(R.string.settings_subscription_fetch_timeout_summary),
            items = fetchTimeoutOptions,
            selectedIndex = fetchTimeoutIndex,
            onSelectedIndexChange = onFetchTimeoutChange,
        )
    }
}

/** Reusable request headers for subscription downloads. */
@Composable
internal fun SettingsUserAgentsSection(
    userAgents: List<String>,
    onOpenUserAgents: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_user_agents))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_user_agents),
            summary = stringResource(R.string.settings_user_agents_summary, userAgents.size),
            onClick = onOpenUserAgents,
        )
    }
}

/** Privacy control for subscription providers that require a stable device identity. */
@Composable
internal fun SettingsSubscriptionDeviceHeadersSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_subscription_device_headers))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(R.string.settings_subscription_device_headers),
            summary = stringResource(R.string.settings_subscription_device_headers_summary),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

/** External-link reference kept separate from subscription download preferences. */
@Composable
internal fun SettingsUrlSchemesSection(
    onOpenUrlSchemes: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_url_schemes))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_url_schemes),
            summary = stringResource(R.string.settings_url_schemes_summary),
            onClick = onOpenUrlSchemes,
        )
    }
}

/** Subscription health-check preferences are app-wide, not VPN traffic settings. */
@Composable
internal fun SettingsSubscriptionPingSection(
    pingModeOptions: List<String>,
    pingMode: Int,
    summary: String,
    onPingModeChange: (Int) -> Unit,
    onOpenPingSettings: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.subscription_ping_settings))
    SettingsSectionCard {
        AppOverlayDropdownPreference(
            title = stringResource(R.string.subscription_ping_mode),
            summary = stringResource(R.string.subscription_ping_mode_summary),
            items = pingModeOptions,
            selectedIndex = pingMode.coerceIn(pingModeOptions.indices),
            onSelectedIndexChange = onPingModeChange,
        )
        ArrowPreference(
            title = stringResource(R.string.subscription_ping_settings),
            summary = summary,
            onClick = onOpenPingSettings,
        )
    }
}

@Composable
internal fun SettingsCoreSection(
    enableSniffing: Boolean,
    enableSniffingRouteOnly: Boolean,
    muxSettingsSummary: String,
    fragmentSettingsSummary: String,
    coreLogLevel: Int,
    enableAccessLog: Boolean,
    onOpenDnsSettings: () -> Unit,
    onEnableSniffingChange: (Boolean) -> Unit,
    onEnableSniffingRouteOnlyChange: (Boolean) -> Unit,
    onOpenMuxSettings: () -> Unit,
    onOpenFragmentSettings: () -> Unit,
    onCoreLogLevelChange: (Int) -> Unit,
    onEnableAccessLogChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_core))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_dns),
            summary = stringResource(R.string.settings_dns_summary),
            onClick = onOpenDnsSettings,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_sniffing),
            summary = stringResource(R.string.settings_sniffing_summary),
            checked = enableSniffing,
            onCheckedChange = onEnableSniffingChange,
        )
        AnimatedVisibility(
            visible = enableSniffing,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchPreference(
                title = stringResource(R.string.settings_sniffing_route_only),
                summary = stringResource(R.string.settings_sniffing_route_only_summary),
                checked = enableSniffingRouteOnly,
                onCheckedChange = onEnableSniffingRouteOnlyChange,
            )
        }
        ArrowPreference(
            title = stringResource(R.string.settings_mux),
            summary = muxSettingsSummary,
            onClick = onOpenMuxSettings,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_fragment),
            summary = fragmentSettingsSummary,
            onClick = onOpenFragmentSettings,
        )
        AppOverlayDropdownPreference(
            title = stringResource(R.string.settings_log_level),
            items = SettingsLogLevelOptions,
            selectedIndex = coreLogLevel,
            onSelectedIndexChange = onCoreLogLevelChange,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_record_access_log),
            checked = enableAccessLog,
            onCheckedChange = onEnableAccessLogChange,
        )
    }
}

@Composable
internal fun SettingsAdvancedSection(
    enableBroadcastControl: Boolean,
    enableIpv6: Boolean,
    enableIpv6Prefer: Boolean,
    subscriptionPingModeOptions: List<String>,
    subscriptionPingMode: Int,
    subscriptionPingSettingsSummary: String,
    onEnableBroadcastControlChange: (Boolean) -> Unit,
    onEnableIpv6Change: (Boolean) -> Unit,
    onEnableIpv6PreferChange: (Boolean) -> Unit,
    onSubscriptionPingModeChange: (Int) -> Unit,
    onOpenSubscriptionPingSettings: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_advanced))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(R.string.settings_broadcast_control),
            summary = stringResource(R.string.settings_broadcast_control_summary),
            checked = enableBroadcastControl,
            onCheckedChange = onEnableBroadcastControlChange,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_ipv6),
            summary = stringResource(R.string.settings_ipv6_summary),
            checked = enableIpv6,
            onCheckedChange = onEnableIpv6Change,
        )
        AnimatedVisibility(
            visible = enableIpv6,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchPreference(
                title = stringResource(R.string.settings_ipv6_prefer),
                summary = stringResource(R.string.settings_ipv6_prefer_summary),
                checked = enableIpv6Prefer,
                onCheckedChange = onEnableIpv6PreferChange,
            )
        }
        AppOverlayDropdownPreference(
            title = stringResource(R.string.subscription_ping_mode),
            items = subscriptionPingModeOptions,
            selectedIndex = subscriptionPingMode.coerceIn(subscriptionPingModeOptions.indices),
            onSelectedIndexChange = onSubscriptionPingModeChange,
        )
        ArrowPreference(
            title = stringResource(R.string.subscription_ping_settings),
            summary = subscriptionPingSettingsSummary,
            onClick = onOpenSubscriptionPingSettings,
        )
    }
}

@Composable
internal fun SettingsVpnSection(
    tunSettingsSummary: String,
    enableVpnHevTun: Boolean,
    enableVpnLocalDns: Boolean,
    enableVpnAppendHttpProxy: Boolean,
    localProxySettingsSummary: String,
    enableTrafficStatsNotification: Boolean,
    onOpenTunSettings: () -> Unit,
    onEnableVpnHevTunChange: (Boolean) -> Unit,
    onEnableVpnLocalDnsChange: (Boolean) -> Unit,
    onEnableVpnAppendHttpProxyChange: (Boolean) -> Unit,
    onOpenLocalProxySettings: () -> Unit,
    onEnableTrafficStatsNotificationChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_proxy_vpn_service))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_tun),
            summary = tunSettingsSummary,
            onClick = onOpenTunSettings,
        )
        SwitchPreference(
            title = stringResource(R.string.configs_hevtun),
            summary = stringResource(R.string.configs_hevtun_summary),
            checked = enableVpnHevTun,
            onCheckedChange = onEnableVpnHevTunChange,
        )
        SwitchPreference(
            title = stringResource(R.string.configs_local_dns),
            summary = stringResource(R.string.configs_local_dns_summary),
            checked = enableVpnLocalDns,
            onCheckedChange = onEnableVpnLocalDnsChange,
        )
        SwitchPreference(
            title = stringResource(R.string.configs_append_http_proxy),
            summary = stringResource(R.string.configs_append_http_proxy_summary),
            checked = enableVpnAppendHttpProxy,
            onCheckedChange = onEnableVpnAppendHttpProxyChange,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_local_proxy),
            summary = localProxySettingsSummary,
            onClick = onOpenLocalProxySettings,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_traffic_stats_notification),
            summary = stringResource(R.string.settings_traffic_stats_notification_summary),
            checked = enableTrafficStatsNotification,
            onCheckedChange = onEnableTrafficStatsNotificationChange,
        )
    }
}

@Composable
internal fun SettingsLogsSection(
    enableAccessLog: Boolean,
    onOpenCoreLogs: () -> Unit,
    onOpenAccessLogs: () -> Unit,
    onOpenLogcatLogs: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_logs))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_core_logs),
            onClick = onOpenCoreLogs,
        )
        AnimatedVisibility(
            visible = enableAccessLog,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ArrowPreference(
                title = stringResource(R.string.settings_access_logs),
                onClick = onOpenAccessLogs,
            )
        }
        ArrowPreference(
            title = stringResource(R.string.settings_logcat),
            onClick = onOpenLogcatLogs,
        )
    }
}

@Composable
internal fun SettingsBackupRestoreSection(
    onBackupUserData: () -> Unit,
    onRestoreUserData: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_backup_restore))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_backup_user_data),
            summary = stringResource(R.string.settings_backup_user_data_summary),
            onClick = onBackupUserData,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_restore_user_data),
            summary = stringResource(R.string.settings_restore_user_data_summary),
            onClick = onRestoreUserData,
        )
    }
}

@Composable
internal fun SettingsResetSection(
    onResetVpnSettings: () -> Unit,
    onResetApp: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_reset))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(R.string.settings_reset_vpn),
            summary = stringResource(R.string.settings_reset_vpn_summary),
            onClick = onResetVpnSettings,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_reset_app),
            summary = stringResource(R.string.settings_reset_app_summary),
            onClick = onResetApp,
        )
    }
}

@Composable
internal fun SettingsAboutSection(
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_about))
    SettingsSectionCard(bottomPadding = 0.dp) {
        ArrowPreference(
            title = stringResource(R.string.settings_about_project),
            onClick = onOpenAbout,
        )
        ArrowPreference(
            title = stringResource(R.string.settings_open_source_licenses),
            onClick = onOpenLicenses,
        )
    }
}
