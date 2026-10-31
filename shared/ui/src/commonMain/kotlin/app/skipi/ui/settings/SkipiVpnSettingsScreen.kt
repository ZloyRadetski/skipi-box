// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_append_http_proxy
import app.skipi.ui.resources.configs_append_http_proxy_summary
import app.skipi.ui.resources.configs_hevtun
import app.skipi.ui.resources.configs_hevtun_summary
import app.skipi.ui.resources.settings_auto_connect_on_app_open_summary
import app.skipi.ui.resources.settings_auto_connect_on_app_open_title
import app.skipi.ui.resources.settings_auto_connect_on_boot_summary
import app.skipi.ui.resources.settings_auto_connect_on_boot_title
import app.skipi.ui.resources.settings_auto_connect_title
import app.skipi.ui.resources.settings_battery_optimization
import app.skipi.ui.resources.settings_battery_optimization_restricted
import app.skipi.ui.resources.settings_battery_optimization_unrestricted
import app.skipi.ui.resources.settings_category_vpn
import app.skipi.ui.resources.settings_kill_switch
import app.skipi.ui.resources.settings_kill_switch_summary
import app.skipi.ui.resources.settings_network_automation_summary
import app.skipi.ui.resources.settings_network_automation_title
import app.skipi.ui.resources.settings_proxy_vpn_service
import app.skipi.ui.resources.settings_seamless_network_switching
import app.skipi.ui.resources.settings_seamless_network_switching_summary
import app.skipi.ui.resources.settings_stability_and_background
import app.skipi.ui.resources.settings_strict_full_tunnel
import app.skipi.ui.resources.settings_strict_full_tunnel_summary
import app.skipi.ui.resources.settings_system_vpn_settings
import app.skipi.ui.resources.settings_system_vpn_settings_summary
import app.skipi.ui.resources.settings_tun
import app.skipi.ui.resources.settings_wake_lock
import app.skipi.ui.resources.settings_wake_lock_summary
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

data class VpnSettingsState(
    val tunSummary: String,
    val hevTunEnabled: Boolean,
    val appendHttpProxyEnabled: Boolean,
    val strictFullTunnelEnabled: Boolean,
    val autoConnectOnAppOpen: Boolean,
    val autoConnectOnBoot: Boolean,
    val killSwitchEnabled: Boolean,
    val batteryOptimizationUnrestricted: Boolean,
    val wakeLockEnabled: Boolean,
    val seamlessNetworkSwitchingEnabled: Boolean,
)

data class VpnSettingsLabels(
    val screenTitle: String,
    val vpnSectionTitle: String,
    val tunTitle: String,
    val hevTunTitle: String,
    val hevTunSummary: String,
    val appendHttpTitle: String,
    val appendHttpSummary: String,
    val strictFullTunnelTitle: String,
    val strictFullTunnelSummary: String,
    val autoConnectSectionTitle: String,
    val autoConnectOnOpenTitle: String,
    val autoConnectOnOpenSummary: String,
    val autoConnectOnBootTitle: String,
    val autoConnectOnBootSummary: String,
    val killSwitchSectionTitle: String,
    val killSwitchTitle: String,
    val killSwitchSummary: String,
    val systemVpnSettingsTitle: String,
    val systemVpnSettingsSummary: String,
    val stabilitySectionTitle: String,
    val batteryOptimizationTitle: String,
    val batteryUnrestrictedSummary: String,
    val batteryRestrictedSummary: String,
    val wakeLockTitle: String,
    val wakeLockSummary: String,
    val seamlessSwitchingTitle: String,
    val seamlessSwitchingSummary: String,
    val networkAutomationTitle: String,
    val networkAutomationSummary: String,
)

/**
 * Rows a host can actually honour. Everything is on by default, which is the Android page; a host
 * hides the rows whose behaviour it does not implement instead of showing inert switches.
 */
data class VpnSettingsCapabilities(
    val hevTun: Boolean = true,
    val appendHttpProxy: Boolean = true,
    val strictFullTunnel: Boolean = true,
    val autoConnectOnBoot: Boolean = true,
    val killSwitch: Boolean = true,
    val systemVpnSettings: Boolean = true,
    val batteryOptimization: Boolean = true,
    val wakeLock: Boolean = true,
    val seamlessSwitching: Boolean = true,
)

