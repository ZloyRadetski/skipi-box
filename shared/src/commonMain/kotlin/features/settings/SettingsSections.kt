// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.modes.ConnectionDisplayModeClassic
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.configs_append_http_proxy
import app.shared.res.configs_append_http_proxy_summary
import app.shared.res.configs_hevtun
import app.shared.res.configs_hevtun_summary
import app.shared.res.configs_local_dns
import app.shared.res.configs_local_dns_summary
import app.shared.res.settings_about
import app.shared.res.settings_about_project
import app.shared.res.settings_access_logs
import app.shared.res.settings_advanced
import app.shared.res.settings_backup_restore
import app.shared.res.settings_backup_user_data
import app.shared.res.settings_backup_user_data_summary
import app.shared.res.settings_broadcast_control
import app.shared.res.settings_broadcast_control_summary
import app.shared.res.settings_classic_show_floating_power_button
import app.shared.res.settings_classic_show_floating_power_button_summary
import app.shared.res.settings_color_mode
import app.shared.res.settings_color_mode_summary
import app.shared.res.settings_connection_display_mode
import app.shared.res.settings_connection_display_mode_summary
import app.shared.res.settings_core
import app.shared.res.settings_core_logs
import app.shared.res.settings_deletion_confirmation
import app.shared.res.settings_deletion_confirmation_summary
import app.shared.res.settings_dns
import app.shared.res.settings_dns_summary
import app.shared.res.settings_enable_all_proxy_group
import app.shared.res.settings_enable_all_proxy_group_summary
import app.shared.res.settings_enable_material_you
import app.shared.res.settings_enable_material_you_summary
import app.shared.res.settings_fragment
import app.shared.res.settings_general
import app.shared.res.settings_ipv6
import app.shared.res.settings_ipv6_prefer
import app.shared.res.settings_ipv6_prefer_summary
import app.shared.res.settings_ipv6_summary
import app.shared.res.settings_language
import app.shared.res.settings_language_summary
import app.shared.res.settings_local_proxy
import app.shared.res.settings_log_level
import app.shared.res.settings_logcat
import app.shared.res.settings_logs
import app.shared.res.settings_mux
import app.shared.res.settings_open_source_licenses
import app.shared.res.settings_proxy_vpn_service
import app.shared.res.settings_record_access_log
import app.shared.res.settings_reset
import app.shared.res.settings_reset_app
import app.shared.res.settings_reset_app_summary
import app.shared.res.settings_reset_vpn
import app.shared.res.settings_reset_vpn_summary
import app.shared.res.settings_restore_user_data
import app.shared.res.settings_restore_user_data_summary
import app.shared.res.settings_show_server_search
import app.shared.res.settings_show_server_search_summary
import app.shared.res.settings_sniffing
import app.shared.res.settings_sniffing_route_only
import app.shared.res.settings_sniffing_route_only_summary
import app.shared.res.settings_sniffing_summary
import app.shared.res.settings_subscription_device_headers
import app.shared.res.settings_subscription_device_headers_summary
import app.shared.res.settings_subscription_fetch_timeout
import app.shared.res.settings_subscription_fetch_timeout_summary
import app.shared.res.settings_theme
import app.shared.res.settings_theme_color
import app.shared.res.settings_theme_color_summary
import app.shared.res.settings_traffic_stats_notification
import app.shared.res.settings_traffic_stats_notification_summary
import app.shared.res.settings_tun
import app.shared.res.settings_url_schemes
import app.shared.res.settings_url_schemes_summary
import app.shared.res.settings_user_agents
import app.shared.res.settings_user_agents_summary
import app.shared.res.subscription_ping_mode
import app.shared.res.subscription_ping_mode_summary
import app.shared.res.subscription_ping_settings
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsThemeSection(
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
    SmallTitle(text = stringResource(Res.string.settings_theme))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_color_mode),
            summary = stringResource(Res.string.settings_color_mode_summary),
            items = colorModeOptions,
            selectedIndex = colorMode,
            onSelectedIndexChange = onColorModeChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_enable_material_you),
            summary = stringResource(Res.string.settings_enable_material_you_summary),
            checked = enableMaterialYou,
            onCheckedChange = onEnableMaterialYouChange,
        )
        AnimatedVisibility(
            visible = enableMaterialYou,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_theme_color),
                summary = stringResource(Res.string.settings_theme_color_summary),
                items = keyColorOptions,
                selectedIndex = seedIndex,
                onSelectedIndexChange = onSeedIndexChange,
            )
        }
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_language),
            summary = stringResource(Res.string.settings_language_summary),
            items = languageOptions,
            selectedIndex = languageMode,
            onSelectedIndexChange = onLanguageModeChange,
        )
    }
}

