// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.getCopyTextOrNull
import features.proxy.server.model.getUrlOrNull
import kotlinx.coroutines.CancellationException

enum class ProxyServerTextCopyFormat {
    Default,
    Url,
    FullJson,
}

sealed interface ProxyServerTextCopyResult {
    data class Success(val text: String) : ProxyServerTextCopyResult
    data object Unsupported : ProxyServerTextCopyResult
    data object InvalidConfig : ProxyServerTextCopyResult
}

/** Selects the portable copy representation; hosts provide only Xray config generation. */
suspend fun copyProxyServerText(
    server: ProxyServer<*>,
    format: ProxyServerTextCopyFormat = ProxyServerTextCopyFormat.Default,
    exportFullJson: suspend () -> String,
): ProxyServerTextCopyResult = try {
    val text = when (format) {
        ProxyServerTextCopyFormat.Url -> server.getUrlOrNull()
        ProxyServerTextCopyFormat.FullJson -> exportFullJson()
        ProxyServerTextCopyFormat.Default -> when (server) {
            is ChainProxy, is StrategyGroup -> exportFullJson()
            else -> server.getCopyTextOrNull()
        }
    }
    text?.let(ProxyServerTextCopyResult::Success) ?: ProxyServerTextCopyResult.Unsupported
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Throwable) {
    ProxyServerTextCopyResult.InvalidConfig
}
