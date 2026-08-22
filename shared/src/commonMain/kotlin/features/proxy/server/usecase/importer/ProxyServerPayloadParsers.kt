// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser

private const val MaxMihomoProviderDepth = 2

suspend fun parseProxyServersFromPayloads(
    payloads: List<String>,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    for (payload in payloads) {
        for (parser in ProxyServerImportParsers) {
            val result = parser(payload, context)
            if (result.urlCount > 0) {
                return result
            }
        }
    }
    return EmptyProxyServerImportResult
}

suspend fun parseProxyServersFromMihomoProviderText(
    text: String,
    parentContext: ProxyServerImportContext,
    providerUrl: String,
): ProxyServerImportResult {
    if (parentContext.providerDepth >= MaxMihomoProviderDepth) {
        return EmptyProxyServerImportResult
    }
    val context = parentContext.copy(
        source = ProxyServerImportSource.MihomoProxyProviderUrl,
        providerDepth = parentContext.providerDepth + 1,
        fetchedProviderUrls = parentContext.fetchedProviderUrls + providerUrl,
    )
    return parseProxyServersFromPayloads(
        payloads = text.importPayloads(context.source),
        context = context,
    )
}

private val ProxyServerImportParsers: List<ProxyServerPayloadParser> = listOf(
    ::parseProxyServersFromJsonConfig,
    ::parseProxyServersFromMihomoYamlConfig,
    ::parseProxyServersFromUrls,
)
