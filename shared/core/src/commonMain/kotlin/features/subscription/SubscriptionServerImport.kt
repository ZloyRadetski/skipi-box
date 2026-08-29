// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerConstants
import utils.decodeFlexibleBase64OrNull

/** Portable import result for the widespread line-based and Base64 subscription formats. */
data class SubscriptionServerImportResult(
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
    val rejectedUrlCount: Int,
)

/**
 * Parses the ordinary formats returned by subscription providers: a list of proxy URLs or
 * a Base64 encoded version of that list. Provider-specific formats are handled by platform
 * importers on top of this safe common baseline.
 */
fun String.importSubscriptionServers(): SubscriptionServerImportResult {
    val payload = decodeSubscriptionPayloadOrSelf()
    val candidates = payload.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter(::isProxySubscriptionUrl)
        .toList()
    val parsed = candidates.mapNotNull { candidate ->
        runCatching { ProxyServer.parse(candidate) }
            .getOrNull()
            ?.takeIf { server -> server.validateFull().isEmpty() }
    }
    val servers = parsed.distinctBy { it.connectionFingerprint() }
    return SubscriptionServerImportResult(
        urlCount = candidates.size,
        servers = servers,
        rejectedUrlCount = candidates.size - parsed.size,
    )
}

private fun String.decodeSubscriptionPayloadOrSelf(): String {
    val raw = trimStart(SubscriptionByteOrderMark)
    if (raw.lineSequence().any(::isProxySubscriptionUrl)) return raw
    val normalized = raw.filterNot(Char::isWhitespace)
    val decoded = normalized.decodeFlexibleBase64OrNull()?.decodeToString() ?: return raw
    return decoded.takeIf { value -> value.lineSequence().any(::isProxySubscriptionUrl) } ?: raw
}

private fun isProxySubscriptionUrl(value: String): Boolean {
    val scheme = value.substringBefore("://", missingDelimiterValue = "").lowercase()
    return scheme in ProxySubscriptionSchemes
}

private val ProxySubscriptionSchemes = setOf(
    ProxyServerConstants.PROTOCOL_HTTP,
    ProxyServerConstants.PROTOCOL_SOCKS,
    ProxyServerConstants.PROTOCOL_SOCKS4,
    ProxyServerConstants.PROTOCOL_SOCKS5,
    ProxyServerConstants.PROTOCOL_SS,
    ProxyServerConstants.PROTOCOL_VMESS,
    ProxyServerConstants.PROTOCOL_VLESS,
    ProxyServerConstants.PROTOCOL_TROJAN,
    ProxyServerConstants.PROTOCOL_HYSTERIA2,
    ProxyServerConstants.PROTOCOL_HY2,
    ProxyServerConstants.PROTOCOL_WIREGUARD,
)

private const val SubscriptionByteOrderMark = '\uFEFF'
