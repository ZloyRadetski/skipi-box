// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_subscription_provider_failed
import app.skipi.ui.resources.desktop_subscription_provider_unnamed
import engine.network.AutomaticSubscriptionResourceUrlGuard
import engine.network.SocksProxyEndpoint
import engine.network.SubscriptionHttpRequest
import engine.network.SubscriptionHttpTransport
import engine.network.defaultSubscriptionConnection
import engine.network.readSubscriptionBodyAtMost
import engine.network.decodeRawUtf8HeaderValue
import engine.network.requireSafeAutomaticSubscriptionResourceUrl
import engine.network.withoutFragment
import engine.network.withAuthenticator
import engine.network.readUtf8Limited
import engine.network.setEmbeddedBasicAuth
import features.subscription.SubscriptionHttpException
import features.subscription.SubscriptionRedirectDecision
import features.subscription.parseSubscriptionHttpErrorMessage
import features.subscription.decideSubscriptionRedirect
import engine.network.toIdnUrl
import engine.network.toBasicAuthHeaderOrNull
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.normalizeSkipiUserAgent
import features.config.isSingleHttpUrl
import features.config.resolveSkipiConfigPayload
import features.proxy.server.usecase.ProxyServerProviderUrlFetcher
import features.subscription.KageSubscriptionAgeCrypto
import features.subscription.SubscriptionAgeCrypto
import features.subscription.SubscriptionAgePolicy
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionFetchPolicy
import features.subscription.SubscriptionMetadata
import features.subscription.SubscriptionServerImportResult
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.subscriptionMetadata
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Transport settings shared by the root, provider, and embedded-config requests in one refresh. */
internal data class DesktopSubscriptionRefreshFetchOptions(
    val timeout: Duration,
    val proxy: DesktopSubscriptionSocksProxy?,
    val deviceHeaders: Map<String, String>,
    val ageSecretKey: String,
    val fallbackUserAgent: String,
)

/** Applies Desktop's configured legacy User-Agent fallback without changing request identity. */
internal suspend fun DesktopSubscriptionFetcher.fetchSubscriptionRefreshResponse(
    url: String,
    storedUserAgent: String,
    options: DesktopSubscriptionRefreshFetchOptions,
    automaticResourceUrl: Boolean = false,
): SubscriptionFetchResponse = withContext(Dispatchers.IO) {
    fetchResponse(
        url = url,
        userAgent = storedUserAgent.trim().ifBlank { options.fallbackUserAgent },
        timeout = options.timeout,
        proxy = options.proxy,
        deviceHeaders = options.deviceHeaders,
        ageSecretKey = options.ageSecretKey,
        automaticResourceUrl = automaticResourceUrl,
    )
}

