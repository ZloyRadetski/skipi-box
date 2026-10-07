// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.yaml.loadMihomoYamlDocument
import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.importer.CustomXrayConfigImportResult
import features.proxy.server.usecase.importer.MihomoProxyProvider
import features.proxy.server.usecase.importer.MihomoYamlMap
import features.proxy.server.usecase.importer.isSupportedMihomoProxyType
import features.proxy.server.usecase.importer.parseCustomXrayConfigPayload
import features.proxy.server.usecase.importer.string
import features.proxy.server.usecase.importer.toMihomoProxyServer
import features.proxy.server.usecase.importer.toMihomoProxyDocument
import features.subscription.importSubscriptionServers
import utils.decodeFlexibleBase64OrNull

/**
 * Desktop Mihomo/Clash payload importer. YAML syntax is loaded by the shared JVM object-tree
 * adapter; this host layer handles proxy mapping and already-fetched provider payloads.
 *
 * External `proxy-providers` are never fetched here. Callers pass downloaded provider texts
 * through [providerPayloads], keeping networking and timeout policy in the desktop subscription
 * adapter. Missing provider bodies are returned as [DesktopMihomoProviderRequest].
 */
internal object DesktopMihomoPayloadImporter {
    const val DefaultMaxProviderDepth: Int = 2

    fun import(
        text: String,
        providerPayloads: Map<String, String> = emptyMap(),
        maxProviderDepth: Int = DefaultMaxProviderDepth,
    ): DesktopMihomoPayloadImportResult {
        require(maxProviderDepth >= 0) { "Provider depth must not be negative" }
        return importInternal(
            text = text,
            providerPayloads = providerPayloads,
            maxProviderDepth = maxProviderDepth,
            providerDepth = 0,
            fetchedProviderUrls = emptySet(),
        ).toPublicResult()
    }

    private fun importInternal(
        text: String,
        providerPayloads: Map<String, String>,
        maxProviderDepth: Int,
        providerDepth: Int,
        fetchedProviderUrls: Set<String>,
    ): InternalMihomoImportResult {
        // Android's standard payload route tries custom Xray JSON before Mihomo YAML. A JSON
        // array is also valid YAML syntax, so preserve that format precedence explicitly.
        val rawJson = parseSubscriptionJsonConfigs(text)
        if (rawJson != null && (rawJson.servers.isNotEmpty() || rawJson.proxyEntryCount > 0)) {
            return rawJson
        }

        val root = runCatching { loadMihomoYamlDocument(text) }.getOrNull()
        val document = root?.toMihomoProxyDocument()
        if (document == null || !document.recognized) {
            val directResult = importDirectSubscriptionPayload(text)
            if (directResult.servers.isNotEmpty() || directResult.proxyEntryCount > 0) {
                return directResult
            }
            val decoded = text.filterNot(Char::isWhitespace).decodeFlexibleBase64OrNull()?.decodeToString()
            if (decoded != null && decoded != text) {
                val decodedJson = parseSubscriptionJsonConfigs(decoded)
                if (decodedJson != null && (decodedJson.servers.isNotEmpty() || decodedJson.proxyEntryCount > 0)) {
                    return decodedJson
                }

                val decodedRoot = runCatching { loadMihomoYamlDocument(decoded) }.getOrNull()
                val decodedDoc = decodedRoot?.toMihomoProxyDocument()
                if (decodedDoc != null && decodedDoc.recognized) {
                    return importInternal(
                        text = decoded,
                        providerPayloads = providerPayloads,
                        maxProviderDepth = maxProviderDepth,
                        providerDepth = providerDepth,
                        fetchedProviderUrls = fetchedProviderUrls,
                    )
                }
                val decodedDirect = importDirectSubscriptionPayload(decoded)
                if (decodedDirect.servers.isNotEmpty() || decodedDirect.proxyEntryCount > 0) {
                    return decodedDirect
                }
            }
            return directResult
        }

        val servers = mutableListOf<ProxyServer<*>>()
        val diagnostics = mutableListOf<String>()
        val pendingProviders = mutableListOf<DesktopMihomoProviderRequest>()
        val proxyNodes = document.proxyNodes + document.providers.flatMap(MihomoProxyProvider::proxyNodes)
        val inlinePayloadTexts = document.inlinePayloadTexts + document.providers.flatMap(MihomoProxyProvider::inlinePayloadTexts)
        var proxyEntryCount = proxyNodes.size
        var rejectedProxyCount = 0

        proxyNodes.forEachIndexed { index, node ->
            runCatching { node.toDesktopProxyServer() }
                .onSuccess(servers::add)
                .onFailure { error ->
                    rejectedProxyCount += 1
                    diagnostics += node.nodeDiagnostic(index, error.message.orEmpty())
                }
        }

        inlinePayloadTexts.forEach { inlinePayload ->
            val child = importInternal(
                text = inlinePayload,
                providerPayloads = providerPayloads,
                maxProviderDepth = maxProviderDepth,
                providerDepth = providerDepth,
                fetchedProviderUrls = fetchedProviderUrls,
            )
            proxyEntryCount += child.proxyEntryCount
            rejectedProxyCount += child.rejectedProxyCount
            servers += child.servers
            pendingProviders += child.pendingProviders
            diagnostics += child.diagnostics
        }

        document.providers.forEach { provider ->
            val request = provider.toDesktopProviderRequestOrNull() ?: return@forEach
            val providerText = providerPayloads[request.url]
            when {
                providerText == null -> pendingProviders += request
                providerDepth >= maxProviderDepth -> {
                    diagnostics += "Provider '${request.name}' skipped: maximum nesting depth reached."
                }

                request.url in fetchedProviderUrls -> {
                    diagnostics += "Provider '${request.name}' skipped: recursive provider URL."
                }

                else -> {
                    val child = importInternal(
                        text = providerText,
                        providerPayloads = providerPayloads,
                        maxProviderDepth = maxProviderDepth,
                        providerDepth = providerDepth + 1,
                        fetchedProviderUrls = fetchedProviderUrls + request.url,
                    )
                    proxyEntryCount += child.proxyEntryCount
                    rejectedProxyCount += child.rejectedProxyCount
                    servers += child.servers
                    pendingProviders += child.pendingProviders
                    diagnostics += child.diagnostics
                }
            }
        }

        return InternalMihomoImportResult(
            recognizedYaml = true,
            proxyEntryCount = proxyEntryCount,
            servers = servers.distinctBy(ProxyServer<*>::connectionFingerprint),
            rejectedProxyCount = rejectedProxyCount,
            pendingProviders = pendingProviders.distinctBy(DesktopMihomoProviderRequest::url),
            diagnostics = diagnostics,
        )
    }

