// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.decodeSkipiConfigPayloadOrNull
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import features.subscription.SubscriptionServerImportResult
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.subscriptionMetadata
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Desktop HTTP adapter for subscriptions. Parsing and validation stay in the shared core. */
class DesktopSubscriptionFetcher(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(DefaultConnectTimeout)
        // Redirects are followed explicitly below so automatic provider/config
        // requests can be checked before every hop.
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val maxResponseBytes: Int = MaxDesktopSubscriptionResponseBytes,
) {
    init {
        require(maxResponseBytes > 0) { "Subscription response limit must be positive" }
    }

    fun fetchAndImport(
        url: String,
        userAgent: String = DefaultDesktopSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
    ): DesktopSubscriptionUpdate {
        val response = fetch(url, userAgent, timeout)
        val imported = importMihomoOrStandardPayload(
            rootPayload = response.body,
            userAgent = userAgent,
            timeout = timeout,
        )
        return DesktopSubscriptionUpdate(
            response = response,
            importResult = imported.result,
            metadata = response.subscriptionMetadata(),
            importDiagnostics = imported.diagnostics,
        )
    }

    fun fetch(
        url: String,
        userAgent: String = DefaultDesktopSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
    ): SubscriptionFetchResponse = fetchResponse(
        url = url,
        userAgent = userAgent,
        timeout = timeout,
        automaticResource = false,
    )

    /**
     * Resolves an optional routing profile advertised by a subscription.
     * Failure is intentionally surfaced to the caller but must not invalidate
     * a successfully imported proxy list, matching Android's nonfatal policy.
     */
    fun resolveEmbeddedConfig(
        metadata: SubscriptionMetadata,
        userAgent: String = DefaultDesktopSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
    ): DesktopResolvedEmbeddedConfig? {
        val embedded = metadata.embeddedConfig ?: return null
        val content = if (embedded.isUrl) {
            fetchAutomaticResource(embedded.payload, userAgent, timeout).body
        } else {
            embedded.payload.decodeSkipiConfigPayloadOrNull() ?: embedded.payload.trim()
        }.trim()
        return content.takeIf(String::isNotEmpty)?.let { resolved ->
            DesktopResolvedEmbeddedConfig(
                content = resolved,
                sourceUrl = if (embedded.isUrl) embedded.payload.trim() else "",
                activate = embedded.activate,
            )
        }
    }

    /**
     * Downloads only the HTTP provider bodies requested by a recognized Mihomo
     * payload. URLs never come from the UI directly; they are declared by the
     * downloaded document and are therefore checked and bounded by depth/count.
     */
    private fun importMihomoOrStandardPayload(
        rootPayload: String,
        userAgent: String,
        timeout: Duration,
    ): DesktopFetchedSubscriptionImport {
        var imported = DesktopMihomoPayloadImporter.import(rootPayload)
        if (!imported.recognizedYaml) {
            return DesktopFetchedSubscriptionImport(
                result = SubscriptionServerImportResult(
                    urlCount = imported.proxyEntryCount,
                    servers = imported.servers,
                    rejectedUrlCount = imported.rejectedProxyCount,
                ),
                diagnostics = imported.diagnostics,
            )
        }

        val providerBodies = linkedMapOf<String, String>()
        val attemptedProviders = mutableSetOf<String>()
        val diagnostics = mutableListOf<String>()
        repeat(DesktopMihomoPayloadImporter.DefaultMaxProviderDepth) {
            val pending = imported.pendingProviders
                .filter { request -> request.url !in providerBodies && request.url !in attemptedProviders }
                .take((MaxProviderRequests - attemptedProviders.size).coerceAtLeast(0))
            if (pending.isEmpty()) return@repeat
            pending.forEach { request ->
                attemptedProviders += request.url
                runCatching { fetchAutomaticResource(request.url, userAgent, timeout) }
                    .onSuccess { provider -> providerBodies[request.url] = provider.body }
                    .onFailure {
                        diagnostics += "Провайдер '${request.name.ifBlank { "без названия" }}' не удалось загрузить; остальные серверы сохранены."
                    }
            }
            imported = DesktopMihomoPayloadImporter.import(
                text = rootPayload,
                providerPayloads = providerBodies,
            )
            if (attemptedProviders.size >= MaxProviderRequests) return@repeat
        }
        val unavailableProviders = imported.pendingProviders
            .filter { request -> request.url !in providerBodies }
            .map { request -> "Провайдер '${request.name.ifBlank { "без названия" }}' пока недоступен." }
        return DesktopFetchedSubscriptionImport(
            result = SubscriptionServerImportResult(
                urlCount = imported.proxyEntryCount,
                servers = imported.servers,
                rejectedUrlCount = imported.rejectedProxyCount,
            ),
            diagnostics = (imported.diagnostics + diagnostics + unavailableProviders).distinct(),
        )
    }

    /**
     * Fetches a URL that was declared inside an already downloaded subscription.
     * Unlike [fetch], it is not a direct UI action, therefore each redirect hop
     * is rejected when it resolves to loopback, private, link-local or metadata
     * infrastructure.
     */
    private fun fetchAutomaticResource(
        url: String,
        userAgent: String,
        timeout: Duration,
    ): SubscriptionFetchResponse = fetchResponse(
        url = url,
        userAgent = userAgent,
        timeout = timeout,
        automaticResource = true,
    )

    private fun fetchResponse(
        url: String,
        userAgent: String,
        timeout: Duration,
        automaticResource: Boolean,
    ): SubscriptionFetchResponse {
        require(url.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        var requestUri = URI(url.trim())
        var redirects = 0

        while (true) {
            if (automaticResource) {
                requestUri = requireSafeAutomaticSubscriptionResourceUrl(requestUri.toString())
            }
            val request = HttpRequest.newBuilder(requestUri)
                .timeout(timeout)
                .header("User-Agent", userAgent.ifBlank { DefaultDesktopSubscriptionUserAgent })
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            // A caller may inject an HttpClient configured to follow redirects.
            // Re-check its final URI even though the production client disables it.
            if (automaticResource) {
                requireSafeAutomaticSubscriptionResourceUrl(response.uri().toString())
            }

            if (response.statusCode() in HttpRedirectStatusCodes) {
                response.body().close()
                check(redirects < MaxSubscriptionRedirects) {
                    "Subscription request exceeded $MaxSubscriptionRedirects redirects"
                }
                val location = response.headers().firstValue("location").orElseThrow {
                    IllegalStateException("Subscription redirect does not provide Location")
                }
                requestUri = requestUri.resolve(location.trim())
                require(requestUri.toString().isValidManualSubscriptionUrl()) {
                    "Subscription redirect has an invalid URL"
                }
                redirects += 1
                continue
            }

            val responseBody = response.readUtf8BodyAtMost(maxResponseBytes)
            if (response.statusCode() !in 200..299) {
                throw DesktopSubscriptionHttpException(
                    statusCode = response.statusCode(),
                    responseBody = responseBody,
                )
            }
            return SubscriptionFetchResponse(
                body = responseBody,
                headers = response.headers().map().mapValues { (_, values) -> values.joinToString(",") },
            )
        }
    }
}

/**
 * Validates an auxiliary URL found in subscription metadata or a Mihomo provider.
 * Manual root subscription URLs deliberately use the existing, less restrictive
 * UI policy in [DesktopSubscriptionFetcher.fetch].
 */
internal fun requireSafeAutomaticSubscriptionResourceUrl(
    value: String,
    resolveAddresses: (String) -> Array<InetAddress> = InetAddress::getAllByName,
): URI {
    val normalized = value.trim()
    require(normalized.isValidManualSubscriptionUrl()) { "Automatic subscription resource has an invalid URL" }
    val uri = URI(normalized)
    require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
        "Automatic subscription resource must use HTTP or HTTPS"
    }
    require(uri.userInfo.isNullOrBlank()) { "Automatic subscription resource must not contain user credentials" }
    require(uri.port == -1 || uri.port in 1..65_535) { "Automatic subscription resource has an invalid port" }

    val host = uri.host
        ?.trim()
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.trimEnd('.')
        ?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Automatic subscription resource has no host")
    require(!host.isKnownLocalOrMetadataHost()) {
        "Automatic subscription resource points to a local or metadata host"
    }
    val addresses = runCatching { resolveAddresses(host) }.getOrElse { error ->
        throw IllegalArgumentException("Automatic subscription resource host cannot be resolved", error)
    }
    require(addresses.isNotEmpty()) { "Automatic subscription resource host cannot be resolved" }
    require(addresses.none(InetAddress::isUnsafeAutomaticSubscriptionAddress)) {
        "Automatic subscription resource points to a private or local network address"
    }
    return uri
}