/** Desktop HTTP adapter for subscriptions. Parsing and validation stay in the shared core. */
class DesktopSubscriptionFetcher(
    /** Built on the first fetch: a client starts a selector thread and loads the TLS stack. */
    private val client: Lazy<HttpClient> = DesktopSubscriptionHttpClient,
    private val maxResponseBytes: Int = MaxDesktopSubscriptionResponseBytes,
    /**
     * Optional test seam for opening HTTP connections, e.g. through a local SOCKS proxy.
     */
    private val urlConnectionFactory: (URL, Proxy?) -> HttpURLConnection = ::defaultSubscriptionConnection,
    private val ageCrypto: SubscriptionAgeCrypto = KageSubscriptionAgeCrypto,
) {
    private val transport = SubscriptionHttpTransport(urlConnectionFactory)

    init {
        require(maxResponseBytes > 0) { "Subscription response limit must be positive" }
    }

    fun fetchAndImport(
        url: String,
        userAgent: String = DefaultSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
        proxy: DesktopSubscriptionSocksProxy? = null,
        deviceHeaders: Map<String, String> = emptyMap(),
        ageSecretKey: String = "",
    ): DesktopSubscriptionUpdate {
        val response = fetchResponse(
            url = url,
            userAgent = userAgent,
            timeout = timeout,
            proxy = proxy,
            deviceHeaders = deviceHeaders,
            ageSecretKey = ageSecretKey,
        )
        val imported = importMihomoOrStandardPayload(
            rootPayload = response.body,
            userAgent = userAgent,
            timeout = timeout,
            proxy = proxy,
            deviceHeaders = deviceHeaders,
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
        userAgent: String = DefaultSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
        proxy: DesktopSubscriptionSocksProxy? = null,
        deviceHeaders: Map<String, String> = emptyMap(),
        ageSecretKey: String = "",
    ): SubscriptionFetchResponse = fetchResponse(
        url = url,
        userAgent = userAgent,
        timeout = timeout,
        proxy = proxy,
        deviceHeaders = deviceHeaders,
        ageSecretKey = ageSecretKey,
    )

    /** Fetches one direct subscription response with the shared Android request/decryption policy. */
    fun fetchResponse(
        url: String,
        userAgent: String = DefaultSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
        proxy: DesktopSubscriptionSocksProxy? = null,
        deviceHeaders: Map<String, String> = emptyMap(),
        ageSecretKey: String = "",
        /** Marks a URL a downloaded subscription named: refused when it or a redirect hop is a private address. */
        automaticResourceUrl: Boolean = false,
    ): SubscriptionFetchResponse = fetchResponseInternal(
        url = url,
        userAgent = userAgent,
        timeout = timeout,
        automaticResource = false,
        proxy = proxy,
        deviceHeaders = deviceHeaders,
        ageSecretKey = ageSecretKey,
        urlGuard = AutomaticSubscriptionResourceUrlGuard.takeIf { automaticResourceUrl },
    )

    /**
     * Resolves an optional routing profile advertised by a subscription.
     * Failure is intentionally surfaced to the caller but must not invalidate
     * a successfully imported proxy list, matching Android's nonfatal policy.
     */
    fun resolveEmbeddedConfig(
        metadata: SubscriptionMetadata,
        userAgent: String = DefaultSubscriptionUserAgent,
        timeout: Duration = DefaultRequestTimeout,
        proxy: DesktopSubscriptionSocksProxy? = null,
        deviceHeaders: Map<String, String> = emptyMap(),
    ): DesktopResolvedEmbeddedConfig? {
        val embedded = metadata.embeddedConfig ?: return null
        // A link payload may hold the address percent-encoded or in Base64 rather than the document.
        val inline = if (embedded.isUrl) null else embedded.payload.resolveSkipiConfigPayload()
        val url = if (embedded.isUrl) embedded.payload.trim() else inline?.takeIf(String::isSingleHttpUrl)
        val content = if (url != null) {
            fetchAutomaticResource(
                url = url,
                userAgent = userAgent,
                timeout = timeout,
                proxy = proxy,
                deviceHeaders = deviceHeaders,
            ).body
        } else {
            inline.orEmpty()
        }.trim()
        return content.takeIf(String::isNotEmpty)?.let { resolved ->
            DesktopResolvedEmbeddedConfig(
                content = resolved,
                sourceUrl = url.orEmpty(),
                activate = embedded.activate,
            )
        }
    }

    /**
     * Fetches HTTP provider bodies as the shared importer encounters them. URLs
     * come from the downloaded document; shared traversal stops ancestor cycles
     * and limits recursive depth, while the host fetch path checks automatic
     * resource URLs and redirect hops.
     */
    private fun importMihomoOrStandardPayload(
        rootPayload: String,
        userAgent: String,
        timeout: Duration,
        proxy: DesktopSubscriptionSocksProxy?,
        deviceHeaders: Map<String, String> = emptyMap(),
    ): DesktopFetchedSubscriptionImport = runBlocking {
        val imported = DesktopMihomoPayloadImporter.importWithProviderFetcher(
            text = rootPayload,
            providerUrlFetcher = ProxyServerProviderUrlFetcher { providerUrl ->
                fetchAutomaticResource(
                    url = providerUrl,
                    userAgent = userAgent,
                    timeout = timeout,
                    proxy = proxy,
                    deviceHeaders = deviceHeaders,
                ).body
            },
            onProviderFetchFailureMessage = { providerName ->
                desktopText(
                    Res.string.desktop_subscription_provider_failed,
                    providerName.ifBlank { desktopText(Res.string.desktop_subscription_provider_unnamed) },
                )
            },
        )
        DesktopFetchedSubscriptionImport(
            result = SubscriptionServerImportResult(
                urlCount = imported.proxyEntryCount,
                servers = imported.servers,
                rejectedUrlCount = imported.rejectedProxyCount,
            ),
            diagnostics = imported.diagnostics.distinct(),
        )
    }

    /**
     * Fetches a URL that was declared inside an already downloaded subscription.
     * Unlike [fetch], it is not a direct UI action, therefore each redirect hop
     * is rejected when it resolves to loopback, private, link-local or metadata
     * infrastructure.
     */
    internal fun fetchAutomaticResource(
        url: String,
        userAgent: String,
        timeout: Duration,
        proxy: DesktopSubscriptionSocksProxy?,
        deviceHeaders: Map<String, String> = emptyMap(),
    ): SubscriptionFetchResponse = fetchResponseInternal(
        url = url,
        userAgent = userAgent,
        timeout = timeout,
        automaticResource = true,
        proxy = proxy,
        deviceHeaders = deviceHeaders,
    )

    private fun fetchResponseInternal(
        url: String,
        userAgent: String,
        timeout: Duration,
        automaticResource: Boolean,
        proxy: DesktopSubscriptionSocksProxy?,
        deviceHeaders: Map<String, String> = emptyMap(),
        ageSecretKey: String = "",
        urlGuard: ((URI) -> URI)? = null,
    ): SubscriptionFetchResponse {
        val normalizedTimeout = if (automaticResource) {
            timeout
        } else {
            Duration.ofSeconds(
                SubscriptionFetchPolicy.timeoutSeconds(
                    timeout.seconds.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                ).toLong(),
            )
        }
        val agePublicKey = if (automaticResource) {
            null
        } else {
            SubscriptionAgePolicy.publicKeyHeaderValue(ageSecretKey, ageCrypto)
        }
        val maxRedirects = if (automaticResource) {
            MaxAutomaticResourceRedirects
        } else {
            SubscriptionFetchPolicy.maxRedirects
        }
        val maxBodyBytes = if (automaticResource) maxResponseBytes else MaxDesktopManualSubscriptionResponseBytes
        return if (automaticResource && proxy == null) {
            fetchViaHttpClient(
                url = url,
                userAgent = userAgent,
                timeout = normalizedTimeout,
                automaticResource = automaticResource,
                maxRedirects = maxRedirects,
                maxBodyBytes = maxBodyBytes,
                deviceHeaders = deviceHeaders,
            )
        } else {
            fetchViaConnection(
                url = url,
                userAgent = userAgent,
                timeout = normalizedTimeout,
                automaticResource = automaticResource,
                maxRedirects = maxRedirects,
                maxBodyBytes = maxBodyBytes,
                agePublicKey = agePublicKey,
                ageSecretKey = ageSecretKey,
                proxy = proxy,
                deviceHeaders = deviceHeaders,
                urlGuard = urlGuard,
            )
        }
    }

    private fun fetchViaHttpClient(
        url: String,
        userAgent: String,
        timeout: Duration,
        automaticResource: Boolean,
        maxRedirects: Int,
        maxBodyBytes: Int,
        deviceHeaders: Map<String, String> = emptyMap(),
    ): SubscriptionFetchResponse {
        require(url.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        var requestUri = URI(url.trim().toIdnUrl()).withoutFragment()
        var redirects = 0
        var sendIdentity = true

        while (true) {
            if (automaticResource) {
                requestUri = requireSafeAutomaticSubscriptionResourceUrl(requestUri.toString()).withoutFragment()
            }
            val requestBuilder = HttpRequest.newBuilder(requestUri)
                .timeout(timeout)
                .header("User-Agent", normalizeSkipiUserAgent(userAgent))
                .header("Accept-Encoding", "gzip, deflate")
            if (sendIdentity) {
                deviceHeaders.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank() && !name.equals("User-Agent", ignoreCase = true)) {
                        requestBuilder.header(name, value)
                    }
                }
                requestUri.toBasicAuthHeaderOrNull()?.let { authHeader ->
                    requestBuilder.header("Authorization", authHeader)
                }
            }
            val request = requestBuilder.GET().build()
            val response = client.value.send(request, HttpResponse.BodyHandlers.ofInputStream())

            // A caller may inject an HttpClient configured to follow redirects.
            // Re-check its final URI even though the production client disables it.
            if (automaticResource) {
                try {
                    requireSafeAutomaticSubscriptionResourceUrl(response.uri().toString())
                } catch (error: Throwable) {
                    // The unsafe response is refused; its connection must not stay open behind the exception.
                    runCatching { response.body().close() }
                    throw error
                }
            }

            if (response.statusCode().isRedirect(automaticResource)) {
                response.body().close()
                check(redirects < maxRedirects) {
                    "Subscription request exceeded $maxRedirects redirects"
                }
                val location = response.headers().firstValue("location").orElseThrow {
                    IllegalStateException("Subscription redirect does not provide Location")
                }
                val target = requestUri.resolve(location.trim()).withoutFragment()
                if (applyRedirectPolicy(requestUri, target)) sendIdentity = false
                requestUri = target
                require(requestUri.toString().isValidManualSubscriptionUrl()) {
                    "Subscription redirect has an invalid URL"
                }
                redirects += 1
                continue
            }

            val responseBody = response.readUtf8BodyAtMost(maxBodyBytes)
            if (response.statusCode() !in 200..299) {
                throw SubscriptionHttpException(
                    statusCode = response.statusCode(),
                    serverMessage = parseSubscriptionHttpErrorMessage(responseBody),
                    responseBody = responseBody,
                )
            }
            return SubscriptionFetchResponse(
                body = responseBody,
                headers = response.headers().map().mapValues { (_, values) -> decodeRawUtf8HeaderValue(values.joinToString(",")) },
            )
        }
    }

    private fun fetchViaConnection(
        url: String,
        userAgent: String,
        timeout: Duration,
        automaticResource: Boolean,
        maxRedirects: Int,
        maxBodyBytes: Int,
        agePublicKey: String?,
        ageSecretKey: String,
        proxy: DesktopSubscriptionSocksProxy?,
        deviceHeaders: Map<String, String> = emptyMap(),
        urlGuard: ((URI) -> URI)? = null,
    ): SubscriptionFetchResponse {
        val response = transport.fetch(
            SubscriptionHttpRequest(
                url = url,
                userAgent = userAgent,
                timeoutMillis = timeout.toMillis().coerceIn(if (automaticResource) 1_000 else 3_000, 600_000).toInt(),
                identityHeaders = deviceHeaders,
                agePublicKey = agePublicKey,
                socksProxy = proxy?.toEndpoint(),
                maxBodyBytes = maxBodyBytes,
                maxRedirects = maxRedirects,
                isRedirectStatus = { it.isRedirect(automaticResource) },
                acceptCompressed = automaticResource,
                urlGuard = urlGuard ?: AutomaticSubscriptionResourceUrlGuard.takeIf { automaticResource },
                responseTooLarge = ::DesktopSubscriptionResponseTooLargeException,
            ),
        )
        return if (automaticResource) {
            response
        } else {
            response.copy(body = SubscriptionAgePolicy.decryptResponseBody(response.body, ageSecretKey, ageCrypto))
        }
    }
}

