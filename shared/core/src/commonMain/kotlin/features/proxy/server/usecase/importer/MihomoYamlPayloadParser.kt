// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser

/** A host-neutral diagnostic emitted while importing one Mihomo YAML document. */
sealed interface MihomoYamlImportDiagnostic {
    val source: ProxyServerImportSource

    data class YamlLoadFailed(
        override val source: ProxyServerImportSource,
        val error: Throwable,
    ) : MihomoYamlImportDiagnostic

    data class NodeRejected(
        override val source: ProxyServerImportSource,
        val failure: MihomoProxyNodeImportFailure,
    ) : MihomoYamlImportDiagnostic

    data class ProviderSkipped(
        override val source: ProxyServerImportSource,
        val providerName: String,
        val reason: MihomoProviderSkipReason,
        val providerHost: String? = null,
        val error: Throwable? = null,
    ) : MihomoYamlImportDiagnostic

    data class Summary(
        override val source: ProxyServerImportSource,
        val serverCount: Int,
        val rejectedCount: Int,
        val urlCount: Int,
    ) : MihomoYamlImportDiagnostic
}

/** Provider conditions that make Mihomo fall back to its inline proxy payload. */
enum class MihomoProviderSkipReason {
    InvalidUrl,
    RecursiveUrl,
    FetcherUnavailable,
    FetchFailed,
}

/**
 * Creates the Mihomo YAML parser for the shared ordered payload pipeline.
 * YAML syntax, URL policy, host logging, and recursive payload parsing are
 * supplied by the caller; provider fetches use the existing import context.
 */
fun mihomoYamlPayloadParser(
    loadYamlTree: (String) -> Any?,
    normalizeProviderUrl: (String?) -> String?,
    parseProviderPayload: suspend (
        text: String,
        parentContext: ProxyServerImportContext,
        normalizedUrl: String,
    ) -> ProxyServerImportResult,
    providerLogHost: (String) -> String = { "<unknown>" },
    diagnosticSink: (MihomoYamlImportDiagnostic) -> Unit = {},
): ProxyServerPayloadParser = parser@{ text, context ->
    val root = try {
        loadYamlTree(text)
    } catch (error: Throwable) {
        diagnosticSink(MihomoYamlImportDiagnostic.YamlLoadFailed(context.source, error))
        null
    } ?: return@parser EmptyProxyServerImportResult

    val document = root.toMihomoProxyDocument()
    val configs = document.proxyNodes
    val providers = document.providers
    if (configs.isEmpty() && providers.isEmpty()) {
        return@parser EmptyProxyServerImportResult
    }

    val importedConfigs = configs.importMihomoProxyNodes()
    importedConfigs.failures.forEach { failure ->
        diagnosticSink(MihomoYamlImportDiagnostic.NodeRejected(context.source, failure))
    }
    var rejectedCount = importedConfigs.rejectedCount
    val servers = importedConfigs.servers.toMutableList()

    val providerResults = providers.map { provider ->
        provider.import(context, normalizeProviderUrl, parseProviderPayload, providerLogHost, diagnosticSink)
    }
    providerResults.forEach { result ->
        rejectedCount += result.rejectedCount
        servers += result.servers
    }

    val urlCount = configs.size + providers.size + providerResults.sumOf(MihomoProviderImportResult::urlCount)
    if (rejectedCount > 0) {
        diagnosticSink(
            MihomoYamlImportDiagnostic.Summary(
                source = context.source,
                serverCount = servers.size,
                rejectedCount = rejectedCount,
                urlCount = urlCount,
            ),
        )
    }
    ProxyServerImportResult(urlCount = urlCount, servers = servers)
}

private suspend fun MihomoProxyProvider.import(
    context: ProxyServerImportContext,
    normalizeProviderUrl: (String?) -> String?,
    parseProviderPayload: suspend (
        text: String,
        parentContext: ProxyServerImportContext,
        normalizedUrl: String,
    ) -> ProxyServerImportResult,
    providerLogHost: (String) -> String,
    diagnosticSink: (MihomoYamlImportDiagnostic) -> Unit,
): MihomoProviderImportResult = when (type) {
    "inline" -> importInlinePayload(context.source, diagnosticSink)
    "http" -> importHttpProvider(context, normalizeProviderUrl, parseProviderPayload, providerLogHost, diagnosticSink)
    else -> MihomoProviderImportResult.Empty
}

private suspend fun MihomoProxyProvider.importHttpProvider(
    context: ProxyServerImportContext,
    normalizeProviderUrl: (String?) -> String?,
    parseProviderPayload: suspend (
        text: String,
        parentContext: ProxyServerImportContext,
        normalizedUrl: String,
    ) -> ProxyServerImportResult,
    providerLogHost: (String) -> String,
    diagnosticSink: (MihomoYamlImportDiagnostic) -> Unit,
): MihomoProviderImportResult {
    val providerUrl = normalizeProviderUrl(url)
    if (providerUrl == null) {
        diagnosticSink(providerSkipped(context, MihomoProviderSkipReason.InvalidUrl))
        return importInlinePayload(context.source, diagnosticSink)
    }
    if (providerUrl in context.fetchedProviderUrls) {
        diagnosticSink(providerSkipped(context, MihomoProviderSkipReason.RecursiveUrl))
        return importInlinePayload(context.source, diagnosticSink)
    }
    val fetcher = context.providerUrlFetcher
    if (fetcher == null) {
        diagnosticSink(providerSkipped(context, MihomoProviderSkipReason.FetcherUnavailable))
        return importInlinePayload(context.source, diagnosticSink)
    }

    val remoteResult = try {
        parseProviderPayload(
            fetcher.fetch(providerUrl),
            context,
            providerUrl,
        )
    } catch (error: Throwable) {
        diagnosticSink(
            providerSkipped(
                context = context,
                reason = MihomoProviderSkipReason.FetchFailed,
                providerHost = providerLogHost(providerUrl),
                error = error,
            ),
        )
        null
    }

    return if (remoteResult != null && remoteResult.servers.isNotEmpty()) {
        MihomoProviderImportResult(
            urlCount = 1 + remoteResult.urlCount,
            servers = remoteResult.servers,
        )
    } else {
        importInlinePayload(context.source, diagnosticSink)
    }
}

private fun MihomoProxyProvider.importInlinePayload(
    source: ProxyServerImportSource,
    diagnosticSink: (MihomoYamlImportDiagnostic) -> Unit,
): MihomoProviderImportResult {
    if (proxyNodes.isEmpty()) return MihomoProviderImportResult.Empty
    val importedNodes = proxyNodes.importMihomoProxyNodes()
    importedNodes.failures.forEach { failure ->
        diagnosticSink(MihomoYamlImportDiagnostic.NodeRejected(source, failure))
    }
    return MihomoProviderImportResult(
        urlCount = proxyNodes.size,
        servers = importedNodes.servers,
        rejectedCount = importedNodes.rejectedCount,
    )
}

private fun MihomoProxyProvider.providerSkipped(
    context: ProxyServerImportContext,
    reason: MihomoProviderSkipReason,
    providerHost: String? = null,
    error: Throwable? = null,
): MihomoYamlImportDiagnostic.ProviderSkipped = MihomoYamlImportDiagnostic.ProviderSkipped(
    source = context.source,
    providerName = name,
    reason = reason,
    providerHost = providerHost,
    error = error,
)

private data class MihomoProviderImportResult(
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
    val rejectedCount: Int = 0,
) {
    companion object {
        val Empty = MihomoProviderImportResult(
            urlCount = 0,
            servers = emptyList(),
        )
    }
}
