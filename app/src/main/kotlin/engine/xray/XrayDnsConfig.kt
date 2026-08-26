// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveFakeDnsEnabled
import engine.network.isIpAddress
import engine.network.isIpv4Address
import engine.vpn.VpnDefaults
import features.proxy.server.model.normalizedServerHost
import features.proxy.server.model.serverHost
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toCsvValues
import utils.toTrimmedNonEmptyDistinctList

internal data class XrayDnsPlan(
    val servers: JsonArray,
    val queryStrategy: String,
    val tag: String,
    val hosts: JsonObject,
    val fakeDns: JsonElement?,
    val routingOptions: XrayDnsRoutingOptions,
)

internal fun XrayConfigRequest.buildXrayDnsPlan(
    startupProxyServerDomains: List<String> = emptyList(),
): XrayDnsPlan {
    return appState.buildXrayDnsPlan(
        proxyDnsServers = proxyDnsServers,
        directDnsServers = directDnsServers,
        directDnsDomains = directDnsDomains,
        dnsHosts = dnsHosts,
        startupProxyServerDomains = startupProxyServerDomains,
        dataDir = dataDir,
    )
}

private fun AppState.buildXrayDnsPlan(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    directDnsDomains: List<String>,
    dnsHosts: List<String>,
    startupProxyServerDomains: List<String>,
    dataDir: String? = null,
): XrayDnsPlan {
    val sanitizedDirectDnsDomains = XrayGeoRuleSanitizer.filterValidDomainRules(directDnsDomains, dataDir)
    val effectiveDirectDnsDomains = xrayDirectDnsDomains(
        directDnsDomains = sanitizedDirectDnsDomains,
        startupProxyServerDomains = startupProxyServerDomains,
        proxyDnsServers = proxyDnsServers,
    )
    val effectiveDirectDnsServers = xrayDirectDnsServers(directDnsServers)
    return XrayDnsPlan(
        servers = xrayDnsServers(
            proxyDnsServers = proxyDnsServers,
            effectiveDirectDnsServers = effectiveDirectDnsServers,
            effectiveDirectDnsDomains = effectiveDirectDnsDomains,
        ),
        queryStrategy = if (enableIpv6) "UseIP" else "UseIPv4",
        tag = XrayTags.PROXY_DNS,
        hosts = dnsHosts.toDnsHostsJson(),
        fakeDns = if (effectiveFakeDnsEnabled) buildXrayFakeDnsConfig() else null,
        routingOptions = XrayDnsRoutingOptions(
            routeProxyDns = xrayProxyDnsServers(
                proxyDnsServers = proxyDnsServers,
                directDnsServers = effectiveDirectDnsServers,
                directDnsDomains = effectiveDirectDnsDomains,
            ).isNotEmpty(),
            routeDirectDns = effectiveDirectDnsDomains.isNotEmpty() &&
                effectiveDirectDnsServers.isNotEmpty(),
        ),
    )
}

internal fun buildXrayDnsConfig(plan: XrayDnsPlan): JsonObject {
    return buildJsonObject {
        put("servers", plan.servers)
        put("queryStrategy", plan.queryStrategy)
        put("tag", plan.tag)
        putIfNotEmpty("hosts", plan.hosts)
    }
}

private fun AppState.buildXrayFakeDnsConfig(): JsonElement {
    return buildJsonObject {
        put("ipPool", fakeDnsIpPool.trim().takeIf(String::isNotEmpty) ?: XrayFakeDnsIpv4Pool)
        put("poolSize", fakeDnsPoolSize.takeIf { it > 0 } ?: XrayFakeDnsIpv4OnlyPoolSize)
    }
}

internal data class XrayDnsRoutingOptions(
    val routeProxyDns: Boolean,
    val routeDirectDns: Boolean,
)

internal fun AppState.xrayProxyDnsServers(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    directDnsDomains: List<String>? = null,
): List<String> {
    val sanitizedProxyDns = proxyDnsServers.toTrimmedNonEmptyDistinctList()
    if (sanitizedProxyDns.isNotEmpty()) {
        return sanitizedProxyDns
    }
    val hasDirectDns = directDnsServers.toTrimmedNonEmptyDistinctList().isNotEmpty() &&
        (directDnsDomains == null || directDnsDomains.isNotEmpty())
    return if (!hasDirectDns) {
        listOf(
            tunVpnDns.trim()
                .takeIf(::isIpv4Address)
                ?: VpnDefaults.IPV4_DNS,
        )
    } else {
        emptyList()
    }
}

internal fun AppState.xrayDirectDnsServers(directDnsServers: List<String>): List<String> {
    val configured = directDnsServers.toTrimmedNonEmptyDistinctList()
    if (configured.isNotEmpty()) {
        return configured
    }
    val appDirect = directDns.toTrimmedNonEmptyDistinctList()
    if (appDirect.isNotEmpty()) {
        return appDirect
    }
    return VpnDefaults.DIRECT_DNS_SERVERS
}

