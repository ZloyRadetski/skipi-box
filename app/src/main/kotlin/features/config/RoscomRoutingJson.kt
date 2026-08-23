// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.ResourceFileUpdateSources
import features.resources.ResourceFileSourceCustom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converter for the Happ/Incy subscription routing payload.
 *
 * Providers ship routing as `happ://routing/onadd/{base64}` or
 * `incy://routing/onadd/{base64}` links (also plain JSON in headers). The
 * base64 decodes to a JSON document with grouped rule lists, DNS settings and
 * geo resource URLs. This file detects that format and converts it into a
 * regular Shadowrocket profile so the normal import path can take over.
 */

private val RoscomRoutingJsonFormat = Json { ignoreUnknownKeys = true }

private const val RoutingKeyName = "Name"
private const val RoutingKeyRouteOrder = "RouteOrder"
private const val RoutingKeyGlobalProxy = "GlobalProxy"
private const val RoutingKeyFakeDns = "FakeDNS"
private const val RoutingKeyDnsHosts = "DnsHosts"
private const val RoutingKeyGeoIpUrl = "Geoipurl"
private const val RoutingKeyGeoSiteUrl = "Geositeurl"

/** Recognized route group names inside [RoutingKeyRouteOrder]. */
private val RoutingGroups = setOf("block", "proxy", "direct")

/** Detects the Happ/Incy routing JSON document; returns it parsed or null. */
internal fun String.toRoscomRoutingJsonOrNull(): JsonObject? {
    val trimmed = trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
    val json = runCatching { RoscomRoutingJsonFormat.parseToJsonElement(trimmed) }
        .getOrNull() as? JsonObject
        ?: return null
    // RouteOrder plus at least one rule group is specific enough to avoid
    // misclassifying unrelated JSON documents.
    if (!json.containsKey(RoutingKeyRouteOrder)) return null
    val hasRuleGroup = listOf("DirectSites", "ProxySites", "BlockSites", "DirectIp", "ProxyIp", "BlockIp")
        .any(json::containsKey)
    return json.takeIf { hasRuleGroup }
}

/**
 * Converts a detected routing JSON document into a complete Shadowrocket
 * profile ([Rule] section plus the SKIPI settings it can express).
 */
internal fun JsonObject.toRoscomRoutingShadowrocketConf(fallbackName: String): String {
    val name = string(RoutingKeyName)?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackName
    val globalProxy = bool(RoutingKeyGlobalProxy, default = true)
    val order = string(RoutingKeyRouteOrder)?.trim()?.lowercase()
        ?.split('-')
        ?.map(String::trim)
        ?.filter { it in RoutingGroups }
        .orEmpty()
        .ifEmpty { listOf("block", "proxy", "direct") }

    val rules = buildList {
        order.forEach { group ->
            when (group) {
                "block" -> appendGroupRules(json = this@toRoscomRoutingShadowrocketConf, sitesKey = "BlockSites", ipsKey = "BlockIp", policy = "REJECT")
                "proxy" -> appendGroupRules(json = this@toRoscomRoutingShadowrocketConf, sitesKey = "ProxySites", ipsKey = "ProxyIp", policy = "PROXY")
                "direct" -> appendGroupRules(json = this@toRoscomRoutingShadowrocketConf, sitesKey = "DirectSites", ipsKey = "DirectIp", policy = "DIRECT")
            }
        }
        add("FINAL," + if (globalProxy) "PROXY" else "DIRECT")
    }

    val proxyDns = dnsServer(typeKey = "RemoteDNSType", domainKey = "RemoteDNSDomain", ipKey = "RemoteDNSIP", legacyKey = "RemoteDns")
    val directDns = dnsServer(typeKey = "DomesticDNSType", domainKey = "DomesticDNSDomain", ipKey = "DomesticDNSIP", legacyKey = "DomesticDns")
    val geoIpUrl = string(RoutingKeyGeoIpUrl)?.trim().orEmpty()
    val geositeUrl = string(RoutingKeyGeoSiteUrl)?.trim().orEmpty()

    val skipiLines = buildList {
        add("profile-name = $name")
        proxyDns?.let { add("proxy-dns = $it") }
        directDns?.let { add("direct-dns = $it") }
        dnsHosts().forEach { host -> add("dns-hosts = $host") }
        string(RoutingKeyFakeDns)?.let { add("fake-dns = ${it.trim().equals("true", ignoreCase = true)}") }
        domainStrategyValue()?.let { add("route-domain-strategy = $it") }
        if (geoIpUrl.isNotEmpty() || geositeUrl.isNotEmpty()) {
            // Custom URLs are only honored while source == custom, so select a
            // matching preset when possible and fall back to custom otherwise.
            val preset = ResourceFileUpdateSources.firstOrNull { source ->
                (geoIpUrl.isEmpty() || source.geoIpUrl == geoIpUrl) &&
                    (geositeUrl.isEmpty() || source.geoSiteUrl == geositeUrl)
            }
            add("resource-source = ${preset?.id ?: ResourceFileSourceCustom}")
            if (geoIpUrl.isNotEmpty()) add("resource-geoip-url = $geoIpUrl")
            if (geositeUrl.isNotEmpty()) add("resource-geosite-url = $geositeUrl")
        }
    }

    return buildString {
        appendLine("# profile-name: $name")
        appendLine("[Rule]")
        rules.forEach(::appendLine)
        appendLine("[SKIPI]")
        skipiLines.forEach(::appendLine)
    }
}

