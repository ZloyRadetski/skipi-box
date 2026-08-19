// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.usecase.importer.importPayloads
import features.proxy.server.usecase.importer.parseProxyServersFromPayloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun importProxyServersFromText(
    text: String,
    source: ProxyServerImportSource,
    providerUrlFetcher: ProxyServerProviderUrlFetcher? = null,
): ProxyServerImportResult = withContext(Dispatchers.Default) {
    val context = ProxyServerImportContext(
        source = source,
        providerUrlFetcher = providerUrlFetcher,
    )
    parseProxyServersFromPayloads(
        payloads = text.importPayloads(source),
        context = context,
    )
}