@Composable
fun SettingsSubscriptionsSection(
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
    SmallTitle(text = stringResource(Res.string.settings_general))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_connection_display_mode),
            summary = stringResource(Res.string.settings_connection_display_mode_summary),
            items = connectionDisplayModeOptions,
            selectedIndex = connectionDisplayMode,
            onSelectedIndexChange = onConnectionDisplayModeChange,
        )
        AnimatedVisibility(visible = connectionDisplayMode == ConnectionDisplayModeClassic) {
            SwitchPreference(
                title = stringResource(Res.string.settings_classic_show_floating_power_button),
                summary = stringResource(Res.string.settings_classic_show_floating_power_button_summary),
                checked = classicShowFloatingPowerButton,
                onCheckedChange = onClassicShowFloatingPowerButtonChange,
            )
        }
        SwitchPreference(
            title = stringResource(Res.string.settings_enable_all_proxy_group),
            summary = stringResource(Res.string.settings_enable_all_proxy_group_summary),
            checked = enableAllProxyGroup,
            onCheckedChange = onEnableAllProxyGroupChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_show_server_search),
            summary = stringResource(Res.string.settings_show_server_search_summary),
            checked = showServerSearch,
            onCheckedChange = onShowServerSearchChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_deletion_confirmation),
            summary = stringResource(Res.string.settings_deletion_confirmation_summary),
            checked = enableDeletionConfirmation,
            onCheckedChange = onEnableDeletionConfirmationChange,
        )
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_subscription_fetch_timeout),
            summary = stringResource(Res.string.settings_subscription_fetch_timeout_summary),
            items = fetchTimeoutOptions,
            selectedIndex = fetchTimeoutIndex,
            onSelectedIndexChange = onFetchTimeoutChange,
        )
    }
}

/** Reusable request headers for subscription downloads. */
@Composable
fun SettingsUserAgentsSection(
    userAgents: List<String>,
    onOpenUserAgents: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_user_agents))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_user_agents),
            summary = stringResource(Res.string.settings_user_agents_summary, userAgents.size),
            onClick = onOpenUserAgents,
        )
    }
}

/** Privacy control for subscription providers that require a stable device identity. */
@Composable
fun SettingsSubscriptionDeviceHeadersSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_subscription_device_headers))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(Res.string.settings_subscription_device_headers),
            summary = stringResource(Res.string.settings_subscription_device_headers_summary),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

/** External-link reference kept separate from subscription download preferences. */
@Composable
fun SettingsUrlSchemesSection(
    onOpenUrlSchemes: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_url_schemes))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_url_schemes),
            summary = stringResource(Res.string.settings_url_schemes_summary),
            onClick = onOpenUrlSchemes,
        )
    }
}

/** Subscription health-check preferences are app-wide, not VPN traffic settings. */
@Composable
fun SettingsSubscriptionPingSection(
    pingModeOptions: List<String>,
    pingMode: Int,
    summary: String,
    onPingModeChange: (Int) -> Unit,
    onOpenPingSettings: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.subscription_ping_settings))
    SettingsSectionCard {
        OverlayDropdownPreference(
            title = stringResource(Res.string.subscription_ping_mode),
            summary = stringResource(Res.string.subscription_ping_mode_summary),
            items = pingModeOptions,
            selectedIndex = pingMode.coerceIn(pingModeOptions.indices),
            onSelectedIndexChange = onPingModeChange,
        )
        ArrowPreference(
            title = stringResource(Res.string.subscription_ping_settings),
            summary = summary,
            onClick = onOpenPingSettings,
        )
    }
}

@Composable
fun SettingsCoreSection(
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
    SmallTitle(text = stringResource(Res.string.settings_core))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_dns),
            summary = stringResource(Res.string.settings_dns_summary),
            onClick = onOpenDnsSettings,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_sniffing),
            summary = stringResource(Res.string.settings_sniffing_summary),
            checked = enableSniffing,
            onCheckedChange = onEnableSniffingChange,
        )
        AnimatedVisibility(
            visible = enableSniffing,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchPreference(
                title = stringResource(Res.string.settings_sniffing_route_only),
                summary = stringResource(Res.string.settings_sniffing_route_only_summary),
                checked = enableSniffingRouteOnly,
                onCheckedChange = onEnableSniffingRouteOnlyChange,
            )
        }
        ArrowPreference(
            title = stringResource(Res.string.settings_mux),
            summary = muxSettingsSummary,
            onClick = onOpenMuxSettings,
        )
        ArrowPreference(
            title = stringResource(Res.string.settings_fragment),
            summary = fragmentSettingsSummary,
            onClick = onOpenFragmentSettings,
        )
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_log_level),
            items = SettingsLogLevelOptions,
            selectedIndex = coreLogLevel,
            onSelectedIndexChange = onCoreLogLevelChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_record_access_log),
            checked = enableAccessLog,
            onCheckedChange = onEnableAccessLogChange,
        )
    }
}