@Composable
fun rememberVpnSettingsLabels(): VpnSettingsLabels = VpnSettingsLabels(
    screenTitle = stringResource(Res.string.settings_category_vpn),
    vpnSectionTitle = stringResource(Res.string.settings_proxy_vpn_service),
    tunTitle = stringResource(Res.string.settings_tun),
    hevTunTitle = stringResource(Res.string.configs_hevtun),
    hevTunSummary = stringResource(Res.string.configs_hevtun_summary),
    appendHttpTitle = stringResource(Res.string.configs_append_http_proxy),
    appendHttpSummary = stringResource(Res.string.configs_append_http_proxy_summary),
    strictFullTunnelTitle = stringResource(Res.string.settings_strict_full_tunnel),
    strictFullTunnelSummary = stringResource(Res.string.settings_strict_full_tunnel_summary),
    autoConnectSectionTitle = stringResource(Res.string.settings_auto_connect_title),
    autoConnectOnOpenTitle = stringResource(Res.string.settings_auto_connect_on_app_open_title),
    autoConnectOnOpenSummary = stringResource(Res.string.settings_auto_connect_on_app_open_summary),
    autoConnectOnBootTitle = stringResource(Res.string.settings_auto_connect_on_boot_title),
    autoConnectOnBootSummary = stringResource(Res.string.settings_auto_connect_on_boot_summary),
    killSwitchSectionTitle = stringResource(Res.string.settings_kill_switch),
    killSwitchTitle = stringResource(Res.string.settings_kill_switch),
    killSwitchSummary = stringResource(Res.string.settings_kill_switch_summary),
    systemVpnSettingsTitle = stringResource(Res.string.settings_system_vpn_settings),
    systemVpnSettingsSummary = stringResource(Res.string.settings_system_vpn_settings_summary),
    stabilitySectionTitle = stringResource(Res.string.settings_stability_and_background),
    batteryOptimizationTitle = stringResource(Res.string.settings_battery_optimization),
    batteryUnrestrictedSummary = stringResource(Res.string.settings_battery_optimization_unrestricted),
    batteryRestrictedSummary = stringResource(Res.string.settings_battery_optimization_restricted),
    wakeLockTitle = stringResource(Res.string.settings_wake_lock),
    wakeLockSummary = stringResource(Res.string.settings_wake_lock_summary),
    seamlessSwitchingTitle = stringResource(Res.string.settings_seamless_network_switching),
    seamlessSwitchingSummary = stringResource(Res.string.settings_seamless_network_switching_summary),
    networkAutomationTitle = stringResource(Res.string.settings_network_automation_title),
    networkAutomationSummary = stringResource(Res.string.settings_network_automation_summary),
)

