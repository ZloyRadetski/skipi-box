// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser

/** Android supplies the SnakeYAML parser and logging adapters to the shared pipeline. */
internal suspend fun parseProxyServersFromPayloads(
    payloads: List<String>,
    context: ProxyServerImportContext,
): ProxyServerImportResult = parseProxyServerPayloads(
    payloads = payloads,
    context = context,
    parsers = AndroidProxyServerImportParsers,
)

internal suspend fun parseProxyServersFromMihomoProviderText(
    text: String,
    parentContext: ProxyServerImportContext,
    providerUrl: String,
): ProxyServerImportResult = importProxyServersFromProviderPayload(
    text = text,
    parentContext = parentContext,
    providerUrl = providerUrl,
    parsers = AndroidProxyServerImportParsers,
)

private val AndroidProxyServerImportParsers: List<ProxyServerPayloadParser> = listOf(
    ::parseProxyServersFromJsonConfig,
    ::parseProxyServersFromMihomoYamlConfig,
    ::parseProxyServersFromWireguardConf,
    ::parseProxyServersFromUrls,
)