internal val LocalSubscriptionProxyHosts: Set<String> = setOf(
    "127.0.0.1",
    "localhost",
    "::1",
    "[::1]",
)

/**
 * Loopback SOCKS proxy endpoint used for subscription updates through the active tunnel.
 *
 * This deliberately rejects LAN and remote endpoints: the setting is for SKIPI's own
 * local Xray SOCKS inbound, not a general-purpose proxy configuration.
 */
data class DesktopSubscriptionSocksProxy(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    init {
        require(host == host.trim() && host in LocalSubscriptionProxyHosts) {
            "Subscription SOCKS proxy host must be a loopback address"
        }
        require(port in 1..65_535) { "Subscription SOCKS proxy port must be in 1..65535" }
    }


    internal fun toJavaProxy(): Proxy = SocksProxyEndpoint(host, port).toJavaProxy()

    internal fun toEndpoint(): SocksProxyEndpoint = SocksProxyEndpoint(host, port, username, password)
}

@Deprecated("Use DesktopSubscriptionSocksProxy instead", ReplaceWith("DesktopSubscriptionSocksProxy"))
typealias DesktopSubscriptionHttpProxy = DesktopSubscriptionSocksProxy

internal fun <T> DesktopSubscriptionSocksProxy?.withAuthenticator(block: () -> T): T =
    if (this == null) block() else toEndpoint().withAuthenticator(block)

