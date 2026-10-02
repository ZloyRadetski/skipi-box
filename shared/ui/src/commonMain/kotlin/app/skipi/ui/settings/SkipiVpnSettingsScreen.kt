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
                item(key = "vpn_tun") {
                    SmallTitle(text = labels.vpnSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = labels.tunTitle,
                            summary = state.tunSummary,
                            onClick = onOpenTunSettings,
                        )
                        SwitchPreference(
                            title = labels.hevTunTitle,
                            summary = labels.hevTunSummary,
                            checked = state.hevTunEnabled,
                            onCheckedChange = onHevTunChange,
                        )
                        SwitchPreference(
                            title = labels.appendHttpTitle,
                            summary = labels.appendHttpSummary,
                            checked = state.appendHttpProxyEnabled,
                            onCheckedChange = onAppendHttpProxyChange,
                        )
                        SwitchPreference(
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
                        SwitchPreference(
                            title = labels.autoConnectOnBootTitle,
                            summary = labels.autoConnectOnBootSummary,
                            checked = state.autoConnectOnBoot,
                            onCheckedChange = onAutoConnectOnBootChange,
                        )
                    }
                }
                item(key = "vpn_kill_switch") {
                    SmallTitle(text = labels.killSwitchSectionTitle)
                    SkipiSettingsSectionCard {
                        SwitchPreference(
                            title = labels.killSwitchTitle,
                            summary = labels.killSwitchSummary,
                            checked = state.killSwitchEnabled,
                            onCheckedChange = onKillSwitchChange,
                        )
                        ArrowPreference(
                            title = labels.systemVpnSettingsTitle,
                            summary = labels.systemVpnSettingsSummary,
                            onClick = onOpenSystemVpnSettings,
                        )
                    }
                }
                item(key = "vpn_stability") {
                    SmallTitle(text = labels.stabilitySectionTitle)
                    SkipiSettingsSectionCard {
                        SwitchPreference(
                            title = labels.batteryOptimizationTitle,
                            summary = if (state.batteryOptimizationUnrestricted) {
                                labels.batteryUnrestrictedSummary
                            } else {
                                labels.batteryRestrictedSummary
                            },
                            checked = state.batteryOptimizationUnrestricted,
                            onCheckedChange = onBatteryOptimizationChange,
                        )
                        SwitchPreference(
                            title = labels.wakeLockTitle,
                            summary = labels.wakeLockSummary,
                            checked = state.wakeLockEnabled,
                            onCheckedChange = onWakeLockChange,
                        )
                        SwitchPreference(
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