    private fun importDirectSubscriptionPayload(text: String): InternalMihomoImportResult {
        val jsonResult = parseSubscriptionJsonConfigs(text)
        if (jsonResult != null && (jsonResult.servers.isNotEmpty() || jsonResult.proxyEntryCount > 0)) {
            return jsonResult
        }
        val imported = text.importSubscriptionServers()
        return InternalMihomoImportResult(
            recognizedYaml = false,
            proxyEntryCount = imported.urlCount,
            servers = imported.servers,
            rejectedProxyCount = imported.rejectedUrlCount,
            pendingProviders = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun parseSubscriptionJsonConfigs(text: String): InternalMihomoImportResult? {
        val imported = parseCustomXrayConfigPayload(text) as? CustomXrayConfigImportResult.Imported
            ?: return null

        return InternalMihomoImportResult(
            recognizedYaml = false,
            proxyEntryCount = imported.configCount,
            servers = imported.servers,
            rejectedProxyCount = imported.rejectedConfigCount,
            pendingProviders = emptyList(),
            diagnostics = emptyList(),
        )
    }
}

internal data class DesktopMihomoProviderRequest(
    val name: String,
    val url: String,
)

internal data class DesktopMihomoPayloadImportResult(
    /** Whether this input was recognized as a Mihomo/Clash YAML document. */
    val recognizedYaml: Boolean,
    /** Number of proxy entries encountered across the root and supplied provider payloads. */
    val proxyEntryCount: Int,
    val servers: List<ProxyServer<*>>,
    val rejectedProxyCount: Int,
    /** HTTP(S) proxy-provider URLs whose body was not supplied through [DesktopMihomoPayloadImporter.import]. */
    val pendingProviders: List<DesktopMihomoProviderRequest>,
    /** Safe, user-facing diagnostics; server secrets are never included. */
    val diagnostics: List<String>,
)

private data class InternalMihomoImportResult(
    val recognizedYaml: Boolean,
    val proxyEntryCount: Int,
    val servers: List<ProxyServer<*>>,
    val rejectedProxyCount: Int,
    val pendingProviders: List<DesktopMihomoProviderRequest>,
    val diagnostics: List<String>,
) {
    fun toPublicResult(): DesktopMihomoPayloadImportResult = DesktopMihomoPayloadImportResult(
        recognizedYaml = recognizedYaml,
        proxyEntryCount = proxyEntryCount,
        servers = servers,
        rejectedProxyCount = rejectedProxyCount,
        pendingProviders = pendingProviders,
        diagnostics = diagnostics,
    )
}

private fun MihomoYamlMap.toDesktopProxyServer(): ProxyServer<*> {
    val type = string("type").orEmpty()
    require(type.isDesktopSupportedMihomoProxyType()) { "unsupported proxy type '$type'" }
    return toMihomoProxyServer()
}

/** The current desktop Core ABI has no native AmneziaWG tunnel lifecycle. */
private fun String.isDesktopSupportedMihomoProxyType(): Boolean =
    isSupportedMihomoProxyType() &&
        !equals("amneziawg", ignoreCase = true) &&
        !equals("awg", ignoreCase = true)

private fun MihomoYamlMap.nodeDiagnostic(index: Int, reason: String): String {
    val type = string("type").orEmpty().ifBlank { "unknown" }
    val name = string("name").orEmpty().ifBlank { "#${index + 1}" }
    return "Proxy '$name' ($type) skipped: ${reason.ifBlank { "invalid configuration" }}"
}

private fun isHttpProviderUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

private fun MihomoProxyProvider.toDesktopProviderRequestOrNull(): DesktopMihomoProviderRequest? {
    val providerUrl = url?.takeIf(::isHttpProviderUrl) ?: return null
    return if (type.isBlank() || type == "http") {
        DesktopMihomoProviderRequest(name = name, url = providerUrl)
    } else {
        null
    }
}
