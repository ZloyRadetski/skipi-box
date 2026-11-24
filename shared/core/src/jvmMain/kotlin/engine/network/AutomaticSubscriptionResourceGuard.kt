// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import features.subscription.isValidManualSubscriptionUrl
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

/** [SubscriptionHttpRequest.urlGuard] for resources a downloaded subscription names (providers, linked configs). */
val AutomaticSubscriptionResourceUrlGuard: (URI) -> URI = { uri ->
    requireSafeAutomaticSubscriptionResourceUrl(uri.toString())
}

/**
 * Validates an auxiliary URL found in subscription metadata or a Mihomo provider: HTTP(S) only, no embedded
 * credentials, and not a local, private, link-local or metadata host (also after DNS resolution). The root URL
 * the user typed is deliberately not checked.
 */
fun requireSafeAutomaticSubscriptionResourceUrl(
    value: String,
    resolveAddresses: (String) -> Array<InetAddress> = InetAddress::getAllByName,
): URI {
    val normalized = value.trim()
    require(normalized.isValidManualSubscriptionUrl()) { "Automatic subscription resource has an invalid URL" }
    val uri = URI(normalized)
    require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
        "Automatic subscription resource must use HTTP or HTTPS"
    }
    require(uri.userInfo.isNullOrBlank()) { "Automatic subscription resource must not contain user credentials" }
    require(uri.port == -1 || uri.port in 1..65_535) { "Automatic subscription resource has an invalid port" }

    val host = uri.host
        ?.trim()
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.trimEnd('.')
        ?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Automatic subscription resource has no host")
    require(!host.isKnownLocalOrMetadataHost()) {
        "Automatic subscription resource points to a local or metadata host"
    }
    val addresses = runCatching { resolveAddresses(host) }.getOrElse { error ->
        throw IllegalArgumentException("Automatic subscription resource host cannot be resolved", error)
    }
    require(addresses.isNotEmpty()) { "Automatic subscription resource host cannot be resolved" }
    require(addresses.none(InetAddress::isUnsafeAutomaticSubscriptionAddress)) {
        "Automatic subscription resource points to a private or local network address"
    }
    return uri
}

private val KnownMetadataHosts = setOf(
    "metadata",
    "metadata.google.internal",
    "metadata.google",
    "instance-data",
    "instance-data.ec2.internal",
)

private fun String.isKnownLocalOrMetadataHost(): Boolean {
    val normalized = trim().trimEnd('.').lowercase()
    return normalized == "localhost" ||
        normalized.endsWith(".localhost") ||
        normalized.endsWith(".local") ||
        normalized in KnownMetadataHosts
}

private fun InetAddress.isUnsafeAutomaticSubscriptionAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
        return true
    }
    val bytes = address.map { byte -> byte.toInt() and 0xFF }
    return when (this) {
        is Inet4Address -> bytes.isUnsafeAutomaticSubscriptionIpv4()
        else -> bytes.isUnsafeAutomaticSubscriptionIpv6()
    }
}

private fun List<Int>.isUnsafeAutomaticSubscriptionIpv4(): Boolean {
    if (size != 4) return true
    val first = this[0]
    val second = this[1]
    val third = this[2]
    return first == 0 ||
        first == 10 ||
        first == 127 ||
        (first == 100 && second in 64..127) ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 0 && third in setOf(0, 2)) ||
        (first == 192 && second == 168) ||
        (first == 198 && second in 18..19) ||
        (first == 198 && second == 51 && third == 100) ||
        (first == 203 && second == 0 && third == 113) ||
        first >= 224
}

private fun List<Int>.isUnsafeAutomaticSubscriptionIpv6(): Boolean {
    if (size != 16) return true
    val ipv4Mapped = take(10).all { it == 0 } && this[10] == 0xFF && this[11] == 0xFF
    if (ipv4Mapped) return drop(12).isUnsafeAutomaticSubscriptionIpv4()
    val first = this[0]
    val second = this[1]
    val third = this[2]
    val fourth = this[3]
    return (first and 0xFE) == 0xFC || // fc00::/7 unique local
        (first == 0x20 && second == 0x01 && third == 0x0D && fourth == 0xB8) // documentation range
}
