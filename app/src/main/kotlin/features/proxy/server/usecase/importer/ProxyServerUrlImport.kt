// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource

internal suspend fun parseProxyServersFromUrls(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    val result = importProxyServersFromUrls(text) { failure ->
        AndroidAppLogger.warn(
            ProxyServerImportLogTag,
            failure.url.importFailureMessage(index = failure.index, source = context.source),
            failure.error,
        )
    }
    return ProxyServerImportResult(
        urlCount = result.urlCount,
        servers = result.servers,
    )
}

private fun String.importFailureMessage(
    index: Int,
    source: ProxyServerImportSource,
): String {
    val protocol = substringBefore("://", missingDelimiterValue = "").ifBlank { "<blank>" }
    return "Failed to import proxy server URL source=${source.logName} index=$index protocol=$protocol length=$length"
}

private const val ProxyServerImportLogTag = "ProxyServerImport"
