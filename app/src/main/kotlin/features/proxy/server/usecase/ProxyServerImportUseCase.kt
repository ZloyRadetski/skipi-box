// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.usecase.importer.importPayloads
import features.proxy.server.usecase.importer.parseProxyServersFromPayloads

internal suspend fun importProxyServersFromText(
    text: String,
    source: ProxyServerImportSource,
    providerUrlFetcher: ProxyServerProviderUrlFetcher? = null,
): ProxyServerImportResult {
    val context = ProxyServerImportContext(
        source = source,
        providerUrlFetcher = providerUrlFetcher,
    )
    return parseProxyServersFromPayloads(
        payloads = text.importPayloads(source),
        context = context,
    )
}
