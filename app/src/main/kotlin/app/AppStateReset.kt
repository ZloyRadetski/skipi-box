// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.config.TrafficConfigAndroidSettings

/**
 * Restores only SKIPI's VPN runtime parameters. Subscription data, servers,
 * Shadowrocket .conf files and configuration routing rules are deliberately
 * retained; they are user content, not a tunnel implementation preference.
 */
internal fun AppState.withVpnSettingsReset(): AppState {
    val defaults = AppState()
    return copy(
        runMode = defaults.runMode,
        enableResolveProxyServerDomain = defaults.enableResolveProxyServerDomain,
        enableVpnLocalDns = defaults.enableVpnLocalDns,
        localProxyPort = defaults.localProxyPort,
        enableDynamicLocalProxyPort = defaults.enableDynamicLocalProxyPort,
        localProxyListenAllInterfaces = defaults.localProxyListenAllInterfaces,
        // Keep existing credentials so that external clients configured with
        // the current username/password are not silently broken by a settings
        // reset.  Credentials are regenerated only via an explicit UI action.
        localProxyUsername = localProxyUsername,
        localProxyPassword = localProxyPassword,
        enableVpnAppendHttpProxy = defaults.enableVpnAppendHttpProxy,
        enableVpnHevTun = defaults.enableVpnHevTun,
        tunMtu = defaults.tunMtu,
        tunVpnDns = defaults.tunVpnDns,
        tunIpv4Cidr = defaults.tunIpv4Cidr,
        tunIpv6Cidr = defaults.tunIpv6Cidr,
        enableSniffing = defaults.enableSniffing,
        enableSniffingRouteOnly = defaults.enableSniffingRouteOnly,
        enableMux = defaults.enableMux,
        muxConcurrency = defaults.muxConcurrency,
        muxXudpConcurrency = defaults.muxXudpConcurrency,
        muxXudpProxyUdp443 = defaults.muxXudpProxyUdp443,
        enableFragment = defaults.enableFragment,
        fragmentPackets = defaults.fragmentPackets,
        fragmentLength = defaults.fragmentLength,
        fragmentInterval = defaults.fragmentInterval,
        enableTrafficStatsNotification = defaults.enableTrafficStatsNotification,
        enableIpv6 = defaults.enableIpv6,
        enableIpv6Prefer = defaults.enableIpv6Prefer,
        enableFakeDns = defaults.enableFakeDns,
        proxyDns = defaults.proxyDns,
        directDns = defaults.directDns,
        directDnsDomains = defaults.directDnsDomains,
        enableDirectDnsForProxyServerDomains = defaults.enableDirectDnsForProxyServerDomains,
        dnsHosts = defaults.dnsHosts,
        proxyAppListMode = defaults.proxyAppListMode,
        proxyAppListSelectedApps = defaults.proxyAppListSelectedApps,
        trafficConfigs = trafficConfigs.map { config ->
            config.copy(androidSettings = TrafficConfigAndroidSettings())
        },
        shadowrocketPolicyGroups = emptyList(),
        proxyRunning = false,
    )
}

