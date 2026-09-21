// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveFakeDnsEnabled
import engine.vpn.VpnDefaults

/**
 * Android boundary for the shared DNS planner.
 *
 * Geo-resource validation remains here because it reads Android's installed
 * resource files; all DNS selection and Xray JSON construction are shared.
 */
internal fun XrayConfigRequest.buildXrayDnsPlan(
    startupProxyServerDomains: List<String> = emptyList(),
): XrayDnsPlan = planXrayDns(
    XrayDnsPlanRequest(
        proxyDnsServers = proxyDnsServers,
        directDnsServers = directDnsServers,
        appDirectDnsServers = appState.directDns,
        defaultDirectDnsServers = VpnDefaults.DIRECT_DNS_SERVERS,
        directDnsDomains = XrayGeoRuleSanitizer.filterValidDomainRules(directDnsDomains, dataDir),
        dnsHosts = dnsHosts,
        startupProxyServerDomains = startupProxyServerDomains,
        enableIpv6 = appState.enableIpv6,
        enableFakeDns = appState.effectiveFakeDnsEnabled,
        fakeDnsIpPool = appState.fakeDnsIpPool,
        fakeDnsPoolSize = appState.fakeDnsPoolSize,
        tunDns = appState.tunVpnDns,
        fallbackDnsServer = VpnDefaults.IPV4_DNS,
    ),
)

internal fun AppState.xrayProxyDnsServers(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    directDnsDomains: List<String>? = null,
): List<String> = xrayProxyDnsServers(
    proxyDnsServers = proxyDnsServers,
    directDnsServers = directDnsServers,
    directDnsDomains = directDnsDomains,
    tunDns = tunVpnDns,
    fallbackDnsServer = VpnDefaults.IPV4_DNS,
)

internal fun AppState.xrayDirectDnsServers(directDnsServers: List<String>): List<String> =
    resolveXrayDirectDnsServers(
        directDnsServers = directDnsServers,
        appDirectDnsServers = directDns,
        defaultDirectDnsServers = VpnDefaults.DIRECT_DNS_SERVERS,
    )

internal fun AppState.xrayDirectDnsDomains(
    directDnsDomains: List<String>,
    startupProxyServerDomains: List<String> = emptyList(),
    proxyDnsServers: List<String> = emptyList(),
    systemBootstrapDnsDomains: List<String> = emptyList(),
): List<String> = resolveXrayDirectDnsDomains(
    directDnsDomains = directDnsDomains,
    startupProxyServerDomains = startupProxyServerDomains,
    proxyDnsServers = proxyDnsServers,
    systemBootstrapDnsDomains = systemBootstrapDnsDomains,
)
