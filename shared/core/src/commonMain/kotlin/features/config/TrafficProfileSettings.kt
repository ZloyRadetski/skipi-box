// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import engine.xray.XrayFakeDnsIpv4OnlyPoolSize
import engine.xray.XrayFakeDnsIpv4Pool
import kotlinx.serialization.Serializable

/** Xray and network activation values stored in the portable traffic-profile document. */
@Serializable
data class TrafficConfigAndroidSettings(
    val enableSniffing: Boolean = true,
    val enableSniffingRouteOnly: Boolean = true,
    val enableMux: Boolean = false,
    val muxConcurrency: String = DefaultMuxConcurrency,
    val muxXudpConcurrency: String = DefaultMuxXudpConcurrency,
    val muxXudpProxyUdp443: Int = DefaultMuxUdp443Mode,
    val enableFragment: Boolean = false,
    val fragmentPackets: String = DefaultFragmentPackets,
    val fragmentLength: String = DefaultFragmentLength,
    val fragmentInterval: String = DefaultFragmentInterval,
    val enableVpnLocalDns: Boolean = true,
    val enableFakeDns: Boolean = false,
    val enableResolveProxyServerDomain: Boolean = true,
    val enableDirectDnsForProxyServerDomains: Boolean = true,
    val tunVpnDns: String = TrafficProfileDefaultTunDns,
    val proxyDns: List<String> = TrafficProfileDefaultProxyDns,
    val directDns: List<String> = TrafficProfileDefaultDirectDns,
    val directDnsDomains: List<String> = emptyList(),
    val dnsHosts: List<String> = emptyList(),
    /** Xray routing domain strategy: 0 = AsIs, 1 = IPIfNonMatch, 2 = IPOnDemand. */
    val routeDomainStrategy: Int = 0,
    /** FakeDNS synthetic address pool; blank falls back to the core default. */
    val fakeDnsIpPool: String = XrayFakeDnsIpv4Pool,
    /** Number of synthetic addresses handed out by FakeDNS. */
    val fakeDnsPoolSize: Int = XrayFakeDnsIpv4OnlyPoolSize,
)

@Serializable
data class TrafficConfigNetworkActivation(
    val enabled: Boolean = false,
    val transport: Int = TrafficConfigNetworkTransportWifi,
)

const val TrafficConfigNetworkTransportWifi = 0
const val TrafficConfigNetworkTransportCellular = 1
const val TrafficProfileDefaultTunDns = "8.8.8.8"
val TrafficProfileDefaultProxyDns = listOf("https://8.8.8.8/dns-query")
val TrafficProfileDefaultDirectDns = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")

/** Validates portable domain rules accepted by the profile DNS editor. */
fun isValidTrafficConfigDnsDomainRule(input: String): Boolean {
    val value = input.trim()
    if (value.isEmpty() || value.any(Char::isWhitespace)) return false
    if (value.startsWith("regexp:", ignoreCase = true)) return value.substringAfter(':').isNotBlank()
    val prefix = value.substringBefore(':', missingDelimiterValue = "").lowercase()
    if (prefix in setOf("domain", "full", "keyword", "geosite", "ext")) {
        return value.substringAfter(':').isNotBlank()
    }
    return !value.contains("://") && !value.contains('/')
}

/** Validates a portable static DNS host mapping in `domain: address[,address]` form. */
fun isValidTrafficConfigDnsHost(input: String): Boolean {
    val separator = input.indexOf(':')
    if (separator <= 0 || separator == input.lastIndex) return false
    val domain = input.substring(0, separator).trim().removeSuffix(".")
    val addresses = input.substring(separator + 1).split(',').map { it.trim().trim('[', ']') }
    if (!isTrafficConfigDnsHostDomain(domain) || addresses.isEmpty()) return false
    return addresses.all { address -> address.isNotEmpty() && engine.network.isIpAddress(address) }
}

private fun isTrafficConfigDnsHostDomain(domain: String): Boolean {
    if (domain.isEmpty() || domain.length > 253) return false
    if (domain.any { it.isWhitespace() || it == '/' || it == ':' }) return false
    return domain.split('.').all { label ->
        label.isNotEmpty() && label.length <= 63 && label.first() != '-' && label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}
