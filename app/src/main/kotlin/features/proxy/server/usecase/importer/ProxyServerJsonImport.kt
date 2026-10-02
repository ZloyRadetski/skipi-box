// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

private const val LogTag = "ProxyServerJsonImport"

internal suspend fun parseProxyServersFromJsonConfig(
    text: String,
    context: ProxyServerImportContext,
): ProxyServerImportResult {
    return parseCustomXrayProxyServerPayload(text, context) { sourceContext, imported ->
        AndroidAppLogger.warn(
            LogTag,
            "Imported ${imported.servers.size} ${sourceContext.source.logName} custom JSON configs, " +
                "skipped ${imported.rejectedConfigCount}/${imported.configCount} failed configs",
        )
    }
}
