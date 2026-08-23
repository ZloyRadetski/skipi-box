// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.model.ProxyServer

internal data class ProxyServerImportResult(
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
)

internal enum class ProxyServerImportSource(
    val logName: String,
    val decodeBase64: Boolean,
) {
    Clipboard(logName = "clipboard", decodeBase64 = false),
    File(logName = "file", decodeBase64 = true),
    QrCode(logName = "qr_code", decodeBase64 = false),
    SubscriptionUrl(logName = "subscription_url", decodeBase64 = true),
    MihomoProxyProviderUrl(logName = "mihomo_proxy_provider_url", decodeBase64 = true),
}

internal fun interface ProxyServerProviderUrlFetcher {
    suspend fun fetch(url: String): String
}

internal data class ProxyServerImportContext(
    val source: ProxyServerImportSource,
    val providerUrlFetcher: ProxyServerProviderUrlFetcher? = null,
    val providerDepth: Int = 0,
    val fetchedProviderUrls: Set<String> = emptySet(),
)

internal typealias ProxyServerPayloadParser = suspend (String, ProxyServerImportContext) -> ProxyServerImportResult

internal val EmptyProxyServerImportResult = ProxyServerImportResult(
    urlCount = 0,
    servers = emptyList(),
)
