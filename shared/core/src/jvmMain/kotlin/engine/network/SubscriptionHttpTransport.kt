// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import features.subscription.SubscriptionFetchPolicy
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionHttpException
import features.subscription.SubscriptionRedirectDecision
import features.subscription.decideSubscriptionRedirect
import features.subscription.isValidManualSubscriptionUrl
import features.subscription.normalizeSkipiUserAgent
import features.subscription.parseSubscriptionHttpErrorMessage
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

const val MaxSubscriptionBodyBytes: Int = 32 * 1024 * 1024
const val MaxSubscriptionErrorBodyBytes: Int = 64 * 1024

/** One GET of a subscription or of a resource a subscription names, with everything that differs per call. */
class SubscriptionHttpRequest(
    val url: String,
    val userAgent: String,
    val timeoutMillis: Int,
    /** Device identity headers; sent to the first site only, blank names/values and a User-Agent are skipped. */
    val identityHeaders: Map<String, String> = emptyMap(),
    val agePublicKey: String? = null,
    val socksProxy: SocksProxyEndpoint? = null,
    val maxBodyBytes: Int = MaxSubscriptionBodyBytes,
    val maxErrorBodyBytes: Int = MaxSubscriptionErrorBodyBytes,
    val maxRedirects: Int = SubscriptionFetchPolicy.maxRedirects,
    val isRedirectStatus: (Int) -> Boolean = { it in 300..399 },
    val acceptCompressed: Boolean = false,
    /** Checked on every hop, the first included; returns the URI to request or throws to refuse it. */
    val urlGuard: ((URI) -> URI)? = null,
    /** The error thrown when a body is longer than the limit. */
    val responseTooLarge: (maxBytes: Int) -> Throwable = { maxBytes ->
        IllegalStateException("Subscription response is larger than $maxBytes bytes")
    },
)

/**
 * The HTTP GET both clients make for a subscription: redirects followed by hand, the device identity, the age
 * key and the URL credentials dropped when a redirect leaves the site ([decideSubscriptionRedirect]), bodies
 * and error bodies capped, headers lowercased. The caller owns the User-Agent fallback, the timeout range, age
 * decryption and the connection factory (a test seam).
 */
