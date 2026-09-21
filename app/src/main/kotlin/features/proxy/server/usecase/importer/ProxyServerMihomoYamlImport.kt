// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.net.URI

private const val LogTag = "ProxyServerMihomoYamlImport"

internal suspend fun parseProxyServersFromMihomoYamlConfig(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    val source = context.source
    val root = runCatching {
        newMihomoYamlParser().loadFromString(text.trimStart(ProxyImportByteOrderMark))
    }.onFailure { error ->
        AndroidAppLogger.warn(
            LogTag,
            "Failed to parse ${source.logName} as mihomo YAML",
            error,
        )
    }.getOrNull() ?: return EmptyProxyServerImportResult
    val document = root.toMihomoProxyDocument()
    val configs = document.proxyNodes
    val providers = document.providers
    if (configs.isEmpty() && providers.isEmpty()) {
        return EmptyProxyServerImportResult
    }

    val importedConfigs = configs.importAndroidMihomoProxyServers(source)
    var skippedCount = importedConfigs.rejectedCount
    val servers = importedConfigs.servers.toMutableList()

    val providerResults = providers.map { provider ->
        provider.importProvider(context)
    }
    providerResults.forEach { result ->
        skippedCount += result.skippedCount
        servers += result.servers
    }

    val importedConfigCount = configs.size + providers.size + providerResults.sumOf { it.urlCount }
    if (skippedCount > 0) {
        AndroidAppLogger.warn(
            LogTag,
            "Imported ${servers.size} ${source.logName} mihomo YAML Proxy Servers, " +
                "skipped $skippedCount/$importedConfigCount unsupported or invalid Proxy Servers",
        )
    }
    return ProxyServerImportResult(
        urlCount = importedConfigCount,
        servers = servers,
    )
}

private fun List<MihomoYamlMap>.importAndroidMihomoProxyServers(
    source: ProxyServerImportSource,
    startIndex: Int = 0,
): MihomoProxyNodeImportResult = importMihomoProxyNodes(startIndex).also { imported ->
    imported.failures.forEach { failure ->
        AndroidAppLogger.warn(
            LogTag,
            skippedMessage(source, failure.index, failure.name, failure.type, failure.reason),
            failure.error?.takeUnless { it is UnsupportedMihomoProxyException },
        )
    }
}

private suspend fun MihomoProxyProvider.importProvider(
    context: ProxyServerImportContext,
): MihomoProviderImportResult {
    return when (type) {
        "inline" -> importPayload(context.source)
        "http" -> importHttpProvider(context)
        else -> MihomoProviderImportResult.Empty
    }
}

private suspend fun MihomoProxyProvider.importHttpProvider(
    context: ProxyServerImportContext,
): MihomoProviderImportResult {
    val providerUrl = url.normalizedProviderUrlOrNull()
    if (providerUrl == null) {
        AndroidAppLogger.warn(LogTag, "Skipped mihomo proxy-provider name=$name reason=invalid provider URL")
        return importPayload(context.source)
    }
    if (providerUrl in context.fetchedProviderUrls) {
        AndroidAppLogger.warn(LogTag, "Skipped mihomo proxy-provider name=$name reason=recursive provider URL")
        return importPayload(context.source)
    }
    val fetcher = context.providerUrlFetcher
    if (fetcher == null) {
        AndroidAppLogger.warn(LogTag, "Skipped mihomo proxy-provider name=$name reason=provider URL fetcher unavailable")
        return importPayload(context.source)
    }
    val result = runCatching {
        val text = fetcher.fetch(providerUrl)
        parseProxyServersFromMihomoProviderText(
            text = text,
            parentContext = context,
            providerUrl = providerUrl,
        )
    }.onFailure { error ->
        AndroidAppLogger.warn(
            LogTag,
            "Failed to fetch mihomo proxy-provider name=$name urlHost=${providerUrl.toLogHost()}",
            error,
        )
    }.getOrNull()
    return if (result != null && result.servers.isNotEmpty()) {
        MihomoProviderImportResult(
            urlCount = 1 + result.urlCount,
            servers = result.servers,
        )
    } else {
        importPayload(context.source)
    }
}

private fun MihomoProxyProvider.importPayload(
    source: ProxyServerImportSource,
): MihomoProviderImportResult {
    if (proxyNodes.isEmpty()) return MihomoProviderImportResult.Empty
    val importedNodes = proxyNodes.importAndroidMihomoProxyServers(source)
    return MihomoProviderImportResult(
        urlCount = proxyNodes.size,
        servers = importedNodes.servers,
        skippedCount = importedNodes.rejectedCount,
    )
}

private fun String?.normalizedProviderUrlOrNull(): String? {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return uri.toString()
}

private fun String.toLogHost(): String {
    return runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "<unknown>"
}

private fun skippedMessage(
    source: ProxyServerImportSource,
    index: Int,
    name: String,
    type: String,
    reason: String,
): String {
    return "Skipped mihomo YAML Proxy Server source=${source.logName} index=$index " +
        "type=${type.ifBlank { "<blank>" }} name=${name.ifBlank { "<blank>" }} reason=$reason"
}

private fun newMihomoYamlParser(): Load {
    return Load(LoadSettings.builder().build())
}

private data class MihomoProviderImportResult(
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
    val skippedCount: Int = 0,
) {
    companion object {
        val Empty = MihomoProviderImportResult(
            urlCount = 0,
            servers = emptyList(),
        )
    }
}