/** Shared VPN settings UI. Tunnel configuration and system permission actions stay in the host. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiVpnSettingsScreen(
    state: VpnSettingsState,
    labels: VpnSettingsLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onOpenTunSettings: () -> Unit,
    onHevTunChange: (Boolean) -> Unit,
    onAppendHttpProxyChange: (Boolean) -> Unit,
    onStrictFullTunnelChange: (Boolean) -> Unit,
    onAutoConnectOnOpenChange: (Boolean) -> Unit,
    onAutoConnectOnBootChange: (Boolean) -> Unit,
    onKillSwitchChange: (Boolean) -> Unit,
    onBatteryOptimizationChange: (Boolean) -> Unit,
    onWakeLockChange: (Boolean) -> Unit,
    onSeamlessSwitchingChange: (Boolean) -> Unit,
    onOpenSystemVpnSettings: () -> Unit,
    onOpenNetworkAutomation: () -> Unit,
    capabilities: VpnSettingsCapabilities = VpnSettingsCapabilities(),
    /** Host-owned rows shown above the shared ones, for settings only that host has. */
    hostContent: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(labels.screenTitle) {
                SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack)
            }
        },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                if (hostContent != null) item(key = "vpn_host") { hostContent() }
                item(key = "vpn_tun") {
                    SmallTitle(text = labels.vpnSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = labels.tunTitle,
                            summary = state.tunSummary,
                            onClick = onOpenTunSettings,
                        )
                        if (capabilities.hevTun) SwitchPreference(
                            title = labels.hevTunTitle,
                            summary = labels.hevTunSummary,
                            checked = state.hevTunEnabled,
                            onCheckedChange = onHevTunChange,
                        )
                        if (capabilities.appendHttpProxy) SwitchPreference(
                            title = labels.appendHttpTitle,
                            summary = labels.appendHttpSummary,
                            checked = state.appendHttpProxyEnabled,
                            onCheckedChange = onAppendHttpProxyChange,
                        )
                        if (capabilities.strictFullTunnel) SwitchPreference(
                            title = labels.strictFullTunnelTitle,
                            summary = labels.strictFullTunnelSummary,
                            checked = state.strictFullTunnelEnabled,
                            onCheckedChange = onStrictFullTunnelChange,
                        )
                    }
                }
                item(key = "vpn_auto_connect") {
                    SmallTitle(text = labels.autoConnectSectionTitle)
                    SkipiSettingsSectionCard {
                        SwitchPreference(
                            title = labels.autoConnectOnOpenTitle,
                            summary = labels.autoConnectOnOpenSummary,
                            checked = state.autoConnectOnAppOpen,
                            onCheckedChange = onAutoConnectOnOpenChange,
                        )
                        if (capabilities.autoConnectOnBoot) SwitchPreference(
                            title = labels.autoConnectOnBootTitle,
                            summary = labels.autoConnectOnBootSummary,
                            checked = state.autoConnectOnBoot,
                            onCheckedChange = onAutoConnectOnBootChange,
                        )
                    }
                }
                if (capabilities.killSwitch || capabilities.systemVpnSettings) item(key = "vpn_kill_switch") {
                    SmallTitle(text = labels.killSwitchSectionTitle)
                    SkipiSettingsSectionCard {
                        if (capabilities.killSwitch) SwitchPreference(
                            title = labels.killSwitchTitle,
                            summary = labels.killSwitchSummary,
                            checked = state.killSwitchEnabled,
                            onCheckedChange = onKillSwitchChange,
                        )
                        if (capabilities.systemVpnSettings) ArrowPreference(
                            title = labels.systemVpnSettingsTitle,
                            summary = labels.systemVpnSettingsSummary,
                            onClick = onOpenSystemVpnSettings,
                        )
                    }
                }
                item(key = "vpn_stability") {
                    SmallTitle(text = labels.stabilitySectionTitle)
                    SkipiSettingsSectionCard {
                        if (capabilities.batteryOptimization) SwitchPreference(
                            title = labels.batteryOptimizationTitle,
                            summary = if (state.batteryOptimizationUnrestricted) {
                                labels.batteryUnrestrictedSummary
                            } else {
                                labels.batteryRestrictedSummary
                            },
                            checked = state.batteryOptimizationUnrestricted,
                            onCheckedChange = onBatteryOptimizationChange,
                        )
                        if (capabilities.wakeLock) SwitchPreference(
                            title = labels.wakeLockTitle,
                            summary = labels.wakeLockSummary,
                            checked = state.wakeLockEnabled,
                            onCheckedChange = onWakeLockChange,
                        )
                        if (capabilities.seamlessSwitching) SwitchPreference(
                            title = labels.seamlessSwitchingTitle,
                            summary = labels.seamlessSwitchingSummary,
                            checked = state.seamlessNetworkSwitchingEnabled,
                            onCheckedChange = onSeamlessSwitchingChange,
                        )
                        ArrowPreference(
                            title = labels.networkAutomationTitle,
                            summary = labels.networkAutomationSummary,
                            onClick = onOpenNetworkAutomation,
                        )
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

/** A titled switch in the same card style as the shared rows, for settings only one host has. */
@Composable
fun SkipiHostSwitchSection(
    sectionTitle: String,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SmallTitle(text = sectionTitle)
    SkipiSettingsSectionCard {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
