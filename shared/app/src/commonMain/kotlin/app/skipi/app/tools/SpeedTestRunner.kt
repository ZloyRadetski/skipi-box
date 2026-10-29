// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.speedtest.SpeedTestMetrics
import features.tools.speedtest.SpeedTestPhase
import features.tools.speedtest.SpeedTestResult
import features.tools.speedtest.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Android's speed test parameters; tests shrink them to keep runs instantaneous. */
data class SpeedTestConfig(
    val pingSamples: Int = 6,
    val downloadStreams: Int = 4,
    val uploadStreams: Int = 3,
    val downloadMaxDurationMillis: Long = 12_000L,
    val uploadMaxDurationMillis: Long = 10_000L,
    val downloadByteBudget: Long = 48L * 1024 * 1024,
    val uploadByteBudget: Long = 16L * 1024 * 1024,
    val downloadPayloadBytes: Int = 8 * 1024 * 1024,
    val uploadPayloadBytes: Long = 8L * 1024 * 1024,
    val sampleIntervalMillis: Long = 200L,
    val retryDelayMillis: Long = 250L,
)

/**
 * Measures ping/jitter and real download/upload throughput against the Cloudflare speed
 * endpoints, in strict order: ping, download, upload. Cancelling the caller stops the run
 * promptly and resets the observable state to idle.
 */
class SpeedTestRunner(
    private val transport: SpeedTestTransport,
    private val config: SpeedTestConfig = SpeedTestConfig(),
    private val onState: (SpeedTestState) -> Unit,
) {
    suspend fun run(): SpeedTestResult? = try {
        val roundTrips = runPingPhase()
        if (roundTrips.isEmpty()) error("Ping phase produced no samples")
        val pingMs = SpeedTestMetrics.median(roundTrips) ?: 0.0
        val jitterMs = SpeedTestMetrics.jitter(roundTrips)

        onState(SpeedTestState(phase = SpeedTestPhase.Download))
        val downloadMbps = measure(
            streamCount = config.downloadStreams,
            maxDurationMillis = config.downloadMaxDurationMillis,
            byteBudget = config.downloadByteBudget,
            onSample = { progress, mbps -> onState(SpeedTestState(SpeedTestPhase.Download, progress, mbps)) },
            chunk = { counter ->
                transport.download(downloadUrl(config.downloadPayloadBytes)) { counter.update { total -> total + it } }
            },
        )

        onState(SpeedTestState(phase = SpeedTestPhase.Upload))
        val uploadMbps = measure(
            streamCount = config.uploadStreams,
            maxDurationMillis = config.uploadMaxDurationMillis,
            byteBudget = config.uploadByteBudget,
            onSample = { progress, mbps -> onState(SpeedTestState(SpeedTestPhase.Upload, progress, mbps)) },
            chunk = { counter ->
                transport.upload(uploadUrl(), config.uploadPayloadBytes) { counter.update { total -> total + it } }
            },
        )

        val result = SpeedTestResult(pingMs, jitterMs, downloadMbps, uploadMbps)
        onState(SpeedTestState(phase = SpeedTestPhase.Finished, progress = 1f, result = result))
        result
    } catch (error: CancellationException) {
        onState(SpeedTestState())
        throw error
    } catch (error: Throwable) {
        onState(
            SpeedTestState(
                phase = SpeedTestPhase.Failed,
                errorMessage = error.message ?: error::class.simpleName,
            ),
        )
        null
    }

    private suspend fun runPingPhase(): List<Double> {
        onState(SpeedTestState(phase = SpeedTestPhase.Ping))
        // Warm-up request: establishes DNS, TCP and TLS so the measured samples reflect pure
        // round-trip time, not the handshake.
        runCatching { transport.roundTripMillis(downloadUrl(bytes = 0)) }
        val samples = mutableListOf<Double>()
        repeat(config.pingSamples) { index ->
            currentCoroutineContext().ensureActive()
            runCatching { transport.roundTripMillis(downloadUrl(bytes = 0)) }
                .getOrNull()
                ?.takeIf { it > 0.0 }
                ?.let(samples::add)
            onState(
                SpeedTestState(
                    phase = SpeedTestPhase.Ping,
                    progress = (index + 1).toFloat() / config.pingSamples,
                ),
            )
        }
        return samples
    }

    /**
     * Multi-stream throughput: parallel transfer loops run until the duration cap or the byte
     * budget is reached while a sampler reports smoothed live speed. The result is total bytes
     * over the actual elapsed time.
     */
    private suspend fun measure(
        streamCount: Int,
        maxDurationMillis: Long,
        byteBudget: Long,
        onSample: (Float, Double) -> Unit,
        chunk: suspend (MutableStateFlow<Long>) -> Unit,
    ): Double {
        val transferred = MutableStateFlow(0L)
        val started = TimeSource.Monotonic.markNow()
        val maxDuration = maxDurationMillis.milliseconds
        var emaMbps: Double? = null
        var lastSampleBytes = 0L
        var lastSampleAt = started

        coroutineScope {
            val sampler = launch {
                while (true) {
                    delay(config.sampleIntervalMillis)
                    val now = TimeSource.Monotonic.markNow()
                    val total = transferred.value
                    val deltaBytes = (total - lastSampleBytes).coerceAtLeast(0)
                    val deltaMillis = (now - lastSampleAt).inWholeMilliseconds.coerceAtLeast(1)
                    lastSampleBytes = total
                    lastSampleAt = now
                    emaMbps = SpeedTestMetrics.smoothedMbps(emaMbps, deltaBytes, deltaMillis)
                    val progress = maxOf(
                        total.toFloat() / byteBudget,
                        (now - started).inWholeMilliseconds.toFloat() / maxDurationMillis,
                    ).coerceIn(0f, 1f)
                    onSample(progress, emaMbps ?: 0.0)
                }
            }
            // Wait for the transfer loops before stopping the sampler, otherwise the UI would
            // never see live throughput updates.
            val workers = (0 until streamCount).map {
                launch {
                    while (started.elapsedNow() < maxDuration && transferred.value < byteBudget) {
                        currentCoroutineContext().ensureActive()
                        // Sleep only after a failure: pausing between successful chunks would
                        // cut the measured throughput by the idle fraction of every cycle.
                        if (runCatching { chunk(transferred) }.isFailure) delay(config.retryDelayMillis)
                    }
                }
            }
            workers.forEach { it.join() }
            sampler.cancel()
        }

        val elapsedMillis = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(1)
        val totalBytes = transferred.value
        return if (totalBytes <= 0) 0.0 else totalBytes * 8.0 * 1000.0 / elapsedMillis / 1_000_000.0
    }

    private fun downloadUrl(bytes: Int) = "https://speed.cloudflare.com/__down?bytes=$bytes&r=${Random.nextLong()}"

    private fun uploadUrl() = "https://speed.cloudflare.com/__up?r=${Random.nextLong()}"
}
