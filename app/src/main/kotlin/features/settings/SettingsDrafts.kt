// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import app.skipi.app.settings.DnsSettingsDraft as SharedDnsSettingsDraft
import app.skipi.app.settings.FragmentSettingsDraft as SharedFragmentSettingsDraft
import app.skipi.app.settings.LocalProxySettingsDraft as SharedLocalProxySettingsDraft
import app.skipi.app.settings.MuxSettingsDraft as SharedMuxSettingsDraft
import app.skipi.app.settings.SettingsValues
import app.skipi.app.settings.SubscriptionPingSettingsDraft as SharedSubscriptionPingSettingsDraft
import app.skipi.app.settings.TunSettingsDraft as SharedTunSettingsDraft
import app.skipi.app.settings.toDnsSettingsDraft
import app.skipi.app.settings.toFragmentSettingsDraft
import app.skipi.app.settings.toLocalProxySettingsDraft
import app.skipi.app.settings.toMuxSettingsDraft
import app.skipi.app.settings.toSubscriptionPingSettingsDraft
import app.skipi.app.settings.toTunSettingsDraft

internal typealias TunSettingsDraft = SharedTunSettingsDraft
internal typealias LocalProxySettingsDraft = SharedLocalProxySettingsDraft
internal typealias DnsSettingsDraft = SharedDnsSettingsDraft
internal typealias MuxSettingsDraft = SharedMuxSettingsDraft
internal typealias FragmentSettingsDraft = SharedFragmentSettingsDraft
internal typealias SubscriptionPingSettingsDraft = SharedSubscriptionPingSettingsDraft

private fun AppState.toSettingsValues() = SettingsValues(
    tunMtu = tunMtu,
    tunVpnDns = tunVpnDns,
    tunIpv4Cidr = tunIpv4Cidr,
    tunIpv6Cidr = tunIpv6Cidr,
    tunTcpKeepAliveInterval = tunTcpKeepAliveInterval,
    tunTcpUserTimeout = tunTcpUserTimeout,
    localProxyPort = localProxyPort,
    dynamicLocalProxyPort = enableDynamicLocalProxyPort,
    localProxyListenAllInterfaces = localProxyListenAllInterfaces,
    localProxyAuth = enableLocalProxyAuth,
    localProxyUsername = localProxyUsername,
    localProxyPassword = localProxyPassword,
    enableVpnLocalDns = enableVpnLocalDns,
    enableFakeDns = enableFakeDns,
    enableResolveProxyServerDomain = enableResolveProxyServerDomain,
    proxyDns = proxyDns,
    directDns = directDns,
    directDnsDomains = directDnsDomains,
    enableDirectDnsForProxyServerDomains = enableDirectDnsForProxyServerDomains,
    dnsHosts = dnsHosts,
    enableMux = enableMux,
    muxConcurrency = muxConcurrency,
    muxXudpConcurrency = muxXudpConcurrency,
    muxXudpProxyUdp443 = muxXudpProxyUdp443,
    enableFragment = enableFragment,
    fragmentPackets = fragmentPackets,
    fragmentLength = fragmentLength,
    fragmentInterval = fragmentInterval,
    subscriptionPingUrl = subscriptionPingUrl,
    subscriptionPingTimeoutMillis = subscriptionPingTimeoutMillis,
    serviceControl = serviceControl,
)

internal fun AppState.toTunSettingsDraft() = toSettingsValues().toTunSettingsDraft()
internal fun AppState.toLocalProxySettingsDraft() = toSettingsValues().toLocalProxySettingsDraft()
internal fun AppState.toDnsSettingsDraft() = toSettingsValues().toDnsSettingsDraft()
internal fun AppState.toMuxSettingsDraft() = toSettingsValues().toMuxSettingsDraft()
internal fun AppState.toFragmentSettingsDraft() = toSettingsValues().toFragmentSettingsDraft()
internal fun AppState.toSubscriptionPingSettingsDraft() = toSettingsValues().toSubscriptionPingSettingsDraft()
