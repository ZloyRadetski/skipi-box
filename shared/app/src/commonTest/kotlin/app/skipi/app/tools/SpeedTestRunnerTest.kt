// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.speedtest.SpeedTestPhase
import features.tools.speedtest.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedTestRunnerTest {
    private val config = SpeedTestConfig(
        pingSamples = 3,
        downloadStreams = 2,
        uploadStreams = 2,
        downloadByteBudget = 4_000L,
        uploadByteBudget = 2_000L,
        downloadPayloadBytes = 1_000,
        uploadPayloadBytes = 1_000L,
        sampleIntervalMillis = 1L,
        retryDelayMillis = 1L,
    )

    private open class FakeTransport(
        val pings: MutableList<Double> = mutableListOf(10.0, 20.0, 12.0, 14.0, 13.0),
        val failPing: Boolean = false,
    ) : SpeedTestTransport {
        val urls = mutableListOf<String>()
        override suspend fun roundTripMillis(url: String): Double {
            urls += url
            if (failPing) error("offline")
            return pings.removeFirst()
        }

        override suspend fun download(url: String, onBytes: (Int) -> Unit) {
            urls += url
            onBytes(1_000)
        }

        override suspend fun upload(url: String, totalBytes: Long, onBytes: (Int) -> Unit) {
            urls += url
            onBytes(totalBytes.toInt())
        }
    }

    @Test
    fun phasesRunInOrderAndFinishWithMedianPingAndJitter() = runTest {
        val states = mutableListOf<SpeedTestState>()
        val result = SpeedTestRunner(FakeTransport(), config, states::add).run()

        val phases = states.map { it.phase }.distinct()
        assertEquals(
            listOf(SpeedTestPhase.Ping, SpeedTestPhase.Download, SpeedTestPhase.Upload, SpeedTestPhase.Finished),
            phases,
        )
        assertNotNull(result)
        // The warm-up sample (10.0) is discarded; samples 20, 12, 14 -> median 14, jitter (8 + 2) / 2.
        assertEquals(14.0, result.pingMs)
        assertEquals(5.0, result.jitterMs)
        assertTrue(result.downloadMbps > 0.0)
        assertTrue(result.uploadMbps > 0.0)
        assertEquals(SpeedTestPhase.Finished, states.last().phase)
        assertEquals(result, states.last().result)
    }

    @Test
    fun requestsTargetCloudflareSpeedEndpoints() = runTest {
        val transport = FakeTransport()
        SpeedTestRunner(transport, config) {}.run()

        assertTrue(transport.urls.first().startsWith("https://speed.cloudflare.com/__down?bytes=0&r="))
        assertTrue(transport.urls.any { it.startsWith("https://speed.cloudflare.com/__down?bytes=1000&r=") })
        assertTrue(transport.urls.any { it.startsWith("https://speed.cloudflare.com/__up?r=") })
    }

    @Test
    fun emptyPingPhaseFailsWithoutResult() = runTest {
        val states = mutableListOf<SpeedTestState>()
        val result = SpeedTestRunner(FakeTransport(failPing = true), config, states::add).run()

        assertNull(result)
        assertEquals(SpeedTestPhase.Failed, states.last().phase)
        assertEquals("Ping phase produced no samples", states.last().errorMessage)
    }

    @Test
    fun cancellationResetsStateToIdleAndRethrows() = runTest {
        val states = mutableListOf<SpeedTestState>()
        val started = CompletableDeferred<Unit>()
        val hanging = object : FakeTransport() {
            override suspend fun roundTripMillis(url: String): Double {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        var cancelled = false
        val job = launch {
            try {
                SpeedTestRunner(hanging, config, states::add).run()
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            }
        }
        started.await()
        job.cancel()
        job.join()

        assertTrue(cancelled)
        assertEquals(SpeedTestState(), states.last())
    }
}
