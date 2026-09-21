// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

private const val LogTag = "WireguardConfImport"

internal suspend fun parseProxyServersFromWireguardConf(
    text: String,
    @Suppress("UNUSED_PARAMETER") context: ProxyServerImportContext,
): ProxyServerImportResult {
    return when (val parsed = parseWireguardConf(text)) {
        WireguardConfParseResult.NotWireguardConf -> EmptyProxyServerImportResult
        is WireguardConfParseResult.Imported -> ProxyServerImportResult(
            urlCount = 1,
            servers = listOf(parsed.server),
        )
        is WireguardConfParseResult.Invalid -> {
            AndroidAppLogger.warn(LogTag, "Failed to parse WireGuard / AmneziaWG conf", parsed.error)
            EmptyProxyServerImportResult
        }
    }
}
