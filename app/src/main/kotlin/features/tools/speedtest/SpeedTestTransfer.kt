// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Multi-stream throughput measurement over the Cloudflare speed endpoints.
 * Parallel transfer loops run until a duration cap or a byte budget is reached
 * while a sampler coroutine reports smoothed live speed. The returned value is
 * total bytes over actual elapsed wall time.
 */
internal class SpeedTestTransfer {
    suspend fun measureDownload(onSample: (Float, Double) -> Unit): Double {
        return measure(
            streamCount = DownloadStreams,
            maxDurationMillis = DownloadMaxDurationMillis,
            byteBudgetBytes = DownloadByteBudgetBytes,
            onSample = onSample,
            chunk = ::downloadChunk,
        )
    }

    suspend fun measureUpload(onSample: (Float, Double) -> Unit): Double {
        return measure(
            streamCount = UploadStreams,
            maxDurationMillis = UploadMaxDurationMillis,
            byteBudgetBytes = UploadByteBudgetBytes,
            onSample = onSample,
            chunk = ::uploadChunk,
        )
    }

    private suspend fun measure(
        streamCount: Int,
        maxDurationMillis: Long,
        byteBudgetBytes: Long,
        onSample: (Float, Double) -> Unit,
        chunk: suspend (AtomicLong) -> Unit,
    ): Double = withContext(Dispatchers.IO) {
        val transferredBytes = AtomicLong(0)
        var emaMbps: Double? = null
        var lastSampleBytes = 0L
        val startedAtMillis = System.currentTimeMillis()
        var lastSampleTimeMillis = startedAtMillis
        val deadlineMillis = startedAtMillis + maxDurationMillis

        kotlinx.coroutines.coroutineScope {
            val workersDone = AtomicBoolean(false)
            val sampler = launch {
                while (!workersDone.get()) {
                    delay(SampleIntervalMillis)
                    val now = System.currentTimeMillis()
                    val deltaBytes = (transferredBytes.get() - lastSampleBytes).coerceAtLeast(0)
                    val deltaMillis = (now - lastSampleTimeMillis).coerceAtLeast(1)
                    lastSampleBytes = transferredBytes.get()
                    lastSampleTimeMillis = now
                    emaMbps = SpeedTestMath.smoothedMbps(emaMbps, deltaBytes, deltaMillis)
                    val progress = maxOf(
                        transferredBytes.get().toFloat() / byteBudgetBytes,
                        (now - startedAtMillis).toFloat() / maxDurationMillis,
                    ).coerceIn(0f, 1f)
                    onSample(progress, emaMbps ?: 0.0)
                }
            }
            // Launch the transfer loops and WAIT for them: setting the flag
            // before joining would kill the sampler immediately and the UI
            // would never see live throughput updates.
            val workerJobs = (0 until streamCount).map {
                launch {
                    while (System.currentTimeMillis() < deadlineMillis &&
                        transferredBytes.get() < byteBudgetBytes
                    ) {
                        currentCoroutineContext().ensureActive()
                        // Sleep only after a failure: an unconditional pause
                        // between successful chunks would cut the measured
                        // throughput by the idle fraction of every cycle.
                        val result = runCatching { chunk(transferredBytes) }
                        if (result.isFailure) {
                            delay(RetryDelayMillis)
                        }
                    }
                }
            }
            workerJobs.forEach { it.join() }
            workersDone.set(true)
            sampler.cancel()
        }

        val elapsedMillis = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(1)
        val totalBytes = transferredBytes.get()
        if (totalBytes <= 0) 0.0 else totalBytes.mbpsOver(elapsedMillis)
    }


    /** Downloads one [PayloadBytes] payload and counts every read byte. */
    private suspend fun downloadChunk(transferredBytes: AtomicLong) {
        val connection = openConnection(downloadUrl(PayloadBytes))
        try {
            configure(connection)
            connection.requestMethod = "GET"
            connection.connect()
            connection.inputStream.use { input ->
                val buffer = ByteArray(BufferSizeBytes)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) transferredBytes.addAndGet(read.toLong())
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Uploads one random [UploadPayloadBytes] payload to the sink endpoint. */
    private suspend fun uploadChunk(transferredBytes: AtomicLong) {
        val connection = openConnection(uploadUrl())
        try {
            configure(connection)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(UploadPayloadBytes)
            connection.connect()
            connection.outputStream.use { output ->
                val buffer = Random.nextBytes(UploadChunkSizeBytes)
                var remaining = UploadPayloadBytes
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val size = minOf(buffer.size.toLong(), remaining).toInt()
                    output.write(buffer, 0, size)
                    remaining -= size
                    transferredBytes.addAndGet(size.toLong())
                }
            }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val DownloadStreams = 4
        private const val UploadStreams = 3
        private const val DownloadMaxDurationMillis = 12_000L
        private const val UploadMaxDurationMillis = 10_000L
        private const val DownloadByteBudgetBytes = 48L * 1024 * 1024
        private const val UploadByteBudgetBytes = 16L * 1024 * 1024
        private const val PayloadBytes = 8 * 1024 * 1024
        private const val UploadPayloadBytes = 8L * 1024 * 1024
        private const val BufferSizeBytes = 64 * 1024
        private const val UploadChunkSizeBytes = 256 * 1024
        private const val SampleIntervalMillis = 200L
        private const val RetryDelayMillis = 250L

        private fun Long.mbpsOver(millis: Long): Double {
            return this * 8.0 * 1000.0 / millis / 1_000_000.0
        }
    }
}