/** The one direct client of the default desktop subscription path, shared by every fetcher and built on first use. */
internal val DesktopSubscriptionHttpClient: Lazy<HttpClient> = lazy { createDesktopSubscriptionHttpClient() }

/** Builds the direct client used for the default desktop subscription path. */
internal fun createDesktopSubscriptionHttpClient(): HttpClient {
    return HttpClient.newBuilder()
        .connectTimeout(DefaultConnectTimeout)
        // Redirects are followed explicitly below so automatic provider/config
        // requests can be checked before every hop.
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
}

private fun HttpResponse<InputStream>.readUtf8BodyAtMost(maxBytes: Int): String {
    val contentEncoding = headers().firstValue("content-encoding").orElse("")
    val contentLength = headers().firstValue("content-length").orElse(null)
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }
    return readSubscriptionBodyAtMost(
        rawStream = body(),
        contentLength = contentLength,
        contentEncoding = contentEncoding,
        maxBytes = maxBytes,
        tooLarge = ::DesktopSubscriptionResponseTooLargeException,
    )
}

/**
 * Applies the redirect policy Android uses ([decideSubscriptionRedirect]): no downgrade to cleartext, no
 * leaving http(s), no hop to an internal address. Returns true when the target is another site, which must
 * not receive the hardware id, the age key or the credentials of the first request.
 */
