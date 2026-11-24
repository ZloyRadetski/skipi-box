// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import java.io.Closeable
import java.io.InputStream
import java.io.IOException
import java.net.URI

/** One HTTP answer. Redirects are not followed by the transport: callers check every hop themselves. */
interface UpdateResponse : Closeable {
    val status: Int

    /** The URL this answer came from. */
    val url: String

    /** The body length the server announced, or -1 when it did not. */
    val contentLength: Long

    fun header(name: String): String?

    /** Opened on first use; closing the response closes it. */
    val body: InputStream
}

/** The platform's way to make a GET: Android binds to the tunnel, Desktop uses java.net.http. */
interface UpdateTransport {
    /** GETs [url] without following redirects; [rangeFrom] > 0 asks for the rest of the file. */
    fun open(url: String, rangeFrom: Long = 0L, headers: Map<String, String> = emptyMap()): UpdateResponse
}

private val RedirectStatusCodes = setOf(301, 302, 303, 307, 308)

/** Release files come from GitHub over HTTPS, and a redirect may only lead to GitHub's own asset hosts. */
fun isTrustedReleaseHost(uri: URI): Boolean {
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return host == "github.com" || host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
}

/**
 * GETs [url] and follows redirects by hand, so every hop (and the URL an answer reports) goes through [allowed]
 * before the next request is made. The returned response is not a redirect and must be closed by the caller.
 */
internal fun UpdateTransport.openFollowing(
    url: String,
    rangeFrom: Long = 0L,
    headers: Map<String, String> = emptyMap(),
    allowed: (URI) -> Boolean,
): UpdateResponse {
    var current = url
    repeat(UpdateHttp.MaxRedirects + 1) {
        val uri = try {
            URI(current)
        } catch (error: Exception) {
            throw IOException("Invalid update link", error)
        }
        if (!allowed(uri)) throw IOException("The update link is not allowed: ${uri.scheme}://${uri.host}")
        val response = open(current, rangeFrom, headers)
        try {
            val reported = runCatching { URI(response.url) }.getOrNull()
            if (reported == null || !allowed(reported)) throw IOException("The update was redirected away from GitHub")
            if (response.status !in RedirectStatusCodes) return response
            val location = response.header("Location")?.takeIf(String::isNotBlank)
                ?: throw IOException("Redirect without a location")
            current = uri.resolve(location).toString()
        } catch (error: Throwable) {
            response.close()
            throw error
        }
        response.close()
    }
    throw IOException("Too many redirects")
}
