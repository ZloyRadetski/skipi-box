// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyHomeLatencyRunnerTest {
    @Test
    fun eachProbePublishesAsItCompletesWhileAnotherProbeRemainsPending() = runTest {
        val slowProbeResult = CompletableDeferred<String>()
        val fastProbeResult = CompletableDeferred<String>()
        val slowProbeStarted = CompletableDeferred<Unit>()
        val fastProbeStarted = CompletableDeferred<Unit>()
        val fastResultPublished = CompletableDeferred<Unit>()
        val slowResultPublished = CompletableDeferred<Unit>()
        val published = mutableListOf<Pair<String, String>>()
        val runner = ProxyHomeLatencyRunner<String, String>(
            concurrency = 2,
            probe = { target ->
                when (target) {
                    "slow" -> { slowProbeStarted.complete(Unit); slowProbeResult.await() }
                    "fast" -> { fastProbeStarted.complete(Unit); fastProbeResult.await() }
                    else -> error("Unexpected target: $target")
                }
            },
            onProbeFailure = { error -> error.message.orEmpty() },
            onResult = { target, result ->
                published += target to result
                if (target == "fast") fastResultPublished.complete(Unit)
                if (target == "slow") slowResultPublished.complete(Unit)
            },
        )

        val job = launch { runner.execute(listOf("slow", "fast")) }
        runCurrent()
        withTimeout(5_000) { slowProbeStarted.await() }
        withTimeout(5_000) { fastProbeStarted.await() }

        fastProbeResult.complete("fast-result")
        runCurrent()
        withTimeout(5_000) { fastResultPublished.await() }
        assertEquals(listOf("fast" to "fast-result"), published)

        slowProbeResult.complete("slow-result")
        runCurrent()
        withTimeout(5_000) { slowResultPublished.await() }
        withTimeout(5_000) { job.join() }

        assertEquals(setOf("fast" to "fast-result", "slow" to "slow-result"), published.toSet())
    }

    @Test
    fun cancellationMapsPendingProbeFailureAndRetainsCompletedPublication() = runTest {
        val pendingProbeStarted = CompletableDeferred<Unit>()
        val published = mutableListOf<Pair<String, String>>()
        val runner = ProxyHomeLatencyRunner<String, String>(
            concurrency = 2,
            probe = { target ->
                if (target == "pending") {
                    pendingProbeStarted.complete(Unit)
                    awaitCancellation()
                } else {
                    "completed-result"
                }
            },
            onProbeFailure = { "failed" },
            onResult = { target, result -> published += target to result },
        )

        val job = launch { runner.execute(listOf("completed", "pending")) }
        runCurrent()
        withTimeout(5_000) { pendingProbeStarted.await() }
        assertEquals(listOf("completed" to "completed-result"), published)

        job.cancelAndJoin()

        assertEquals(
            setOf("completed" to "completed-result", "pending" to "failed"),
            published.toSet(),
        )
    }

    @Test
    fun androidSourceCancellationMapsProbeToFailureBeforeCancelledRunSkipsCompletion() = runTest {
        val probeStarted = CompletableDeferred<Unit>()
        val published = mutableListOf<Pair<String, String>>()
        var aggregateCompletionReached = false
        val runner = ProxyHomeLatencyRunner<String, String>(
            concurrency = 1,
            probe = { target ->
                // Android wraps the tester call in runCatching before its synchronous state update.
                runCatching {
                    probeStarted.complete(Unit)
                    awaitCancellation()
                }.getOrElse { "failed" }
            },
            onProbeFailure = { "runner-failed" },
            onResult = { target, result -> published += target to result },
        )

        val job = launch {
            runner.execute(listOf("cancelled"))
            aggregateCompletionReached = true
        }
        runCurrent()
        withTimeout(5_000) { probeStarted.await() }

        job.cancelAndJoin()

        assertEquals(listOf("cancelled" to "failed"), published)
        assertTrue(job.isCancelled)
        assertFalse(aggregateCompletionReached)
    }

    @Test
    fun oneProbeFailureIsMappedWithoutDiscardingSuccessfulSiblingResults() = runTest {
        val published = mutableMapOf<String, String>()
        val runner = ProxyHomeLatencyRunner<String, String>(
            concurrency = 1,
            probe = { target ->
                if (target == "failed") error("unreachable")
                "reachable"
            },
            onProbeFailure = { error -> "failed:${error.message.orEmpty()}" },
            onResult = { target, result -> published[target] = result },
        )

        runner.execute(listOf("failed", "successful"))

        assertEquals(mapOf("failed" to "failed:unreachable", "successful" to "reachable"), published)
    }

    @Test
    fun hostOwnedGateRemainsInsideProbeWithoutASecondOuterAcquire() = runTest {
        val hostGate = Semaphore(1)
        var limiterCalls = 0
        val published = mutableMapOf<Int, String>()
        val runner = ProxyHomeLatencyRunner<Int, String>(
            concurrency = 2,
            probe = { target ->
                hostGate.withPermit { "result-$target" }
            },
            onProbeFailure = { error -> error.message.orEmpty() },
            onResult = { target, result -> published[target] = result },
            probeLimiter = { runProbe ->
                limiterCalls += 1
                runProbe()
            },
        )

        withTimeout(5_000) { runner.execute(listOf(1, 2)) }

        assertEquals(2, limiterCalls)
        assertEquals(mapOf(1 to "result-1", 2 to "result-2"), published)
    }

    @Test
    fun concurrencyUsesThePersistedValueClampedToOneThroughSixtyFour() = runTest {
        assertEquals(1, maximumActiveProbes(requestedConcurrency = 0, targetCount = 3, scheduler = testScheduler))
        assertEquals(64, maximumActiveProbes(requestedConcurrency = 65, targetCount = 65, scheduler = testScheduler))
    }

    private suspend fun maximumActiveProbes(
        requestedConcurrency: Int,
        targetCount: Int,
        scheduler: TestCoroutineScheduler,
    ): Int = coroutineScope {
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val runner = ProxyHomeLatencyRunner<Int, String>(
            concurrency = requestedConcurrency,
            probe = { target ->
                active += 1
                maximum = maxOf(maximum, active)
                try {
                    release.await()
                    "result-$target"
                } finally {
                    active -= 1
                }
            },
            onProbeFailure = { error -> error.message.orEmpty() },
            onResult = { _, _ -> },
        )

        val job = launch { runner.execute((1..targetCount).toList()) }
        scheduler.runCurrent()
        val observedMaximum = maximum
        release.complete(Unit)
        scheduler.runCurrent()
        withTimeout(5_000) { job.join() }
        observedMaximum
    }
}
