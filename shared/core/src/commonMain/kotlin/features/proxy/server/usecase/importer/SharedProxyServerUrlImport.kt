// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerConstants

/** The platform-neutral result of parsing direct proxy URLs from arbitrary text. */
data class ProxyServerUrlImportResult(
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
)

/** A failed individual URL parse; the host app owns reporting it to logs or UI. */
data class ProxyServerUrlImportFailure(
    val url: String,
    val index: Int,
    val error: Throwable,
)

/**
 * Extracts supported proxy links from lines or surrounding prose. This has no
 * platform logging or I/O: callers can decide how parse failures are surfaced.
 */
fun importProxyServersFromUrls(
    text: String,
    onFailure: (ProxyServerUrlImportFailure) -> Unit = {},
): ProxyServerUrlImportResult {
    val seenUrls = linkedSetOf<String>()
    val servers = mutableListOf<ProxyServer<*>>()
    var urlCount = 0

    fun importCandidate(url: String): ProxyServer<*>? {
        if (!seenUrls.add(url)) return null
        val index = urlCount++
        return runCatching {
            val server = ProxyServer.parse(url)
            val issues = if (server is AmneziaWg) server.validateFull() else server.validateBasic()
            require(issues.isEmpty()) {
                "Proxy validation failed: ${issues.joinToString { it.error.name }}"
            }
            server
        }.onFailure { error ->
            onFailure(ProxyServerUrlImportFailure(url = url, index = index, error = error))
        }.getOrNull()
    }

    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trim().trimStart(ProxyServerImportByteOrderMark)
        if (line.isBlank()) return@forEach

        if (line.startsWithProxyServerScheme()) {
            importCandidate(line)?.let { server ->
                servers += server
                return@forEach
            }
        }

        line.embeddedProxyServerUrls()
            .filterNot { url -> url == line }
            .forEach { url ->
                importCandidate(url)?.let { server -> servers += server }
            }
    }

    return ProxyServerUrlImportResult(
        urlCount = urlCount,
        servers = servers,
    )
}

private fun String.embeddedProxyServerUrls(): Sequence<String> = ProxyServerUrlRegex.findAll(this)
    .map { match ->
        val raw = match.value.trimEnd(',', ';')
        if (raw.startsWith("${ProxyServerConstants.PROTOCOL_OLCRTC}://", ignoreCase = true) &&
            raw.endsWith('>') &&
            raw.lastIndexOf('>') > raw.lastIndexOf('@')
        ) {
            raw.dropLast(1)
        } else {
            raw
        }
    }

private fun String.startsWithProxyServerScheme(): Boolean {
    val lower = lowercase()
    return ProxyServerUrlPrefixes.any { prefix -> lower.startsWith(prefix) }
}

private const val ProxyServerImportByteOrderMark = '\uFEFF'

private val ProxyServerUrlPrefixes = listOf(
    "${ProxyServerConstants.PROTOCOL_HTTP}://",
    "${ProxyServerConstants.PROTOCOL_SOCKS}://",
    "${ProxyServerConstants.PROTOCOL_SOCKS4}://",
    "${ProxyServerConstants.PROTOCOL_SOCKS5}://",
    "${ProxyServerConstants.PROTOCOL_SS}://",
    "${ProxyServerConstants.PROTOCOL_VMESS}://",
    "${ProxyServerConstants.PROTOCOL_VLESS}://",
    "${ProxyServerConstants.PROTOCOL_TROJAN}://",
    "${ProxyServerConstants.PROTOCOL_HY2}://",
    "${ProxyServerConstants.PROTOCOL_HYSTERIA2}://",
    "${ProxyServerConstants.PROTOCOL_WIREGUARD}://",
    "${ProxyServerConstants.PROTOCOL_AMNEZIA_WG}://",
    "${ProxyServerConstants.PROTOCOL_AWG}://",
    "${ProxyServerConstants.PROTOCOL_OLCRTC}://",
)

private val ProxyServerUrlRegex = Regex(
    "(?i)\\b(?:olcrtc://[^\\s\"']+|\\b(?:http|socks|socks4|socks5|ss|vmess|vless|trojan|hy2|hysteria2|wireguard|amneziawg|awg)://[^\\s<>\"']+)",
)
