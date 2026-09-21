// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser
import utils.decodeFlexibleBase64OrNull

/** Prevents recursive Mihomo provider references from creating unbounded imports. */
const val DefaultMihomoProviderMaxDepth = 2

/**
 * Runs ordered parsers against the raw payload and, where allowed by [source],
 * its decoded Base64 variant. The first recognized payload wins.
 */
suspend fun importProxyServerPayloadText(
    text: String,
    context: ProxyServerImportContext,
    parsers: List<ProxyServerPayloadParser>,
): ProxyServerImportResult = parseProxyServerPayloads(
    payloads = text.toProxyServerImportPayloads(context.source),
    context = context,
    parsers = parsers,
)

/** Executes the parser chain for already materialized payload variants. */
suspend fun parseProxyServerPayloads(
    payloads: List<String>,
    context: ProxyServerImportContext,
    parsers: List<ProxyServerPayloadParser>,
): ProxyServerImportResult {
    for (payload in payloads) {
        for (parser in parsers) {
            val result = parser(payload, context)
            if (result.urlCount > 0) return result
        }
    }
    return EmptyProxyServerImportResult
}

/**
 * Imports a fetched Mihomo provider body with a child context. Networking is
 * deliberately supplied by the platform through [ProxyServerImportContext].
 */
suspend fun importProxyServersFromProviderPayload(
    text: String,
    parentContext: ProxyServerImportContext,
    providerUrl: String,
    parsers: List<ProxyServerPayloadParser>,
    maxProviderDepth: Int = DefaultMihomoProviderMaxDepth,
): ProxyServerImportResult {
    if (parentContext.providerDepth >= maxProviderDepth) return EmptyProxyServerImportResult
    val context = parentContext.copy(
        source = ProxyServerImportSource.MihomoProxyProviderUrl,
        providerDepth = parentContext.providerDepth + 1,
        fetchedProviderUrls = parentContext.fetchedProviderUrls + providerUrl,
    )
    return importProxyServerPayloadText(
        text = text,
        context = context,
        parsers = parsers,
    )
}

/** Produces the raw and optional decoded variants in the established import order. */
fun String.toProxyServerImportPayloads(source: ProxyServerImportSource): List<String> =
    if (source.decodeBase64) {
        listOfNotNull(decodeProxyServerImportBase64OrNull(), this).distinct()
    } else {
        listOf(this)
    }

private fun String.decodeProxyServerImportBase64OrNull(): String? {
    val normalized = trimStart(ProxyImportByteOrderMark).filterNot(Char::isWhitespace)
    if (normalized.isBlank()) return null
    return normalized.decodeFlexibleBase64OrNull()?.decodeToString()
}

/** The UTF-8 BOM occasionally present in subscription and configuration payloads. */
const val ProxyImportByteOrderMark = '\uFEFF'
