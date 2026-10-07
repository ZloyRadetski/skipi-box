// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.yaml.loadMihomoYamlDocument
import app.skipi.yaml.mihomoProviderUrlLogHost
import app.skipi.yaml.normalizeMihomoProviderUrl
import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.importer.DefaultMihomoProviderMaxDepth
import features.proxy.server.usecase.importer.MihomoProviderSkipReason
import features.proxy.server.usecase.importer.MihomoYamlImportDiagnostic
import features.proxy.server.usecase.importer.importProxyServerPayloadText
import features.proxy.server.usecase.importer.importProxyServersFromProviderPayload
import features.proxy.server.usecase.importer.mihomoYamlPayloadParser
import features.proxy.server.usecase.importer.standardProxyServerPayloadParsers
import kotlinx.coroutines.runBlocking

/**
 * Desktop Mihomo/Clash payload adapter. The shared parser owns YAML interpretation and provider
 * traversal; this host layer supplies YAML loading, URL policy, diagnostics, and the already-fetched
 * provider-body map. It never performs network requests.
 */
internal object DesktopMihomoPayloadImporter {
    const val DefaultMaxProviderDepth: Int = DefaultMihomoProviderMaxDepth

    fun import(
        text: String,
        providerPayloads: Map<String, String> = emptyMap(),
        maxProviderDepth: Int = DefaultMaxProviderDepth,
        source: ProxyServerImportSource = ProxyServerImportSource.SubscriptionUrl,
    ): DesktopMihomoPayloadImportResult {
        require(maxProviderDepth >= 0) { "Provider depth must not be negative" }

        val diagnostics = mutableListOf<String>()
        val pendingProviders = mutableListOf<DesktopMihomoProviderRequest>()
        var rejectedProxyCount = 0
        var recognizedYaml = false

        lateinit var parsers: List<ProxyServerPayloadParser>
        val mihomoParser = mihomoYamlPayloadParser(
            loadYamlTree = ::loadMihomoYamlDocument,
            normalizeProviderUrl = ::normalizeMihomoProviderUrl,
            parseProviderPayload = { providerText, parentContext, providerUrl ->
                importProxyServersFromProviderPayload(
                    text = providerText,
                    parentContext = parentContext,
                    providerUrl = providerUrl,
                    parsers = parsers,
                    maxProviderDepth = maxProviderDepth,
                )
            },
            providerLogHost = ::mihomoProviderUrlLogHost,
            diagnosticSink = { diagnostic ->
                when (diagnostic) {
                    is MihomoYamlImportDiagnostic.NodeRejected -> {
                        rejectedProxyCount += 1
                        diagnostics += diagnostic.toDesktopDiagnostic()
                    }

                    is MihomoYamlImportDiagnostic.ProviderSkipped -> {
                        val missingBody = diagnostic.error as? MissingDesktopProviderBody
                        if (diagnostic.reason == MihomoProviderSkipReason.FetchFailed && missingBody != null) {
                            pendingProviders += DesktopMihomoProviderRequest(
                                name = diagnostic.providerName,
                                url = missingBody.url,
                            )
                        } else {
                            diagnostics += diagnostic.toDesktopDiagnostic()
                        }
                    }

                    is MihomoYamlImportDiagnostic.YamlLoadFailed,
                    is MihomoYamlImportDiagnostic.Summary -> Unit
                }
            },
        )
        val trackedMihomoParser: ProxyServerPayloadParser = { payload, context ->
            mihomoParser(payload, context).also { result ->
                if (result.urlCount > 0) recognizedYaml = true
            }
        }

        parsers = standardProxyServerPayloadParsers(
            mihomoYamlParser = trackedMihomoParser,
            onRejectedCustomJson = { context, imported ->
                rejectedProxyCount += imported.rejectedConfigCount
                diagnostics += "Imported ${imported.servers.size} ${context.source.logName} custom JSON configs, " +
                    "skipped ${imported.rejectedConfigCount}/${imported.configCount} failed configs"
            },
            onInvalidWireguard = { _, _ ->
                rejectedProxyCount += 1
                diagnostics += "Failed to parse WireGuard / AmneziaWG conf"
            },
            onUrlFailure = { context, failure ->
                rejectedProxyCount += 1
                val protocol = failure.url.substringBefore("://", missingDelimiterValue = "").ifBlank { "<blank>" }
                diagnostics += "Failed to import proxy server URL source=${context.source.logName} " +
                    "index=${failure.index} protocol=$protocol length=${failure.url.length}"
            },
        )

        val context = ProxyServerImportContext(
            source = source,
            providerUrlFetcher = features.proxy.server.usecase.ProxyServerProviderUrlFetcher { url ->
                providerPayloads[url] ?: throw MissingDesktopProviderBody(url)
            },
        )
        val imported = runBlocking {
            importProxyServerPayloadText(
                text = text,
                context = context,
                parsers = parsers,
            )
        }

        return DesktopMihomoPayloadImportResult(
            recognizedYaml = recognizedYaml,
            proxyEntryCount = imported.urlCount,
            servers = imported.servers,
            rejectedProxyCount = rejectedProxyCount,
            pendingProviders = pendingProviders.distinctBy(DesktopMihomoProviderRequest::url),
            diagnostics = diagnostics,
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

private class MissingDesktopProviderBody(val url: String) : RuntimeException()

private fun MihomoYamlImportDiagnostic.NodeRejected.toDesktopDiagnostic(): String {
    val failure = this.failure
    val name = failure.name.ifBlank { "#${failure.index + 1}" }
    val type = failure.type.ifBlank { "unknown" }
    val reason = failure.reason.ifBlank { "invalid configuration" }
    return "Proxy '$name' ($type) skipped: $reason"
}

private fun MihomoYamlImportDiagnostic.ProviderSkipped.toDesktopDiagnostic(): String = when (reason) {
    MihomoProviderSkipReason.InvalidUrl -> "Provider '$providerName' skipped: invalid provider URL."
    MihomoProviderSkipReason.RecursiveUrl -> "Provider '$providerName' skipped: recursive provider URL."
    MihomoProviderSkipReason.FetcherUnavailable -> "Provider '$providerName' skipped: provider URL fetcher unavailable."
    MihomoProviderSkipReason.FetchFailed -> "Provider '$providerName' skipped: provider URL fetch failed."
}