@Composable
fun SettingsAdvancedSection(
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
    SmallTitle(text = stringResource(Res.string.settings_advanced))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(Res.string.settings_broadcast_control),
            summary = stringResource(Res.string.settings_broadcast_control_summary),
            checked = enableBroadcastControl,
            onCheckedChange = onEnableBroadcastControlChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_ipv6),
            summary = stringResource(Res.string.settings_ipv6_summary),
            checked = enableIpv6,
            onCheckedChange = onEnableIpv6Change,
        )
        AnimatedVisibility(
            visible = enableIpv6,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SwitchPreference(
                title = stringResource(Res.string.settings_ipv6_prefer),
                summary = stringResource(Res.string.settings_ipv6_prefer_summary),
                checked = enableIpv6Prefer,
                onCheckedChange = onEnableIpv6PreferChange,
            )
        }
        OverlayDropdownPreference(
            title = stringResource(Res.string.subscription_ping_mode),
            items = subscriptionPingModeOptions,
            selectedIndex = subscriptionPingMode.coerceIn(subscriptionPingModeOptions.indices),
            onSelectedIndexChange = onSubscriptionPingModeChange,
        )
        ArrowPreference(
            title = stringResource(Res.string.subscription_ping_settings),
            summary = subscriptionPingSettingsSummary,
            onClick = onOpenSubscriptionPingSettings,
        )
    }
}

@Composable
fun SettingsVpnSection(
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
    SmallTitle(text = stringResource(Res.string.settings_proxy_vpn_service))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_tun),
            summary = tunSettingsSummary,
            onClick = onOpenTunSettings,
        )
        SwitchPreference(
            title = stringResource(Res.string.configs_hevtun),
            summary = stringResource(Res.string.configs_hevtun_summary),
            checked = enableVpnHevTun,
            onCheckedChange = onEnableVpnHevTunChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.configs_local_dns),
            summary = stringResource(Res.string.configs_local_dns_summary),
            checked = enableVpnLocalDns,
            onCheckedChange = onEnableVpnLocalDnsChange,
        )
        SwitchPreference(
            title = stringResource(Res.string.configs_append_http_proxy),
            summary = stringResource(Res.string.configs_append_http_proxy_summary),
            checked = enableVpnAppendHttpProxy,
            onCheckedChange = onEnableVpnAppendHttpProxyChange,
        )
        ArrowPreference(
            title = stringResource(Res.string.settings_local_proxy),
            summary = localProxySettingsSummary,
            onClick = onOpenLocalProxySettings,
        )
        SwitchPreference(
            title = stringResource(Res.string.settings_traffic_stats_notification),
            summary = stringResource(Res.string.settings_traffic_stats_notification_summary),
            checked = enableTrafficStatsNotification,
            onCheckedChange = onEnableTrafficStatsNotificationChange,
        )
    }
}

@Composable
fun SettingsLogsSection(
    enableAccessLog: Boolean,
    onOpenCoreLogs: () -> Unit,
    onOpenAccessLogs: () -> Unit,
    onOpenLogcatLogs: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_logs))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_core_logs),
            onClick = onOpenCoreLogs,
        )
        AnimatedVisibility(
            visible = enableAccessLog,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ArrowPreference(
                title = stringResource(Res.string.settings_access_logs),
                onClick = onOpenAccessLogs,
            )
        }
        ArrowPreference(
            title = stringResource(Res.string.settings_logcat),
            onClick = onOpenLogcatLogs,
        )
    }
}

@Composable
fun SettingsBackupRestoreSection(
    onBackupUserData: () -> Unit,
    onRestoreUserData: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_backup_restore))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_backup_user_data),
            summary = stringResource(Res.string.settings_backup_user_data_summary),
            onClick = onBackupUserData,
        )
        ArrowPreference(
            title = stringResource(Res.string.settings_restore_user_data),
            summary = stringResource(Res.string.settings_restore_user_data_summary),
            onClick = onRestoreUserData,
        )
    }
}

@Composable
fun SettingsResetSection(
    onResetVpnSettings: () -> Unit,
    onResetApp: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_reset))
    SettingsSectionCard {
        ArrowPreference(
            title = stringResource(Res.string.settings_reset_vpn),
            summary = stringResource(Res.string.settings_reset_vpn_summary),
            onClick = onResetVpnSettings,
        )
        ArrowPreference(
            title = stringResource(Res.string.settings_reset_app),
            summary = stringResource(Res.string.settings_reset_app_summary),
            onClick = onResetApp,
        )
    }
}

@Composable
fun SettingsAboutSection(
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    SmallTitle(text = stringResource(Res.string.settings_about))
    SettingsSectionCard(bottomPadding = 0.dp) {
        ArrowPreference(
            title = stringResource(Res.string.settings_about_project),
            onClick = onOpenAbout,
        )
        ArrowPreference(
            title = stringResource(Res.string.settings_open_source_licenses),
            onClick = onOpenLicenses,
        )
    }
}
