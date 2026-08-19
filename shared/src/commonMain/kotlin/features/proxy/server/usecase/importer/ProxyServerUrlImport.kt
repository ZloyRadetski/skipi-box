// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AppLogger
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerConstants
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource

internal suspend fun parseProxyServersFromUrls(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    val source = context.source
    val seenUrls = linkedSetOf<String>()
    val servers = mutableListOf<ProxyServer<*>>()
    var urlCount = 0

    fun importCandidate(url: String): ProxyServer<*>? {
        if (!seenUrls.add(url)) return null
        val server = parseProxyServerUrlOrNull(
            url = url,
            index = urlCount,
            source = source,
        )
        urlCount += 1
        return server
    }

    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trim().trimStart(ImportByteOrderMark)
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

    return ProxyServerImportResult(
        urlCount = urlCount,
        servers = servers,
    )
}

private fun parseProxyServerUrlOrNull(
    url: String,
    index: Int,
    source: ProxyServerImportSource,
): ProxyServer<*>? {
    return runCatching {
        val server = ProxyServer.parse(url)
        val issues = server.validateBasic()
        if (issues.isNotEmpty()) {
            throw IllegalArgumentException("Basic validation failed: ${issues.joinToString { it.error.name }}")
        }
        server
    }
        .onFailure { error ->
            AppLogger.warn(
                ProxyServerImportLogTag,
                url.importFailureMessage(index = index, source = source),
                error,
            )
        }
        .getOrNull()
}

private fun String.importFailureMessage(
    index: Int,
    source: ProxyServerImportSource,
): String {
    val protocol = substringBefore("://", missingDelimiterValue = "").ifBlank { "<blank>" }
    return "Failed to import proxy server URL source=${source.logName} index=$index protocol=$protocol length=$length"
}

private fun String.embeddedProxyServerUrls(): Sequence<String> {
    return ProxyServerUrlRegex.findAll(this)
        .map { match -> match.value.trimEnd(',', ';') }
}

private fun String.startsWithProxyServerScheme(): Boolean {
    val lower = lowercase()
    return ProxyServerUrlPrefixes.any { prefix -> lower.startsWith(prefix) }
}

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
)

private val ProxyServerUrlRegex = Regex(
    "(?i)\\b(?:http|socks|socks4|socks5|ss|vmess|vless|trojan|hy2|hysteria2|wireguard)://[^\\s<>\"']+",
)

private const val ProxyServerImportLogTag = "ProxyServerImport"
