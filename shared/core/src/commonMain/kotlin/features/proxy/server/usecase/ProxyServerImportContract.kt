// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.model.ProxyServer

/** Portable result of recognizing proxy server entries in one import payload. */
data class ProxyServerImportResult(
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
)

/** Identifies the trust and decoding policy for an incoming proxy payload. */
enum class ProxyServerImportSource(
    val logName: String,
    val decodeBase64: Boolean,
) {
    Clipboard(logName = "clipboard", decodeBase64 = false),
    File(logName = "file", decodeBase64 = true),
    QrCode(logName = "qr_code", decodeBase64 = false),
    SubscriptionUrl(logName = "subscription_url", decodeBase64 = true),
    MihomoProxyProviderUrl(logName = "mihomo_proxy_provider_url", decodeBase64 = true),
}

/** Platform-owned network boundary for a referenced Mihomo proxy-provider. */
fun interface ProxyServerProviderUrlFetcher {
    suspend fun fetch(url: String): String
}

/** Mutable traversal state that is carried through nested provider payloads. */
data class ProxyServerImportContext(
    val source: ProxyServerImportSource,
    val providerUrlFetcher: ProxyServerProviderUrlFetcher? = null,
    val providerDepth: Int = 0,
    val fetchedProviderUrls: Set<String> = emptySet(),
)

/** One platform or format-specific parser in a portable import pipeline. */
typealias ProxyServerPayloadParser = suspend (String, ProxyServerImportContext) -> ProxyServerImportResult

/** Result for a payload that no parser recognized. */
val EmptyProxyServerImportResult = ProxyServerImportResult(
    urlCount = 0,
    servers = emptyList(),
)
