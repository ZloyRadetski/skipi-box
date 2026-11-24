// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import engine.network.TunnelNetworks
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Opens update requests through the tunnel when it is up (the app is excluded from its own VPN), never following redirects. */
internal class AndroidUpdateTransport(
    private val context: Context?,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
) : UpdateTransport {
    override fun open(url: String, rangeFrom: Long, headers: Map<String, String>): UpdateResponse {
        val connection = TunnelNetworks.withLocalProxyAuthenticator {
            TunnelNetworks.openHttpConnection(context, URL(url)).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (rangeFrom > 0L) setRequestProperty("Range", "bytes=$rangeFrom-")
                // A SOCKS proxy authenticates lazily, so the request must be sent while the authenticator is set.
                responseCode
            }
        }
        return ConnectionResponse(url, connection)
    }

    private class ConnectionResponse(
        override val url: String,
        private val connection: HttpURLConnection,
    ) : UpdateResponse {
        override val status: Int get() = connection.responseCode
        override val contentLength: Long get() = connection.contentLengthLong
        override fun header(name: String): String? = connection.getHeaderField(name)
        override val body: InputStream get() = connection.inputStream
        override fun close() = connection.disconnect()
    }
}
