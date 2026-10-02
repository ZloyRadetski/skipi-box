// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.importer.importProxyServerPayloadText as parsePayloadText
import features.proxy.server.usecase.importer.importProxyServersFromProviderPayload as parseProviderPayload

/** Shared import entry point: decodes source variants and routes through the ordered parser chain. */
suspend fun importProxyServerPayload(
    text: String,
    context: ProxyServerImportContext,
    parsers: List<ProxyServerPayloadParser>,
): ProxyServerImportResult = parsePayloadText(text, context, parsers)

/** Shared recursive-provider entry point with the same format route and traversal safeguards. */
suspend fun importProxyServerProviderPayload(
    text: String,
    parentContext: ProxyServerImportContext,
    providerUrl: String,
    parsers: List<ProxyServerPayloadParser>,
): ProxyServerImportResult = parseProviderPayload(
    text = text,
    parentContext = parentContext,
    providerUrl = providerUrl,
    parsers = parsers,
)
