// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import app.skipi.yaml.loadMihomoYamlDocument
import app.skipi.yaml.mihomoProviderUrlLogHost
import app.skipi.yaml.normalizeMihomoProviderUrl
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser

private const val LogTag = "ProxyServerMihomoYamlImport"

/** Keeps the existing direct test seam while delegating all parsing and traversal to shared/core. */
internal suspend fun parseProxyServersFromMihomoYamlConfig(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult = AndroidMihomoYamlPayloadParser(text, context)

private val AndroidMihomoYamlPayloadParser: ProxyServerPayloadParser = mihomoYamlPayloadParser(
    loadYamlTree = ::loadMihomoYamlDocument,
    normalizeProviderUrl = ::normalizeMihomoProviderUrl,
    parseProviderPayload = ::parseProxyServersFromMihomoProviderText,
    providerLogHost = ::mihomoProviderUrlLogHost,
    diagnosticSink = ::logMihomoYamlImportDiagnostic,
)

private fun logMihomoYamlImportDiagnostic(diagnostic: MihomoYamlImportDiagnostic) {
    when (diagnostic) {
        is MihomoYamlImportDiagnostic.YamlLoadFailed -> {
            AndroidAppLogger.warn(
                LogTag,
                "Failed to parse ${diagnostic.source.logName} as mihomo YAML",
                diagnostic.error,
            )
        }

        is MihomoYamlImportDiagnostic.NodeRejected -> {
            val failure = diagnostic.failure
            AndroidAppLogger.warn(
                LogTag,
                skippedMessage(
                    diagnostic.source,
                    failure.index,
                    failure.name,
                    failure.type,
                    failure.reason,
                ),
                failure.error?.takeUnless { it is UnsupportedMihomoProxyException },
            )
        }

        is MihomoYamlImportDiagnostic.ProviderSkipped -> {
            when (diagnostic.reason) {
                MihomoProviderSkipReason.InvalidUrl ->
                    AndroidAppLogger.warn(
                        LogTag,
                        "Skipped mihomo proxy-provider name=${diagnostic.providerName} reason=invalid provider URL",
                    )

                MihomoProviderSkipReason.RecursiveUrl ->
                    AndroidAppLogger.warn(
                        LogTag,
                        "Skipped mihomo proxy-provider name=${diagnostic.providerName} reason=recursive provider URL",
                    )

                MihomoProviderSkipReason.FetcherUnavailable ->
                    AndroidAppLogger.warn(
                        LogTag,
                        "Skipped mihomo proxy-provider name=${diagnostic.providerName} " +
                            "reason=provider URL fetcher unavailable",
                    )

                MihomoProviderSkipReason.FetchFailed ->
                    AndroidAppLogger.warn(
                        LogTag,
                        "Failed to fetch mihomo proxy-provider name=${diagnostic.providerName} " +
                            "urlHost=${diagnostic.providerHost ?: "<unknown>"}",
                        diagnostic.error,
                    )
            }
        }

        is MihomoYamlImportDiagnostic.Summary -> {
            AndroidAppLogger.warn(
                LogTag,
                "Imported ${diagnostic.serverCount} ${diagnostic.source.logName} mihomo YAML Proxy Servers, " +
                    "skipped ${diagnostic.rejectedCount}/${diagnostic.urlCount} unsupported or invalid Proxy Servers",
            )
        }
    }
}

private fun skippedMessage(
    source: features.proxy.server.usecase.ProxyServerImportSource,
    index: Int,
    name: String,
    type: String,
    reason: String,
): String {
    return "Skipped mihomo YAML Proxy Server source=${source.logName} index=$index " +
        "type=${type.ifBlank { "<blank>" }} name=${name.ifBlank { "<blank>" }} reason=$reason"
}
