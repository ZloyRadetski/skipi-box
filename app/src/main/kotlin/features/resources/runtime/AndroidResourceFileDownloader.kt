// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import engine.network.SocksProxyEndpoint
import engine.network.withSocksProxyAuthenticator
import android.content.Context
import android.net.Network
import engine.network.TunnelNetworks
import features.resources.runtime.ResourceFileDownloadAttemptFactory
import features.resources.runtime.ResourceFileDownloadConnection
import features.resources.runtime.ResourceFileDownloadConnectionOpener
import features.resources.runtime.ResourceFileDownloadTransfer
import java.io.File
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
        proxy: SocksProxyEndpoint? = null,
        userAgent: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        runResourceFileDownloadWithProxyFallback(
            proxy = proxy,
            usernameOf = SocksProxyEndpoint::username,
            withAuthenticator = { selectedProxy, block ->
                withSocksProxyAuthenticator(
                    port = selectedProxy.port,
                    username = selectedProxy.username,
                    password = selectedProxy.password,
                    block = block,
                )
            },
            onProxyFallback = {
                AndroidResourceFileLogger.info("Proxy download failed, falling back to direct connection")
            },
            downloadViaProxy = { selectedProxy ->
                transfer(target, selectedProxy, userAgent).download(url, onProgress)
            },
            downloadDirect = {
                transfer(target, null, userAgent).download(url, onProgress)
            },
        )
    }

    private fun transfer(
        target: File,
        proxy: SocksProxyEndpoint?,
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
        isCancelled = ResourceFileDownloadCancellation::isCancelled,
        throwIfCancelled = ResourceFileDownloadCancellation::throwIfCancelled,
    )
}

private class AndroidVpnNetworkSnapshot(var network: Network?)

/** Adapts a single Android HTTP connection to the shared transfer contract. */
private class AndroidResourceFileDownloadConnection(
    private val uri: URI,
    private val proxy: SocksProxyEndpoint?,
    private val userAgent: String?,
    private val vpnNetwork: AndroidVpnNetworkSnapshot,
) : ResourceFileDownloadConnection {
    private var connection = uri.toUrlConnection(proxy, userAgent, vpnNetwork.network)

    override val responseCode: Int
        get() {
            ResourceFileDownloadCancellation.track(connection)
            ResourceFileDownloadCancellation.throwIfCancelled()
            return try {
                connection.responseCode
            } catch (error: SocketException) {
                if (vpnNetwork.network != null) {
                    AndroidResourceFileLogger.info("VPN socket binding failed ($error), falling back to direct network")
                    vpnNetwork.network = null
                    ResourceFileDownloadCancellation.untrack(connection)
                    connection.disconnect()
                    connection = uri.toUrlConnection(proxy, userAgent, null)
                    ResourceFileDownloadCancellation.track(connection)
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
        ResourceFileDownloadCancellation.untrack(connection)
        connection.disconnect()
    }
}

private fun URI.toUrlConnection(
    proxy: SocksProxyEndpoint?,
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

private const val ResourceFileDefaultUserAgent = "SKIPI/1.0"

internal fun overallProgress(
    fileIndex: Int,
    fileCount: Int,
    downloadedBytes: Long,
    totalBytes: Long,
): Int? {
    return resourceFileUpdateOverallProgress(
        fileIndex = fileIndex,
        fileCount = fileCount,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    )
}
