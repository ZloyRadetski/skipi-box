// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.network.isIpAddress
import engine.network.isIpv4Address
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

data class XrayDnsPlan(
    val servers: JsonArray,
    val queryStrategy: String,
    val tag: String,
    val hosts: JsonObject,
    val fakeDns: JsonElement?,
    /**
     * Xray's DNS outbound only hands A and AAAA queries to the built-in DNS
     * module by default. Other record types need an explicit plaintext DNS
     * destination.
     */
    val nonIpQueryFallback: XrayDnsTcpFallback,
    val routingOptions: XrayDnsRoutingOptions,
)

data class XrayDnsRoutingOptions(
    val routeProxyDns: Boolean,
    val routeDirectDns: Boolean,
)

/**
 * Platform-neutral input for the DNS section of a generated Xray config.
 *
 * The platform supplies its tunnel DNS and defaults; the resulting plan is
 * identical for Android, desktop, and any future SKIPI Core host.
 */
data class XrayDnsPlanRequest(
    val proxyDnsServers: List<String>,
    val directDnsServers: List<String>,
    val appDirectDnsServers: List<String>,
    val defaultDirectDnsServers: List<String>,
    val directDnsDomains: List<String>,
    val dnsHosts: List<String>,
    val startupProxyServerDomains: List<String>,
    val enableIpv6: Boolean,
    val enableFakeDns: Boolean,
    val fakeDnsIpPool: String,
    val fakeDnsPoolSize: Int,
    val tunDns: String,
    val fallbackDnsServer: String,
)

fun planXrayDns(request: XrayDnsPlanRequest): XrayDnsPlan {
    val sanitizedProxyDnsServers = request.proxyDnsServers.toSupportedXrayDnsServers()
    val effectiveDirectDnsServers = resolveXrayDirectDnsServers(
        directDnsServers = request.directDnsServers,
        appDirectDnsServers = request.appDirectDnsServers,
        defaultDirectDnsServers = request.defaultDirectDnsServers,
    )
    val systemBootstrapDnsDomains = systemDnsBootstrapDomains(
        proxyDnsServers = sanitizedProxyDnsServers,
        directDnsServers = effectiveDirectDnsServers,
    )
    val effectiveDirectDnsDomains = resolveXrayDirectDnsDomains(
        directDnsDomains = request.directDnsDomains,
        startupProxyServerDomains = request.startupProxyServerDomains,
        proxyDnsServers = sanitizedProxyDnsServers,
        systemBootstrapDnsDomains = systemBootstrapDnsDomains,
    )
    val effectiveProxyDnsServers = xrayProxyDnsServers(
        proxyDnsServers = sanitizedProxyDnsServers,
        directDnsServers = effectiveDirectDnsServers,
        directDnsDomains = effectiveDirectDnsDomains,
        tunDns = request.tunDns,
        fallbackDnsServer = request.fallbackDnsServer,
    )
    return XrayDnsPlan(
        servers = buildXrayDnsServers(
            proxyDnsServers = effectiveProxyDnsServers,
            effectiveDirectDnsServers = effectiveDirectDnsServers,
            effectiveDirectDnsDomains = effectiveDirectDnsDomains,
            systemBootstrapDnsDomains = systemBootstrapDnsDomains,
            enableFakeDns = request.enableFakeDns,
            tunDns = request.tunDns,
            fallbackDnsServer = request.fallbackDnsServer,
        ),
        queryStrategy = if (request.enableIpv6) "UseIP" else "UseIPv4",
        tag = XrayTags.PROXY_DNS,
        hosts = request.dnsHosts.toDnsHostsJson(),
        fakeDns = if (request.enableFakeDns) {
            buildXrayFakeDnsConfig(
                ipPool = request.fakeDnsIpPool,
                poolSize = request.fakeDnsPoolSize,
            )
        } else {
            null
        },
        nonIpQueryFallback = sanitizedProxyDnsServers
            .asSequence()
            .mapNotNull(String::toXrayTcpDnsFallbackOrNull)
            .firstOrNull()
            ?: XrayDnsTcpFallback(
                address = request.tunDns.trim().takeIf(::isIpAddress) ?: request.fallbackDnsServer,
            ),
        routingOptions = XrayDnsRoutingOptions(
            routeProxyDns = effectiveProxyDnsServers.isNotEmpty(),
            routeDirectDns = systemBootstrapDnsDomains.isNotEmpty() ||
                (effectiveDirectDnsDomains.isNotEmpty() && effectiveDirectDnsServers.isNotEmpty()),
        ),
    )
}

