// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.network.isIpAddress

/**
 * Raw TCP DNS destination used when Xray cannot send a DNS record type through
 * its built-in DNS module.
 */
data class XrayDnsTcpFallback(
    val address: String,
    val port: Int = 53,
)

/**
 * DNS server forms understood by the bundled Xray core.
 *
 * Keep this in one place: profile import, the global settings sheet and the
 * generated Xray JSON must never disagree about which value is usable.
 */
fun isSupportedXrayDnsServer(
    value: String,
    allowFakeDns: Boolean = false,
): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return false
    if (trimmed.equals("localhost", ignoreCase = true)) return true
    if (trimmed.equals("fakedns", ignoreCase = true)) return allowFakeDns

    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd >= 0) {
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        if (scheme !in SupportedXrayDnsUrlSchemes) return false
        return isXrayDnsAuthority(trimmed.xrayDnsAuthority())
    }

    return isIpAddress(trimmed) || (!trimmed.contains(':') && isXrayDnsHostDomain(trimmed))
}

fun Iterable<String>.toSupportedXrayDnsServers(): List<String> {
    return asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { server -> isSupportedXrayDnsServer(server) }
        .distinct()
        .toList()
}

/**
 * Hostnames for remote DNS transports have to be bootstrapped through direct
 * DNS. Local transports deliberately use the system resolver instead.
 */
fun String.remoteXrayDnsHostOrNull(): String? {
    val trimmed = trim()
    if (!isSupportedXrayDnsServer(trimmed)) return null

    val schemeEnd = trimmed.indexOf("://")
    val authority = when {
        schemeEnd < 0 -> trimmed.takeIf { !isIpAddress(it) }
        trimmed.substring(0, schemeEnd).lowercase() in RemoteXrayDnsUrlSchemes -> trimmed.xrayDnsAuthority()
        else -> null
    } ?: return null

    val host = authority.xrayDnsHost()
    return host
        ?.removeSuffix(".")
        ?.takeIf { it.isNotBlank() && !it.equals("localhost", ignoreCase = true) && !isIpAddress(it) }
        ?.lowercase()
}

/**
 * Builds the raw TCP DNS destination used for record types which Xray cannot
 * pass to its built-in DNS module. A `tcp://` endpoint preserves its explicitly
 * selected port. A DoH URL alone does not prove that its host also offers raw
 * TCP DNS, so the only HTTPS exception is Null's Proxy, whose documented TCP
 * endpoint is the configured host on port 53. Local-only transports
 * deliberately stay out of this path.
 */
fun String.toXrayTcpDnsFallbackOrNull(): XrayDnsTcpFallback? {
    val trimmed = trim()
    if (!isSupportedXrayDnsServer(trimmed) || trimmed.equals("localhost", ignoreCase = true)) {
        return null
    }

    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd < 0) {
        return XrayDnsTcpFallback(address = trimmed.removeSuffix("."))
    }

    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    if (scheme !in RemoteXrayDnsUrlSchemes) return null
    val authority = trimmed.xrayDnsAuthority()
    val host = authority.xrayDnsHost()?.removeSuffix(".") ?: return null
    if (scheme == "tcp") {
        return XrayDnsTcpFallback(address = host, port = authority.xrayDnsPortOrNull() ?: 53)
    }
    return host
        .takeIf { value -> value.equals(NullsProxyDnsHost, ignoreCase = true) }
        ?.let { address -> XrayDnsTcpFallback(address = address) }
}

private fun String.xrayDnsAuthority(): String {
    val schemeEnd = indexOf("://")
    if (schemeEnd < 0) return ""
    return substring(schemeEnd + 3)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
}

private fun String.xrayDnsHost(): String? {
    val trimmed = trim()
    if (trimmed.startsWith("[")) {
        val closeBracketIndex = trimmed.indexOf(']')
        return trimmed.substring(1, closeBracketIndex.takeIf { it > 1 } ?: return null)
    }

    return when (trimmed.count { it == ':' }) {
        0 -> trimmed
        1 -> trimmed.substringBefore(':')
        else -> trimmed // An unbracketed IPv6 literal; rejected by the caller.
    }
}

private fun String.xrayDnsPortOrNull(): Int? {
    val trimmed = trim()
    val rawPort = when {
        trimmed.startsWith("[") -> trimmed.substringAfter(']', missingDelimiterValue = "").removePrefix(":")
        trimmed.count { it == ':' } == 1 -> trimmed.substringAfter(':')
        else -> ""
    }
    return rawPort.toIntOrNull()?.takeIf { port -> port in 1..65_535 }
}

private fun isXrayDnsAuthority(authority: String): Boolean {
    val trimmed = authority.trim()
    if (trimmed.isBlank()) return false

    if (trimmed.startsWith("[")) {
        val closeBracketIndex = trimmed.indexOf(']')
        if (closeBracketIndex <= 1) return false
        val host = trimmed.substring(1, closeBracketIndex)
        val rest = trimmed.substring(closeBracketIndex + 1)
        return isIpAddress(host) && (rest.isEmpty() || (rest.startsWith(':') && rest.drop(1).isXrayDnsPort()))
    }

    return when (trimmed.count { it == ':' }) {
        0 -> isIpAddress(trimmed) || isXrayDnsHostDomain(trimmed)
        1 -> {
            val host = trimmed.substringBefore(':')
            val port = trimmed.substringAfter(':')
            (isIpAddress(host) || isXrayDnsHostDomain(host)) && port.isXrayDnsPort()
        }

        else -> isIpAddress(trimmed)
    }
}

private fun String.isXrayDnsPort(): Boolean = toIntOrNull() in 1..65_535

private fun isXrayDnsHostDomain(domain: String): Boolean {
    val normalized = domain.removeSuffix(".")
    if (normalized.isEmpty() || normalized.length > 253) return false
    if (normalized.any { it.isWhitespace() || it == '/' || it == ':' }) return false
    return normalized.split('.').all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private val SupportedXrayDnsUrlSchemes = setOf(
    "https",
    "h2c",
    "https+local",
    "h2c+local",
    "quic+local",
    "tcp",
    "tcp+local",
)

private val RemoteXrayDnsUrlSchemes = setOf("https", "h2c", "tcp")
private const val NullsProxyDnsHost = "dns.nullsproxy.com"
