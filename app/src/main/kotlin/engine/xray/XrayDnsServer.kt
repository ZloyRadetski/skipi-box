// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.network.isIpAddress

/**
 * DNS server forms understood by the bundled Xray core.
 *
 * Keep this in one place: profile import, the global settings sheet and the
 * generated Xray JSON must never disagree about which value is usable.
 */
internal fun isSupportedXrayDnsServer(
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

internal fun Iterable<String>.toSupportedXrayDnsServers(): List<String> {
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
internal fun String.remoteXrayDnsHostOrNull(): String? {
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