private fun HttpResponse<InputStream>.readUtf8BodyAtMost(maxBytes: Int): String {
    headers().firstValue("content-length").orElse(null)
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }
        ?.let { contentLength ->
            if (contentLength > maxBytes) {
                body().close()
                throw DesktopSubscriptionResponseTooLargeException(maxBytes)
            }
        }

    return body().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBytes) throw DesktopSubscriptionResponseTooLargeException(maxBytes)
            output.write(buffer, 0, count)
        }
        String(output.toByteArray(), StandardCharsets.UTF_8)
    }
}

private fun String.isKnownLocalOrMetadataHost(): Boolean {
    val normalized = trim().trimEnd('.').lowercase()
    return normalized == "localhost" ||
        normalized.endsWith(".localhost") ||
        normalized.endsWith(".local") ||
        normalized in KnownMetadataHosts
}

private fun InetAddress.isUnsafeAutomaticSubscriptionAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
        return true
    }
    val bytes = address.map { byte -> byte.toInt() and 0xFF }
    return when (this) {
        is Inet4Address -> bytes.isUnsafeAutomaticSubscriptionIpv4()
        else -> bytes.isUnsafeAutomaticSubscriptionIpv6()
    }
}

private fun List<Int>.isUnsafeAutomaticSubscriptionIpv4(): Boolean {
    if (size != 4) return true
    val first = this[0]
    val second = this[1]
    val third = this[2]
    return first == 0 ||
        first == 10 ||
        first == 127 ||
        (first == 100 && second in 64..127) ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 0 && third in setOf(0, 2)) ||
        (first == 192 && second == 168) ||
        (first == 198 && second in 18..19) ||
        (first == 198 && second == 51 && third == 100) ||
        (first == 203 && second == 0 && third == 113) ||
        first >= 224
}