private fun applyRedirectPolicy(from: URI, to: URI): Boolean =
    when (val decision = decideSubscriptionRedirect(from.toString(), to.toString())) {
        SubscriptionRedirectDecision.Follow -> false
        SubscriptionRedirectDecision.FollowWithoutIdentity -> true
        is SubscriptionRedirectDecision.Reject -> error("Subscription redirect refused: ${decision.reason}")
    }

private fun Int.isRedirect(automaticResource: Boolean): Boolean =
    if (automaticResource) this in HttpRedirectStatusCodes else this in 300..399

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


private val DefaultConnectTimeout: Duration = Duration.ofSeconds(10)
private val DefaultRequestTimeout: Duration = Duration.ofSeconds(SubscriptionFetchPolicy.defaultTimeoutSeconds.toLong())
const val MaxDesktopSubscriptionResponseBytes = 8 * 1024 * 1024
/** Same ceiling as the Android manual fetch (AndroidSubscriptionFetcher.MaxSubscriptionBodyBytes). */
const val MaxDesktopManualSubscriptionResponseBytes = 32 * 1024 * 1024
private const val MaxAutomaticResourceRedirects = 5
private val HttpRedirectStatusCodes = setOf(301, 302, 303, 307, 308)
private data class DesktopFetchedSubscriptionImport(
    val result: SubscriptionServerImportResult,
    val diagnostics: List<String>,
)
