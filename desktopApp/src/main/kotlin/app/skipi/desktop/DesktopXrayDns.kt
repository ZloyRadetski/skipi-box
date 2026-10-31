// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.vpn.VpnDefaults
import engine.xray.DefaultDirectDnsDomains
import engine.xray.XrayDnsPlan
import engine.xray.XrayDnsPlanRequest
import engine.xray.XrayFakeDnsIpv4OnlyPoolSize
import engine.xray.XrayFakeDnsIpv4Pool
import engine.xray.XrayRoutingRuleValidator
import engine.xray.planXrayDns

/** Global DNS values used when no profile supplies them; Android has no setter for them either. */
internal data class DesktopDnsInputs(
    val proxyDns: List<String> = VpnDefaults.PROXY_DNS_SERVERS,
    val directDns: List<String> = VpnDefaults.DIRECT_DNS_SERVERS,
    val directDnsDomains: List<String> = DefaultDirectDnsDomains,
    val dnsHosts: List<String> = emptyList(),
    val enableIpv6: Boolean = false,
    val enableIpv6Prefer: Boolean = false,
    /** Android's TUN DNS: the resolver the plan falls back to when no proxy DNS is configured. */
    val tunDns: String = VpnDefaults.IPV4_DNS,
)

/**
 * Builds Android's DNS plan for the local SOCKS runtime. FakeDNS stays off because Android only
 * enables it together with the VPN local DNS, which Desktop's SOCKS listener does not provide.
 */
internal fun planDesktopXrayDns(
    inputs: DesktopDnsInputs,
    startupProxyServerDomains: List<String>,
    ruleValidator: XrayRoutingRuleValidator,
): XrayDnsPlan = planXrayDns(
    XrayDnsPlanRequest(
        proxyDnsServers = inputs.proxyDns,
        directDnsServers = inputs.directDns,
        appDirectDnsServers = inputs.directDns,
        defaultDirectDnsServers = VpnDefaults.DIRECT_DNS_SERVERS,
        directDnsDomains = ruleValidator.filterValidDomainRules(inputs.directDnsDomains),
        dnsHosts = inputs.dnsHosts,
        startupProxyServerDomains = startupProxyServerDomains,
        enableIpv6 = inputs.enableIpv6,
        enableFakeDns = false,
        fakeDnsIpPool = XrayFakeDnsIpv4Pool,
        fakeDnsPoolSize = XrayFakeDnsIpv4OnlyPoolSize,
        tunDns = inputs.tunDns,
        fallbackDnsServer = VpnDefaults.IPV4_DNS,
    ),
)
