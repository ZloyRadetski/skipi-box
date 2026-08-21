// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlin.random.Random

/**
 * Measures ping/jitter and real download/upload throughput against the
 * Cloudflare speed endpoints. The test runs as plain HTTP traffic, so it
 * measures the effective network path (direct or through the VPN tunnel).
 * Cancelling the calling coroutine stops the run promptly.
 */
internal class SpeedTestEngine(
    private val onState: (SpeedTestState) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val transfer = SpeedTestTransfer()

    suspend fun run(): SpeedTestResult? = withContext(ioDispatcher) {
        try {
            val roundTripTimes = runPingPhase()
            if (roundTripTimes.isEmpty()) {
                throw IOException("Ping phase produced no samples")
            }
            val pingMs = SpeedTestMath.median(roundTripTimes) ?: 0.0
            val jitterMs = SpeedTestMath.jitter(roundTripTimes)

            emit(SpeedTestState(phase = SpeedTestPhase.Download))
            val downloadMbps = transfer.measureDownload { progress, mbps ->
                emit(SpeedTestState(SpeedTestPhase.Download, progress, mbps))
            }

            emit(SpeedTestState(phase = SpeedTestPhase.Upload))
            val uploadMbps = transfer.measureUpload { progress, mbps ->
                emit(SpeedTestState(SpeedTestPhase.Upload, progress, mbps))
            }

            val result = SpeedTestResult(
                pingMs = pingMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
            )
            emit(
                SpeedTestState(
                    phase = SpeedTestPhase.Finished,
                    progress = 1f,
                    result = result,
                ),
            )
            AndroidAppLogger.info(
                LogTag,
                "Speed test finished: ping=%.1fms jitter=%.1fms down=%.1fMbps up=%.1fMbps"
                    .format(pingMs, jitterMs, downloadMbps, uploadMbps),
            )
            result
        } catch (error: CancellationException) {
            emit(SpeedTestState())
            throw error
        } catch (error: Throwable) {
            AndroidAppLogger.warn(LogTag, "Speed test failed", error)
            emit(
                SpeedTestState(
                    phase = SpeedTestPhase.Failed,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                ),
            )
            null
        }
    }

    private suspend fun runPingPhase(): List<Double> {
        emit(SpeedTestState(phase = SpeedTestPhase.Ping))
        val samples = mutableListOf<Double>()
        repeat(PingRequestCount) { index ->
            currentCoroutineContext().ensureActive()
            runCatching { measureRoundTrip() }
                .getOrNull()
                ?.takeIf { it > 0.0 }
                ?.let(samples::add)
            emit(
                SpeedTestState(
                    phase = SpeedTestPhase.Ping,
                    progress = (index + 1).toFloat() / PingRequestCount,
                ),
            )
        }
        return samples
    }

    private fun measureRoundTrip(): Double {
        val startedAt = System.nanoTime()
        val connection = openConnection(downloadUrl(bytes = 0))
        try {
            configure(connection, connectTimeoutMillis = PingConnectTimeoutMillis)
            connection.requestMethod = "GET"
            connection.connect()
            connection.inputStream.use { input -> input.read() }
        } finally {
            connection.disconnect()
        }
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI
    }

    private fun emit(state: SpeedTestState) {
        onState(state)
    }

    companion object {
        private const val LogTag = "SpeedTest"
        private const val NANOS_PER_MILLI = 1_000_000.0
        private const val PingRequestCount = 6
        private const val PingConnectTimeoutMillis = 5_000
    }
}

/** Opens a plain [HttpURLConnection] against one absolute [url]. */
internal fun openConnection(url: String): HttpURLConnection {
    return URL(url).openConnection() as HttpURLConnection
}

/** Applies shared timeout/cache settings to a speed test connection. */
internal fun configure(
    connection: HttpURLConnection,
    connectTimeoutMillis: Int = 10_000,
    readTimeoutMillis: Int = 20_000,
) {
    connection.connectTimeout = connectTimeoutMillis
    connection.readTimeout = readTimeoutMillis
    connection.useCaches = false
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("Cache-Control", "no-cache")
}

/** Unique download endpoint URL requesting exactly [bytes] bytes. */
internal fun downloadUrl(bytes: Int): String {
    return "https://speed.cloudflare.com/__down?bytes=$bytes&r=${Random.nextLong()}"
}

/** Upload sink endpoint URL. */
internal fun uploadUrl(): String {
    return "https://speed.cloudflare.com/__up?r=${Random.nextLong()}"
}
