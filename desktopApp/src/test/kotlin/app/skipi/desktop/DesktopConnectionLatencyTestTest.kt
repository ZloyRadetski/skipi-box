// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopConnectionLatencyTestTest {
    private var testing = emptySet<Int>()
    private var latency: Map<Int, DesktopServerLatencyResult> = emptyMap()

    private fun kotlinx.coroutines.CoroutineScope.runner(measure: suspend () -> Long?) = DesktopConnectionLatencyTest(
        scope = this,
        measure = measure,
        readTestingServerIds = { testing },
        publishTestingServerIds = { testing = it },
        readLatencyByServerId = { latency },
        publishLatencyByServerId = { latency = it },
    )

    @Test
    fun theServerShowsAsTestingWhileTheConnectionIsMeasuredThenKeepsTheDelay() = runBlocking {
        val answer = CompletableDeferred<Long?>()
        val started = CompletableDeferred<Unit>()
        val runner = runner { started.complete(Unit); answer.await() }

        val job = runner.start(7)
        withTimeout(5_000) { started.await() }
        assertEquals(setOf(7), testing)

        answer.complete(84)
        withTimeout(5_000) { job.join() }

        assertTrue(testing.isEmpty())
        assertEquals(DesktopServerLatencyResult.Success(84), latency[7])
    }

    @Test
    fun anUnansweredProbeIsRecordedAsATimeout() = runBlocking {
        val runner = runner { null }

        withTimeout(5_000) { runner.start(7).join() }

        assertTrue(testing.isEmpty())
        assertEquals(DesktopServerLatencyResult.Timeout, latency[7])
    }

    @Test
    fun cancellingStopsTheProbeAndKeepsThePreviousDelay() = runBlocking {
        latency = mapOf(7 to DesktopServerLatencyResult.Success(40))
        val started = CompletableDeferred<Unit>()
        val runner = runner { started.complete(Unit); kotlinx.coroutines.awaitCancellation() }

        val job = runner.start(7)
        withTimeout(5_000) { started.await() }
        runner.cancel()
        withTimeout(5_000) { job.join() }

        assertTrue(testing.isEmpty())
        assertEquals(DesktopServerLatencyResult.Success(40), latency[7])
    }

    @Test
    fun otherServersTestingStateIsLeftAlone() = runBlocking {
        testing = setOf(3)
        val runner = runner { 12 }

        withTimeout(5_000) { runner.start(7).join() }

        assertEquals(setOf(3), testing)
        assertEquals(DesktopServerLatencyResult.Success(12), latency[7])
    }

    @Test
    fun startingAgainReplacesTheRunningProbe() = runBlocking {
        val runner = runner { 5 }

        val firstJob = runner.start(7)
        val secondJob = runner.start(7)
        withTimeout(5_000) { secondJob.join() }
        firstJob.cancelAndJoin()

        assertTrue(firstJob.isCancelled)
        assertEquals(DesktopServerLatencyResult.Success(5), latency[7])
        assertTrue(testing.isEmpty())
    }
}
