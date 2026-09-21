// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

private const val LogTag = "ProxyServerJsonImport"

internal suspend fun parseProxyServersFromJsonConfig(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    val source = context.source
    val imported = parseCustomXrayConfigPayload(text) as? CustomXrayConfigImportResult.Imported
        ?: return EmptyProxyServerImportResult
    if (imported.rejectedConfigCount > 0) {
        AndroidAppLogger.warn(
            LogTag,
            "Imported ${imported.servers.size} ${source.logName} custom JSON configs, " +
                "skipped ${imported.rejectedConfigCount}/${imported.configCount} failed configs",
        )
    }
    return ProxyServerImportResult(
        urlCount = imported.configCount,
        servers = imported.servers,
    )
}