fun buildXrayDnsConfig(plan: XrayDnsPlan): JsonObject = buildJsonObject {
    put("servers", plan.servers)
    put("queryStrategy", plan.queryStrategy)
    put("tag", plan.tag)
    putIfNotEmpty("hosts", plan.hosts)
}

fun xrayProxyDnsServers(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
    directDnsDomains: List<String>? = null,
    tunDns: String,
    fallbackDnsServer: String,
): List<String> {
    val sanitizedProxyDns = proxyDnsServers.toSupportedXrayDnsServers()
    if (sanitizedProxyDns.isNotEmpty()) {
        return sanitizedProxyDns
    }
    val hasDirectDns = directDnsServers.toSupportedXrayDnsServers().isNotEmpty() &&
        (directDnsDomains == null || directDnsDomains.isNotEmpty())
    return if (!hasDirectDns) {
        listOf(
            tunDns.trim()
                .takeIf(::isIpv4Address)
                ?: fallbackDnsServer,
        )
    } else {
        emptyList()
    }
}

fun resolveXrayDirectDnsServers(
    directDnsServers: List<String>,
    appDirectDnsServers: List<String>,
    defaultDirectDnsServers: List<String>,
): List<String> {
    val configured = directDnsServers.toSupportedXrayDnsServers()
    if (configured.isNotEmpty()) {
        return configured
    }
    val appDirect = appDirectDnsServers.toSupportedXrayDnsServers()
    if (appDirect.isNotEmpty()) {
        return appDirect
    }
    return defaultDirectDnsServers
}

fun resolveXrayDirectDnsDomains(
    directDnsDomains: List<String>,
    startupProxyServerDomains: List<String> = emptyList(),
    proxyDnsServers: List<String> = emptyList(),
    systemBootstrapDnsDomains: List<String> = emptyList(),
): List<String> {
    val remoteDnsDomains = proxyDnsServers.mapNotNull { server ->
        server.remoteXrayDnsHostOrNull()?.let { host -> "domain:$host" }
    }
    return (directDnsDomains.toTrimmedNonEmptyDistinctList() + startupProxyServerDomains + remoteDnsDomains)
        .filterNot(systemBootstrapDnsDomains.toSet()::contains)
        .distinct()
}

/**
 * Uses the system resolver for the bootstrap lookup only when the same remote
 * DNS server was selected for both proxied and direct DNS.
 */
fun systemDnsBootstrapDomains(
    proxyDnsServers: List<String>,
    directDnsServers: List<String>,
): List<String> {
    val directDnsHosts = directDnsServers.mapNotNull(String::remoteXrayDnsHostOrNull).toSet()
    if (directDnsHosts.isEmpty()) return emptyList()
    return proxyDnsServers.mapNotNull { server ->
        server.remoteXrayDnsHostOrNull()
            ?.takeIf(directDnsHosts::contains)
            ?.let { host -> "domain:$host" }
    }.distinct()
}

fun Iterable<XrayProxyOutboundServer>.startupProxyServerDnsDomains(): List<String> =
    mapNotNull { outbound -> outbound.server?.serverHost() }.startupProxyServerHostDnsDomains()

fun Iterable<String>.startupProxyServerHostDnsDomains(): List<String> =
    mapNotNull { host -> host.toXrayDnsDomainRule() }.distinct()

private fun buildXrayFakeDnsConfig(
    ipPool: String,
    poolSize: Int,
): JsonElement = buildJsonObject {
    put("ipPool", ipPool.trim().takeIf(String::isNotEmpty) ?: XrayFakeDnsIpv4Pool)
    put("poolSize", poolSize.takeIf { it > 0 } ?: XrayFakeDnsIpv4OnlyPoolSize)
}

private fun buildXrayDnsServers(
    proxyDnsServers: List<String>,
    effectiveDirectDnsServers: List<String>,
    effectiveDirectDnsDomains: List<String>,
    systemBootstrapDnsDomains: List<String>,
    enableFakeDns: Boolean,
    tunDns: String,
    fallbackDnsServer: String,
): JsonArray = buildJsonArray {
    if (enableFakeDns) {
        add(JsonPrimitive("fakedns"))
    }
    if (systemBootstrapDnsDomains.isNotEmpty()) {
        add(
            buildJsonObject {
                put("address", "localhost")
                put("domains", systemBootstrapDnsDomains.toJsonStringArray())
                put("skipFallback", true)
                put("tag", XrayTags.DIRECT_DNS)
            },
        )
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
    proxyDnsServers.forEach { server -> add(JsonPrimitive(server)) }
    if (!enableFakeDns && proxyDnsServers.isEmpty()) {
        add(
            JsonPrimitive(
                tunDns.trim().takeIf(::isIpv4Address) ?: fallbackDnsServer,
            ),
        )
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