internal fun extractDohHost(dohUrl: String): String? {
    val trimmed = dohUrl.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true) && !trimmed.startsWith("http://", ignoreCase = true)) {
        return null
    }
    val afterScheme = trimmed.substringAfter("://")
    val hostPort = afterScheme.substringBefore('/')
    val host = hostPort.substringBefore(':').trim()
    if (host.isBlank() || isIpAddress(host)) {
        return null
    }
    return host
}

internal fun AppState.xrayDirectDnsDomains(
    directDnsDomains: List<String>,
    startupProxyServerDomains: List<String> = emptyList(),
    proxyDnsServers: List<String> = emptyList(),
): List<String> {
    val dohDomains = proxyDnsServers.mapNotNull { dohUrl ->
        extractDohHost(dohUrl)?.let { host -> "domain:$host" }
    }
    return (directDnsDomains.toTrimmedNonEmptyDistinctList() + startupProxyServerDomains + dohDomains).distinct()
}

internal fun Iterable<XrayProxyOutboundServer>.startupProxyServerDnsDomains(): List<String> {
    return mapNotNull { outbound -> outbound.server?.serverHost() }.startupProxyServerHostDnsDomains()
}

internal fun Iterable<String>.startupProxyServerHostDnsDomains(): List<String> {
    return mapNotNull { host -> host.toXrayDnsDomainRule() }.distinct()
}

private fun AppState.xrayDnsServers(
    proxyDnsServers: List<String>,
    effectiveDirectDnsServers: List<String>,
    effectiveDirectDnsDomains: List<String>,
): JsonArray {
    val proxyDns = xrayProxyDnsServers(
        proxyDnsServers = proxyDnsServers,
        directDnsServers = effectiveDirectDnsServers,
        directDnsDomains = effectiveDirectDnsDomains,
    )
    return buildJsonArray {
        if (effectiveFakeDnsEnabled) {
            add(JsonPrimitive("fakedns"))
        }
        if (effectiveDirectDnsDomains.isNotEmpty()) {
            effectiveDirectDnsServers.forEach { server ->
                add(
                    buildJsonObject {
                        put("address", server)
                        put("domains", effectiveDirectDnsDomains.toJsonStringArray())
                        put("skipFallback", true)
                        put("tag", XrayTags.DIRECT_DNS)
                    },
                )
            }
        }
        proxyDns.forEach { server -> add(JsonPrimitive(server)) }
        // Direct-DNS entries use skipFallback, so they only resolve their own
        // domains.  Without fakedns or a proxy DNS entry there would be no
        // fallback resolver left for every other domain, which breaks DNS
        // resolution entirely.  Always keep a fallback-capable server.
        if (!effectiveFakeDnsEnabled && proxyDns.isEmpty()) {
            add(
                JsonPrimitive(
                    tunVpnDns.trim().takeIf(::isIpv4Address) ?: VpnDefaults.IPV4_DNS,
                ),
            )
        }
    }
}

private fun String.toXrayDnsDomainRule(): String? {
    val host = normalizedServerHost()
    if (host.isBlank() || host.equals("localhost", ignoreCase = true) || isIpAddress(host)) {
        return null
    }
    return "domain:$host"
}

private val BuiltInDnsHosts: Map<String, List<String>> = mapOf(
    "dns.quad9.net" to listOf("9.9.9.9", "149.112.112.112"),
    "cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
    "1dot1dot1dot1.cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
    "one.one.one.one" to listOf("1.1.1.1", "1.0.0.1"),
    "dns.google" to listOf("8.8.8.8", "8.8.4.4"),
    "dns.adguard-dns.com" to listOf("94.140.14.14", "94.140.15.15"),
    "dns.alidns.com" to listOf("223.5.5.5", "223.6.6.6"),
    "doh.pub" to listOf("1.12.12.12", "120.53.53.53"),
    "common.dot.dns.yandex.net" to listOf("77.88.8.8", "77.88.8.1"),
)

private fun List<String>.toDnsHostsJson(): JsonObject {
    val hostsMap = LinkedHashMap<String, List<String>>()
    BuiltInDnsHosts.forEach { (domain, ips) ->
        hostsMap[domain] = ips
    }
    forEach { entry ->
        val separatorIndex = entry.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) {
            return@forEach
        }
        val domain = entry.substring(0, separatorIndex).trim()
        val addresses = entry.substring(separatorIndex + 1)
            .toCsvValues()
            .mapNotNull { address -> address.trim('[', ']').takeIf(String::isNotEmpty) }
        if (domain.isNotEmpty() && addresses.isNotEmpty()) {
            hostsMap[domain] = addresses
        }
    }
    return buildJsonObject {
        hostsMap.forEach { (domain, addresses) ->
            put(
                domain,
                if (addresses.size == 1) JsonPrimitive(addresses.first()) else addresses.toJsonStringArray(),
            )
        }
    }
}
