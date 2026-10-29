// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AppLog
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

private const val LogTag = "WireguardConfImport"

suspend fun parseProxyServersFromWireguardConf(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult = parseWireguardProxyServerPayload(text, context) { _, error ->
    AppLog.warn(LogTag, "Failed to parse WireGuard / AmneziaWG conf", error)
}
