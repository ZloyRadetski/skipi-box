// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

private const val LogTag = "WireguardConfImport"

internal suspend fun parseProxyServersFromWireguardConf(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult = parseWireguardProxyServerPayload(text, context) { _, error ->
    AndroidAppLogger.warn(LogTag, "Failed to parse WireGuard / AmneziaWG conf", error)
}
