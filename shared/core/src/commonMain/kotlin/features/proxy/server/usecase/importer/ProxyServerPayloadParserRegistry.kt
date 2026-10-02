// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser

/** Creates the shared format route while leaving YAML syntax decoding to the host adapter. */
fun standardProxyServerPayloadParsers(
    mihomoYamlParser: ProxyServerPayloadParser,
    onRejectedCustomJson: (
        context: ProxyServerImportContext,
        result: CustomXrayConfigImportResult.Imported,
    ) -> Unit = { _, _ -> },
    onInvalidWireguard: (context: ProxyServerImportContext, error: Throwable) -> Unit = { _, _ -> },
    onUrlFailure: (context: ProxyServerImportContext, failure: ProxyServerUrlImportFailure) -> Unit = { _, _ -> },
): List<ProxyServerPayloadParser> = listOf(
    { text, context -> parseCustomXrayProxyServerPayload(text, context, onRejectedCustomJson) },
    mihomoYamlParser,
    { text, context -> parseWireguardProxyServerPayload(text, context, onInvalidWireguard) },
    { text, context -> parseProxyUrlProxyServerPayload(text, context, onUrlFailure) },
)

fun parseCustomXrayProxyServerPayload(
    text: String,
    context: ProxyServerImportContext,
    onRejected: (ProxyServerImportContext, CustomXrayConfigImportResult.Imported) -> Unit,
): ProxyServerImportResult = when (val result = parseCustomXrayConfigPayload(text)) {
    is CustomXrayConfigImportResult.Imported -> {
        if (result.rejectedConfigCount > 0) onRejected(context, result)
        ProxyServerImportResult(urlCount = result.configCount, servers = result.servers)
    }
    CustomXrayConfigImportResult.InvalidJson,
    CustomXrayConfigImportResult.NoConfigObjects,
    CustomXrayConfigImportResult.NotJson -> EmptyProxyServerImportResult
}

fun parseWireguardProxyServerPayload(
    text: String,
    context: ProxyServerImportContext,
    onInvalid: (ProxyServerImportContext, Throwable) -> Unit,
): ProxyServerImportResult = when (val result = parseWireguardConf(text)) {
    WireguardConfParseResult.NotWireguardConf -> EmptyProxyServerImportResult
    is WireguardConfParseResult.Invalid -> {
        onInvalid(context, result.error)
        EmptyProxyServerImportResult
    }
    is WireguardConfParseResult.Imported -> ProxyServerImportResult(urlCount = 1, servers = listOf(result.server))
}

fun parseProxyUrlProxyServerPayload(
    text: String,
    context: ProxyServerImportContext,
    onFailure: (ProxyServerImportContext, ProxyServerUrlImportFailure) -> Unit,
): ProxyServerImportResult {
    val result = importProxyServersFromUrls(text) { failure -> onFailure(context, failure) }
    return ProxyServerImportResult(urlCount = result.urlCount, servers = result.servers)
}
