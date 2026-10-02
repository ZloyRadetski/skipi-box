// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import app.skipi.app.proxy.importProxyServerPayload
import app.skipi.app.proxy.importProxyServerProviderPayload
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.importer.standardProxyServerPayloadParsers

/** Android supplies the SnakeYAML parser and logging adapters to the shared pipeline. */
internal suspend fun parseProxyServersFromPayloads(
    payloads: List<String>,
    context: ProxyServerImportContext,
): ProxyServerImportResult = parseProxyServerPayloads(
    payloads = payloads,
    context = context,
    parsers = AndroidProxyServerImportParsers,
)

internal suspend fun importProxyServersFromPayloadText(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult = importProxyServerPayload(
    text = text,
    context = context,
    parsers = AndroidProxyServerImportParsers,
)

internal suspend fun parseProxyServersFromMihomoProviderText(
    text: String,
    parentContext: ProxyServerImportContext,
    providerUrl: String,
): ProxyServerImportResult = importProxyServerProviderPayload(
    text = text,
    parentContext = parentContext,
    providerUrl = providerUrl,
    parsers = AndroidProxyServerImportParsers,
)

private val AndroidProxyServerImportParsers: List<ProxyServerPayloadParser> = standardProxyServerPayloadParsers(
    mihomoYamlParser = ::parseProxyServersFromMihomoYamlConfig,
    onRejectedCustomJson = { context, imported ->
        AndroidAppLogger.warn(
            "ProxyServerJsonImport",
            "Imported ${imported.servers.size} ${context.source.logName} custom JSON configs, " +
                "skipped ${imported.rejectedConfigCount}/${imported.configCount} failed configs",
        )
    },
    onInvalidWireguard = { _, error ->
        AndroidAppLogger.warn("WireguardConfImport", "Failed to parse WireGuard / AmneziaWG conf", error)
    },
    onUrlFailure = { context, failure ->
        val protocol = failure.url.substringBefore("://", missingDelimiterValue = "").ifBlank { "<blank>" }
        AndroidAppLogger.warn(
            "ProxyServerImport",
            "Failed to import proxy server URL source=${context.source.logName} " +
                "index=${failure.index} protocol=$protocol length=${failure.url.length}",
            failure.error,
        )
    },
)
