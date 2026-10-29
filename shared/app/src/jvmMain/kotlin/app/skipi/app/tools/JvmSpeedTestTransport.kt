// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import kotlin.random.Random

/**
 * Opens a connected, tunnel-only request. SOCKS authentication must remain installed for the
 * connect call itself, which is why [connect] also performs it.
 */
fun interface SpeedTestConnectionFactory {
    fun connect(url: String, configure: (HttpURLConnection) -> Unit): HttpURLConnection
}

/** Connection factory that goes strictly through a SOCKS tunnel listener, never directly. */
fun socksSpeedTestConnectionFactory(route: DiagnosticsRoute.Socks) = SpeedTestConnectionFactory { url, configure ->
    route.withSocksAuthenticator {
        (URI(url).toURL().openConnection(route.javaProxy()) as HttpURLConnection).apply {
            configure(this)
            connect()
        }
    }
}

/** HttpURLConnection-based speed test transfers over a host-provided [connectionFactory]. */
class JvmSpeedTestTransport(
    private val connectionFactory: SpeedTestConnectionFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpeedTestTransport {
    /**
     * One HTTP round trip with the stream fully read so the connection returns to the keep-alive
     * cache; consecutive samples then ride a warm connection instead of paying TCP+TLS each time.
     */
    override suspend fun roundTripMillis(url: String): Double = withContext(ioDispatcher) {
        val startedAt = System.nanoTime()
        val connection = connectionFactory.connect(url) { target ->
            configure(target, connectTimeoutMillis = PingConnectTimeoutMillis)
            target.requestMethod = "GET"
        }
        try {
            connection.inputStream.use { input -> while (input.read() != -1) Unit }
        } finally {
            runCatching { connection.inputStream?.close() }
        }
        (System.nanoTime() - startedAt) / NanosPerMilli
    }

    override suspend fun download(url: String, onBytes: (Int) -> Unit) = withContext(ioDispatcher) {
        val connection = connectionFactory.connect(url) { target ->
            configure(target)
            target.requestMethod = "GET"
        }
        try {
            connection.inputStream.use { input ->
                val buffer = ByteArray(BufferSizeBytes)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) onBytes(read)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun upload(url: String, totalBytes: Long, onBytes: (Int) -> Unit) = withContext(ioDispatcher) {
        val connection = connectionFactory.connect(url) { target ->
            configure(target)
            target.requestMethod = "POST"
            target.doOutput = true
            target.setFixedLengthStreamingMode(totalBytes)
        }
        try {
            connection.outputStream.use { output ->
                val buffer = Random.nextBytes(UploadChunkSizeBytes)
                var remaining = totalBytes
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val size = minOf(buffer.size.toLong(), remaining).toInt()
                    output.write(buffer, 0, size)
                    remaining -= size
                    onBytes(size)
                }
            }
            connection.responseCode
            Unit
        } finally {
            connection.disconnect()
        }
    }

    private fun configure(
        connection: HttpURLConnection,
        connectTimeoutMillis: Int = 10_000,
        readTimeoutMillis: Int = 20_000,
    ) {
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.useCaches = false
        // A redirected request could open after the SOCKS authenticator scope has ended. Failing
        // it is preferable to ever measuring a direct path.
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Cache-Control", "no-cache")
    }

    private companion object {
        const val NanosPerMilli = 1_000_000.0
        const val PingConnectTimeoutMillis = 5_000
        const val BufferSizeBytes = 64 * 1024
        const val UploadChunkSizeBytes = 256 * 1024
    }
}
