// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import app.AppState
import features.settings.sheets.DnsSettingsBottomSheet
import features.settings.sheets.FragmentSettingsBottomSheet
import features.settings.sheets.LocalProxySettingsBottomSheet
import features.settings.sheets.MuxSettingsBottomSheet
import features.settings.sheets.TunSettingsBottomSheet
import features.settings.sheets.SubscriptionPingSettingsBottomSheet
import features.settings.sheets.sanitizeMuxUdp443Index
import app.modes.RunModeVpnService

@Composable
internal fun SettingsBottomSheetsHost(
    appState: AppState,
    sheetState: SettingsSheetState,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    LocalProxySettingsBottomSheet(
        show = sheetState.showLocalProxySettings,
        port = sheetState.localProxySettingsDraft.port,
        enableDynamicPort = sheetState.localProxySettingsDraft.enableDynamicPort,
        listenAllInterfaces = sheetState.localProxySettingsDraft.listenAllInterfaces,
        enableAuth = sheetState.localProxySettingsDraft.enableAuth,
        username = sheetState.localProxySettingsDraft.username,
        password = sheetState.localProxySettingsDraft.password,
        onPortChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(
                port = it,
            )
        },
        onEnableDynamicPortChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(enableDynamicPort = it)
        },
        onListenAllInterfacesChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(listenAllInterfaces = it)
        },
        onEnableAuthChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(enableAuth = it)
        },
        onUsernameChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(username = it)
        },
        onPasswordChange = {
            sheetState.localProxySettingsDraft = sheetState.localProxySettingsDraft.copy(password = it)
        },
        onDismissRequest = { sheetState.showLocalProxySettings = false },
        onSave = { port, enableDynamicPort, listenAllInterfaces, enableAuth, username, password ->
            updateAppState { state ->
                state.copy(
                    localProxyPort = port,
                    enableDynamicLocalProxyPort = enableDynamicPort,
                    localProxyListenAllInterfaces = listenAllInterfaces,
                    enableLocalProxyAuth = enableAuth,
                    localProxyUsername = username,
                    localProxyPassword = password,
                )
            }
            sheetState.showLocalProxySettings = false
        },
    )
    TunSettingsBottomSheet(
        show = sheetState.showTunSettings,
        mtu = sheetState.tunSettingsDraft.mtu,
        vpnDns = sheetState.tunSettingsDraft.vpnDns,
        ipv4Cidr = sheetState.tunSettingsDraft.ipv4Cidr,
        ipv6Cidr = sheetState.tunSettingsDraft.ipv6Cidr,
        tcpKeepAliveInterval = sheetState.tunSettingsDraft.tcpKeepAliveInterval,
        tcpUserTimeout = sheetState.tunSettingsDraft.tcpUserTimeout,
        showVpnDns = appState.runMode == RunModeVpnService,
        onMtuChange = {
            sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(mtu = it)
        },
        onVpnDnsChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(vpnDns = it) },
        onIpv4CidrChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(ipv4Cidr = it) },
        onIpv6CidrChange = { sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(ipv6Cidr = it) },
        onTcpKeepAliveIntervalChange = {
            sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(tcpKeepAliveInterval = it)
        },
        onTcpUserTimeoutChange = {
            sheetState.tunSettingsDraft = sheetState.tunSettingsDraft.copy(tcpUserTimeout = it)
        },
        onDismissRequest = { sheetState.showTunSettings = false },
        onSave = { mtu, vpnDns, ipv4Cidr, ipv6Cidr, tcpKeepAlive, tcpUserTimeout ->
            updateAppState { state ->
                state.copy(
                    tunMtu = mtu,
                    tunVpnDns = if (state.runMode == RunModeVpnService) vpnDns else state.tunVpnDns,
                    tunIpv4Cidr = ipv4Cidr,
                    tunIpv6Cidr = ipv6Cidr,
                    tunTcpKeepAliveInterval = tcpKeepAlive,
                    tunTcpUserTimeout = tcpUserTimeout,
                )
            }
            sheetState.showTunSettings = false
        },
    )
    DnsSettingsBottomSheet(
        show = sheetState.showDnsSettings,
        enableVpnLocalDns = sheetState.dnsSettingsDraft.enableVpnLocalDns,
        enableFakeDns = sheetState.dnsSettingsDraft.enableFakeDns,
        enableResolveProxyServerDomain = sheetState.dnsSettingsDraft.enableResolveProxyServerDomain,
        proxyDns = sheetState.dnsSettingsDraft.proxyDns,
        directDns = sheetState.dnsSettingsDraft.directDns,
        directDnsDomains = sheetState.dnsSettingsDraft.directDnsDomains,
        enableDirectDnsForProxyServerDomains = sheetState.dnsSettingsDraft.enableDirectDnsForProxyServerDomains,
        dnsHosts = sheetState.dnsSettingsDraft.dnsHosts,
        onEnableVpnLocalDnsChange = { enabled ->
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(
                enableVpnLocalDns = enabled,
                enableFakeDns = if (enabled) sheetState.dnsSettingsDraft.enableFakeDns else false,
            )
        },
        onEnableFakeDnsChange = {
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(enableFakeDns = it)
        },
        onEnableResolveProxyServerDomainChange = {
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(enableResolveProxyServerDomain = it)
        },
        onProxyDnsChange = { sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(proxyDns = it) },
        onDirectDnsChange = { sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(directDns = it) },
        onDirectDnsDomainsChange = {
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(directDnsDomains = it)
        },
        onEnableDirectDnsForProxyServerDomainsChange = {
            sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(
                enableDirectDnsForProxyServerDomains = it,
            )
        },
        onDnsHostsChange = { sheetState.dnsSettingsDraft = sheetState.dnsSettingsDraft.copy(dnsHosts = it) },
        onDismissRequest = { sheetState.showDnsSettings = false },
        onSave = { enableVpnLocalDns, enableFakeDns, enableResolveProxyServerDomain, proxyDns, directDns, directDnsDomains, enableDirectDnsForProxyServerDomains, dnsHosts ->
            updateAppState { state ->
                state.copy(
                    enableVpnLocalDns = enableVpnLocalDns,
                    enableFakeDns = enableFakeDns,
                    enableResolveProxyServerDomain = enableResolveProxyServerDomain,
                    proxyDns = proxyDns,
                    directDns = directDns,
                    directDnsDomains = directDnsDomains,
                    enableDirectDnsForProxyServerDomains = enableDirectDnsForProxyServerDomains,
                    dnsHosts = dnsHosts,
                )
            }
            sheetState.showDnsSettings = false
        },
    )
    MuxSettingsBottomSheet(
        show = sheetState.showMuxSettings,
        enabled = sheetState.muxSettingsDraft.enabled,
        concurrency = sheetState.muxSettingsDraft.concurrency,
        xudpConcurrency = sheetState.muxSettingsDraft.xudpConcurrency,
        xudpProxyUdp443 = sheetState.muxSettingsDraft.xudpProxyUdp443,
        onEnabledChange = { sheetState.muxSettingsDraft = sheetState.muxSettingsDraft.copy(enabled = it) },
        onConcurrencyChange = {
            sheetState.muxSettingsDraft = sheetState.muxSettingsDraft.copy(concurrency = it)
        },
        onXudpConcurrencyChange = {
            sheetState.muxSettingsDraft = sheetState.muxSettingsDraft.copy(xudpConcurrency = it)
        },
        onXudpProxyUdp443Change = {
            sheetState.muxSettingsDraft = sheetState.muxSettingsDraft.copy(xudpProxyUdp443 = sanitizeMuxUdp443Index(it))
        },
        onDismissRequest = { sheetState.showMuxSettings = false },
        onSave = { enabled, concurrency, xudpConcurrency, xudpProxyUdp443 ->
            updateAppState { state ->
                state.copy(
                    enableMux = enabled,
                    muxConcurrency = concurrency,
                    muxXudpConcurrency = xudpConcurrency,
                    muxXudpProxyUdp443 = xudpProxyUdp443,
                )
            }
            sheetState.showMuxSettings = false
        },
    )
    FragmentSettingsBottomSheet(
        show = sheetState.showFragmentSettings,
        enabled = sheetState.fragmentSettingsDraft.enabled,
        packets = sheetState.fragmentSettingsDraft.packets,
        length = sheetState.fragmentSettingsDraft.length,
        interval = sheetState.fragmentSettingsDraft.interval,
        onEnabledChange = {
            sheetState.fragmentSettingsDraft = sheetState.fragmentSettingsDraft.copy(enabled = it)
        },
        onPacketsChange = { sheetState.fragmentSettingsDraft = sheetState.fragmentSettingsDraft.copy(packets = it) },
        onLengthChange = {
            sheetState.fragmentSettingsDraft = sheetState.fragmentSettingsDraft.copy(
                length = it,
            )
        },
        onIntervalChange = {
            sheetState.fragmentSettingsDraft = sheetState.fragmentSettingsDraft.copy(
                interval = it,
            )
        },
        onDismissRequest = { sheetState.showFragmentSettings = false },
        onSave = { enabled, packets, length, interval ->
            updateAppState { state ->
                state.copy(
                    enableFragment = enabled,
                    fragmentPackets = packets,
                    fragmentLength = length,
                    fragmentInterval = interval,
                )
            }
            sheetState.showFragmentSettings = false
        },
    )
    SubscriptionPingSettingsBottomSheet(
        show = sheetState.showSubscriptionPingSettings,
        url = sheetState.subscriptionPingSettingsDraft.url,
        timeoutMillis = sheetState.subscriptionPingSettingsDraft.timeoutMillis,
        onUrlChange = { value ->
            sheetState.subscriptionPingSettingsDraft = sheetState.subscriptionPingSettingsDraft.copy(url = value)
        },
        onTimeoutMillisChange = { value ->
            sheetState.subscriptionPingSettingsDraft = sheetState.subscriptionPingSettingsDraft.copy(
                timeoutMillis = value,
            )
        },
        onDismissRequest = { sheetState.showSubscriptionPingSettings = false },
        onSave = { url, timeoutMillis ->
            updateAppState { state ->
                state.copy(
                    subscriptionPingUrl = url,
                    subscriptionPingTimeoutMillis = timeoutMillis,
                )
            }
            sheetState.showSubscriptionPingSettings = false
        },
    )
}
