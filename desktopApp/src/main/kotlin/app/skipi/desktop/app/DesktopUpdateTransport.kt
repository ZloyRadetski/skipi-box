// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.UpdateHttp
import features.updater.UpdateResponse
import features.updater.UpdateTransport
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** One client for every update request (a new one each time left a selector thread behind until a full collection). */
internal val DesktopReleaseHttpClient: Lazy<HttpClient> = lazy {
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(UpdateHttp.ApiConnectTimeoutMs.toLong()))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
}

/** [java.net.http.HttpClient] as an [UpdateTransport]; redirects are left to the shared code, which checks each hop. */
internal class DesktopUpdateTransport(
    private val client: Lazy<HttpClient> = DesktopReleaseHttpClient,
    private val requestTimeout: Duration,
) : UpdateTransport {
    override fun open(url: String, rangeFrom: Long, headers: Map<String, String>): UpdateResponse {
        val request = HttpRequest.newBuilder(URI(url)).timeout(requestTimeout).GET().apply {
            headers.forEach { (name, value) -> header(name, value) }
            if (rangeFrom > 0L) header("Range", "bytes=$rangeFrom-")
        }.build()
        val response = client.value.send(request, HttpResponse.BodyHandlers.ofInputStream())
        return object : UpdateResponse {
            override val status: Int = response.statusCode()
            override val url: String = response.uri().toString()
            override val contentLength: Long = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            override fun header(name: String): String? = response.headers().firstValue(name).orElse(null)
            override val body: InputStream get() = response.body()
            override fun close() = response.body().close()
        }
    }
}
