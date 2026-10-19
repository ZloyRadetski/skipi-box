// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.net.Network
import engine.network.TunnelNetworks
import features.resources.runtime.ResourceFileDownloadAttemptFactory
import features.resources.runtime.ResourceFileDownloadConnection
import features.resources.runtime.ResourceFileDownloadConnectionOpener
import features.resources.runtime.ResourceFileDownloadTransfer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketException
import java.net.URI

internal class AndroidResourceFileDownloader(
    private val context: Context? = null,
) {
    fun download(
        url: String,
        target: File,
        proxy: AndroidResourceFileDownloadProxy? = null,
        userAgent: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        if (proxy != null) {
            try {
                proxy.withAuthenticator {
                    transfer(target, proxy, userAgent).download(url, onProgress)
                }
                return
            } catch (_: IOException) {
                AndroidResourceFileLogger.info("Proxy download failed, falling back to direct connection")
            }
        }
        transfer(target, null, userAgent).download(url, onProgress)
    }

    private fun transfer(
        target: File,
        proxy: AndroidResourceFileDownloadProxy?,
        userAgent: String?,
    ): ResourceFileDownloadTransfer = ResourceFileDownloadTransfer(
        attempts = ResourceFileDownloadAttemptFactory {
            val vpnNetwork = AndroidVpnNetworkSnapshot(TunnelNetworks.locateVpnNetwork(context))
            ResourceFileDownloadConnectionOpener { uri ->
                AndroidResourceFileDownloadConnection(
                    uri = uri,
                    proxy = proxy,
                    userAgent = userAgent,
                    vpnNetwork = vpnNetwork,
                )
            }
        },
        publishAtomically = { write -> writeAtomically(target, write) },
        isCancelled = AndroidResourceFileDownloadCancellation::isCancelled,
        throwIfCancelled = AndroidResourceFileDownloadCancellation::throwIfCancelled,
    )
}

internal data class AndroidResourceFileDownloadProxy(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

private class AndroidVpnNetworkSnapshot(var network: Network?)

/** Adapts a single Android HTTP connection to the shared transfer contract. */
private class AndroidResourceFileDownloadConnection(
    private val uri: URI,
    private val proxy: AndroidResourceFileDownloadProxy?,
    private val userAgent: String?,
    private val vpnNetwork: AndroidVpnNetworkSnapshot,
) : ResourceFileDownloadConnection {
    private var connection = uri.toUrlConnection(proxy, userAgent, vpnNetwork.network)

    override val responseCode: Int
        get() {
            AndroidResourceFileDownloadCancellation.track(connection)
            AndroidResourceFileDownloadCancellation.throwIfCancelled()
            return try {
                connection.responseCode
            } catch (error: SocketException) {
                if (vpnNetwork.network != null) {
                    AndroidResourceFileLogger.info("VPN socket binding failed ($error), falling back to direct network")
                    vpnNetwork.network = null
                    AndroidResourceFileDownloadCancellation.untrack(connection)
                    connection.disconnect()
                    connection = uri.toUrlConnection(proxy, userAgent, null)
                    AndroidResourceFileDownloadCancellation.track(connection)
                    connection.responseCode
                } else {
                    throw error
                }
            }
        }

    override val location: String?
        get() = connection.getHeaderField("Location")

    override val contentLength: Long
        get() = connection.contentLengthLong

    override val inputStream
        get() = connection.inputStream

    override fun disconnect() {
        AndroidResourceFileDownloadCancellation.untrack(connection)
        connection.disconnect()
    }
}

private fun URI.toUrlConnection(
    proxy: AndroidResourceFileDownloadProxy?,
    userAgent: String? = null,
    vpnNetwork: Network? = null,
): HttpURLConnection {
    val url = toURL()
    val connection = when {
        proxy != null -> url.openConnection(proxy.toJavaProxy())
        // Explicit SOCKS proxy wins; otherwise prefer the VPN tunnel when up.
        vpnNetwork != null -> runCatching { vpnNetwork.openConnection(url) }.getOrElse { url.openConnection() }
        else -> url.openConnection()
    }
    return (connection as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = false
        requestMethod = "GET"
        setRequestProperty("User-Agent", userAgent ?: ResourceFileDefaultUserAgent)
    }
}

private fun AndroidResourceFileDownloadProxy.toJavaProxy(): Proxy {
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}

private fun <T> AndroidResourceFileDownloadProxy?.withAuthenticator(block: () -> T): T {
    if (this == null || username.isBlank()) return block()
    return TunnelNetworks.withSocksProxyAuthenticator(
        port = port,
        username = username,
        password = password,
        block = block,
    )
}

private const val ResourceFileDefaultUserAgent = "SKIPI/1.0"

internal fun overallProgress(
    fileIndex: Int,
    fileCount: Int,
    downloadedBytes: Long,
    totalBytes: Long,
): Int? {
    if (totalBytes <= 0L || fileCount <= 0) return null
    val completedFiles = fileIndex.coerceAtLeast(0).toDouble()
    val currentFileProgress = (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
    return (((completedFiles + currentFileProgress) / fileCount.toDouble()) * 100).toInt().coerceIn(0, 100)
}