private fun List<Int>.isUnsafeAutomaticSubscriptionIpv6(): Boolean {
    if (size != 16) return true
    val ipv4Mapped = take(10).all { it == 0 } && this[10] == 0xFF && this[11] == 0xFF
    if (ipv4Mapped) return drop(12).isUnsafeAutomaticSubscriptionIpv4()
    val first = this[0]
    val second = this[1]
    val third = this[2]
    val fourth = this[3]
    return (first and 0xFE) == 0xFC || // fc00::/7 unique local
        (first == 0x20 && second == 0x01 && third == 0x0D && fourth == 0xB8) // documentation range
}

class DesktopSubscriptionHttpException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Subscription server returned HTTP $statusCode")

class DesktopSubscriptionResponseTooLargeException(
    val maxBytes: Int,
) : IllegalStateException("Subscription response exceeds $maxBytes bytes")

data class DesktopSubscriptionUpdate(
    val response: SubscriptionFetchResponse,
    val importResult: SubscriptionServerImportResult,
    val metadata: SubscriptionMetadata,
    /** Non-fatal YAML/provider diagnostics; an imported group is never erased because of these. */
    val importDiagnostics: List<String> = emptyList(),
)

data class DesktopResolvedEmbeddedConfig(
    val content: String,
    val sourceUrl: String,
    val activate: Boolean,
)

const val DefaultDesktopSubscriptionUserAgent = "SKIPI Desktop"

private val DefaultConnectTimeout: Duration = Duration.ofSeconds(10)
private val DefaultRequestTimeout: Duration = Duration.ofSeconds(30)
private const val MaxProviderRequests = 12
const val MaxDesktopSubscriptionResponseBytes = 8 * 1024 * 1024
private const val MaxSubscriptionRedirects = 5
private val HttpRedirectStatusCodes = setOf(301, 302, 303, 307, 308)
private val KnownMetadataHosts = setOf(
    "metadata",
    "metadata.google.internal",
    "metadata.google",
    "instance-data",
    "instance-data.ec2.internal",
)

private data class DesktopFetchedSubscriptionImport(
    val result: SubscriptionServerImportResult,
    val diagnostics: List<String>,
)