class SubscriptionHttpTransport(
    private val connectionFactory: (URL, Proxy?) -> HttpURLConnection = ::defaultSubscriptionConnection,
) {
    fun fetch(request: SubscriptionHttpRequest): SubscriptionFetchResponse =
        request.socksProxy.withAuthenticator { fetchWithRedirects(request) }

    private fun fetchWithRedirects(request: SubscriptionHttpRequest): SubscriptionFetchResponse {
        require(request.url.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        var requestUri = URI(request.url.trim().toIdnUrl()).withoutFragment()
        var redirects = 0
        var sendIdentity = true
        val userAgent = normalizeSkipiUserAgent(request.userAgent)

        while (true) {
            request.urlGuard?.let { guard -> requestUri = guard(requestUri).withoutFragment() }
            val connection = connectionFactory(requestUri.toURL(), request.socksProxy?.toJavaProxy()).apply {
                connectTimeout = request.timeoutMillis
                readTimeout = request.timeoutMillis
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Connection", "close")
                if (request.acceptCompressed) setRequestProperty("Accept-Encoding", "gzip, deflate")
                if (sendIdentity) {
                    request.identityHeaders.forEach { (name, value) ->
                        if (name.isNotBlank() && value.isNotBlank() && !name.equals("User-Agent", ignoreCase = true)) {
                            setRequestProperty(name, value)
                        }
                    }
                    request.agePublicKey?.let { setRequestProperty("X-Age-Public-Key", it) }
                    setEmbeddedBasicAuth(requestUri.toString())
                }
            }
            try {
                val statusCode = connection.responseCode
                if (request.isRedirectStatus(statusCode)) {
                    check(redirects < request.maxRedirects) {
                        "Subscription request exceeded ${request.maxRedirects} redirects"
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Subscription redirect does not provide Location")
                    val target = requestUri.resolve(location.trim()).withoutFragment()
                    when (val decision = decideSubscriptionRedirect(requestUri.toString(), target.toString())) {
                        SubscriptionRedirectDecision.Follow -> Unit
                        SubscriptionRedirectDecision.FollowWithoutIdentity -> sendIdentity = false
                        is SubscriptionRedirectDecision.Reject ->
                            error("Subscription redirect refused: ${decision.reason}")
                    }
                    require(target.toString().isValidManualSubscriptionUrl()) {
                        "Subscription redirect has an invalid URL"
                    }
                    requestUri = target
                    redirects += 1
                    continue
                }
                if (statusCode !in 200..299) {
                    val errorBody = runCatching {
                        connection.errorStream?.let { stream ->
                            readSubscriptionBodyAtMost(
                                stream,
                                connection.contentLengthLong.takeIf { it >= 0L },
                                connection.contentEncoding.orEmpty(),
                                request.maxErrorBodyBytes,
                                request.responseTooLarge,
                            )
                        }
                    }.getOrNull()
                    throw SubscriptionHttpException(
                        statusCode = statusCode,
                        serverMessage = parseSubscriptionHttpErrorMessage(errorBody),
                        responseBody = errorBody,
                    )
                }
                val body = readSubscriptionBodyAtMost(
                    connection.inputStream,
                    connection.contentLengthLong.takeIf { it >= 0L },
                    connection.contentEncoding.orEmpty(),
                    request.maxBodyBytes,
                    request.responseTooLarge,
                )
                val headers = connection.headerFields
                    .filterKeys { name -> name != null }
                    .mapNotNull { (name, values) ->
                        name?.lowercase()?.let { normalized ->
                            normalized to decodeRawUtf8HeaderValue(values.orEmpty().joinToString(","))
                        }
                    }
                    .toMap()
                return SubscriptionFetchResponse(body = body, headers = headers)
            } finally {
                connection.disconnect()
            }
        }
    }
}

fun defaultSubscriptionConnection(url: URL, proxy: Proxy?): HttpURLConnection {
    val connection = if (proxy != null) url.openConnection(proxy) else url.openConnection()
    return connection as HttpURLConnection
}

/** Reads a response body as UTF-8, unpacking gzip/deflate, and refuses one longer than [maxBytes] as early as it can. */
fun readSubscriptionBodyAtMost(
    rawStream: InputStream,
    contentLength: Long?,
    contentEncoding: String,
    maxBytes: Int,
    tooLarge: (Int) -> Throwable,
): String {
    if (contentLength != null && contentLength > maxBytes) {
        rawStream.close()
        throw tooLarge(maxBytes)
    }
    val stream = try {
        when (contentEncoding.trim().lowercase()) {
            "gzip", "x-gzip" -> GZIPInputStream(rawStream)
            "deflate" -> InflaterInputStream(rawStream)
            else -> rawStream
        }
    } catch (e: Throwable) {
        rawStream.close()
        throw e
    }
    return stream.use { input -> input.readUtf8Limited(maxBytes) { tooLarge(maxBytes) } }
}

/** This URI without its `#fragment`, which is never sent to a server. */
fun URI.withoutFragment(): URI {
    if (rawFragment == null) return this
    val uriString = toString()
    val hashIndex = uriString.indexOf('#')
    return if (hashIndex >= 0) URI(uriString.substring(0, hashIndex)) else this
}

/**
 * Java decodes HTTP header bytes as ISO-8859-1, but providers send titles (emoji, Cyrillic) as raw UTF-8.
 * Restores the original text when the value is a valid UTF-8 byte sequence read as Latin-1.
 */
fun decodeRawUtf8HeaderValue(value: String): String {
    if (value.all { it.code < 0x80 } || value.any { it.code > 0xFF }) return value
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching {
        decoder.decode(java.nio.ByteBuffer.wrap(value.toByteArray(StandardCharsets.ISO_8859_1))).toString()
    }.getOrDefault(value)
}