private fun MutableList<String>.appendGroupRules(json: JsonObject, sitesKey: String, ipsKey: String, policy: String) {
    json.stringList(sitesKey).plus(json.stringList(ipsKey)).forEach { value ->
        routingValueToShadowrocketRule(value, policy)?.let(::add)
    }
}

/**
 * Maps one routing JSON value onto a Shadowrocket rule line. Geosite and ext
 * references use SKIPI's existing DOMAIN-SET type; geo tags become GEOIP,
 * CIDR blocks become IP-CIDR and bare domains are treated as suffixes.
 */
private fun routingValueToShadowrocketRule(value: String, policy: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.isEmpty() -> null
        trimmed.startsWith("geosite:", ignoreCase = true) || trimmed.startsWith("ext:", ignoreCase = true) ->
            "DOMAIN-SET,$trimmed,$policy"

        trimmed.startsWith("geoip:", ignoreCase = true) ->
            "GEOIP,${trimmed.substringAfter(':')},$policy"

        trimmed.contains('/') -> "IP-CIDR,$trimmed,$policy"
        else -> "DOMAIN-SUFFIX,$trimmed,$policy"
    }
}

/**
 * Resolves one remote/domestic DNS entry. DoH/DoT/DoQ use their explicit
 * domain field; everything else falls back to the plain server address.
 */
private fun JsonObject.dnsServer(typeKey: String, domainKey: String, ipKey: String, legacyKey: String): String? {
    val type = string(typeKey)?.trim()?.lowercase().orEmpty()
    val domain = string(domainKey)?.trim().orEmpty()
    val ip = string(ipKey)?.trim().orEmpty()
    val legacy = string(legacyKey)?.trim().orEmpty()

    val resolved = when {
        type in setOf("doh", "https", "dot", "tls", "doq", "quic") && domain.isNotEmpty() ->
            if ("://" in domain) {
                domain
            } else {
                when (type) {
                    "dot", "tls" -> "tls://$domain"
                    "doq", "quic" -> "quic://$domain"
                    else -> domain
                }
            }

        ip.isNotEmpty() -> ip
        else -> ""
    }.ifBlank { legacy }

    return resolved.takeIf(CharSequence::isNotEmpty)
}

private fun JsonObject.dnsHosts(): List<String> {
    val hostsObject = this[RoutingKeyDnsHosts] as? JsonObject ?: return emptyList()
    return hostsObject.mapNotNull { (host, value) ->
        val address = (value as? JsonPrimitive)?.takeIf { it != JsonNull }?.content?.trim().orEmpty()
        "$host:$address".takeIf { host.isNotBlank() && address.isNotEmpty() }
    }
}

/**
 * Maps the provider's DomainStrategy onto SKIPI's Xray routing domain
 * strategy so the provider's resolution order is respected instead of
 * silently falling back to AsIs.
 */
private fun JsonObject.domainStrategyValue(): String? {
    val raw = string("DomainStrategy")?.trim()?.lowercase().orEmpty()
    return when (raw) {
        "asis", "as-is" -> "AsIs"
        "ipondemand", "ip-on-demand" -> "IPOnDemand"
        "ipifnonmatch", "ip-if-non-match" -> "IPIfNonMatch"
        else -> null
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}

private fun JsonObject.stringList(key: String): List<String> {
    return (this[key] as? JsonArray)
        ?.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim() }
        .orEmpty()
        .filter(String::isNotEmpty)
}

private fun JsonObject.bool(key: String, default: Boolean): Boolean {
    return string(key)?.trim()?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: default
}