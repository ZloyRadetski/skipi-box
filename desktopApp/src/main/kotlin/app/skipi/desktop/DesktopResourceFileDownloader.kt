// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.resources.runtime.ResourceFileDownloadAttemptFactory
import features.resources.runtime.ResourceFileDownloadCancellation
import features.resources.runtime.ResourceFileDownloadConnection
import features.resources.runtime.ResourceFileDownloadConnectionOpener
import features.resources.runtime.ResourceFileDownloadTransfer
import features.resources.runtime.runResourceFileDownloadWithProxyFallback
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI

/** Desktop HTTP adapter for the shared resource-file transfer policy. */
internal class DesktopResourceFileDownloader(
    private val publishAtomically: (File, (OutputStream) -> Unit) -> Unit,
) {
    fun download(
        url: String,
        target: File,
        proxy: DesktopSubscriptionSocksProxy? = null,
        userAgent: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        runResourceFileDownloadWithProxyFallback(
            proxy = proxy,
            usernameOf = DesktopSubscriptionSocksProxy::username,
            withAuthenticator = { selectedProxy, block -> selectedProxy.withAuthenticator(block) },
            onProxyFallback = {
                DesktopLogger.info("ResourceFiles", "Proxy download failed, falling back to direct connection")
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
        proxy: DesktopSubscriptionSocksProxy?,
        userAgent: String?,
    ): ResourceFileDownloadTransfer = ResourceFileDownloadTransfer(
        attempts = ResourceFileDownloadAttemptFactory {
            ResourceFileDownloadConnectionOpener { uri ->
                DesktopResourceFileDownloadConnection(uri, proxy, userAgent)
            }
        },
        publishAtomically = { write -> publishAtomically(target, write) },
        isCancelled = ResourceFileDownloadCancellation::isCancelled,
        throwIfCancelled = ResourceFileDownloadCancellation::throwIfCancelled,
    )
}

/** Adapts one URLConnection while the shared transfer follows redirects and retries. */
private class DesktopResourceFileDownloadConnection(
    uri: URI,
    proxy: DesktopSubscriptionSocksProxy?,
    userAgent: String?,
) : ResourceFileDownloadConnection {
    private val connection = uri.toResourceFileUrlConnection(proxy, userAgent)

    override val responseCode: Int
        get() {
            ResourceFileDownloadCancellation.track(connection)
            ResourceFileDownloadCancellation.throwIfCancelled()
            return connection.responseCode
        }

    override val location: String?
        get() = connection.getHeaderField("Location")

    override val contentLength: Long
        get() = connection.contentLengthLong

    override val inputStream: InputStream
        get() = connection.inputStream

    override fun disconnect() {
        ResourceFileDownloadCancellation.untrack(connection)
        connection.disconnect()
    }
}

private fun URI.toResourceFileUrlConnection(
    proxy: DesktopSubscriptionSocksProxy?,
    userAgent: String?,
): HttpURLConnection {
    val url = toURL()
    val connection = if (proxy != null) {
        url.openConnection(proxy.toJavaProxy())
    } else {
        url.openConnection()
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
