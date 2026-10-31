// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProxyHomeLatencyRunnerTest {
    @Test
    fun noEligibleTargetsPublishInjectedMessageAndCompleteHomeRun() = runBlocking {
        val events = mutableListOf<String>()
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
            subscriptionPingConcurrency = 8,
            tcpTester = DesktopProxyHomeTcpTester { _, _ -> error("No probe should run") },
            jobOwner = DesktopProxyHomeLatencyJobOwner(),
            readTestingServerIds = { emptySet() },
            publishTestingServerIds = { error("No testing state should be published") },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = { error("No results should be published") },
            latencyDoneTemplate = "done:{count}",
            noTestableServersMessage = "no-testable",
            publishMessage = { events += "message:$it" },
        )

        val job = runner.start(emptyList()) { events += "home-finished" }
        withTimeout(5_000) { job.join() }

        assertEquals(listOf("message:no-testable", "home-finished"), events)
    }

    @Test
    fun resultsAndSummaryPublishBeforeTestingStateClearsAndHomeRunCompletes() = runBlocking {
        val events = mutableListOf<String>()
        val allowProbeToFinish = CompletableDeferred<DesktopServerLatencyResult>()
        val probeStarted = CompletableDeferred<Unit>()
        var testingIds = emptySet<Int>()
        var latencyById: Map<Int, DesktopServerLatencyResult> = mapOf(11 to DesktopServerLatencyResult.Success(8))
        val owner = DesktopProxyHomeLatencyJobOwner()
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
            subscriptionPingConcurrency = 8,
            tcpTester = DesktopProxyHomeTcpTester { serverId, _ ->
                events += "probe:$serverId"
                probeStarted.complete(Unit)
                allowProbeToFinish.await()
            },
            jobOwner = owner,
            readTestingServerIds = { testingIds },
            publishTestingServerIds = { ids ->
                testingIds = ids
                events += "testing:${ids.sorted()}"
            },
            readLatencyByServerId = { latencyById },
            publishLatencyByServerId = { values ->
                latencyById = values
                events += "results:${values.keys.sorted()}"
            },
            latencyDoneTemplate = "done:{count}",
            noTestableServersMessage = "no-testable",
            publishMessage = { message -> events += "message:$message" },
        )

        val job = runner.start(listOf(target(7))) { events += "home-finished" }
        probeStarted.await()
        assertTrue(owner.hasActiveJobs)
        assertEquals(setOf(7), testingIds)

        allowProbeToFinish.complete(DesktopServerLatencyResult.Success(12))
        job.join()

        assertEquals(
            listOf(
                "testing:[7]",
                "probe:7",
                "results:[7, 11]",
                "message:done:1",
                "testing:[]",
                "home-finished",
            ),
            events,
        )
        assertEquals(DesktopServerLatencyResult.Success(12), latencyById[7])
        assertEquals(DesktopServerLatencyResult.Success(8), latencyById[11])
        assertFalse(owner.hasActiveJobs)
    }

    @Test
    fun cancellingAProbePublishesMappedFailureBeforeTestingStateClears() = runBlocking {
        val events = mutableListOf<String>()
        val probeStarted = CompletableDeferred<Unit>()
        var testingIds = emptySet<Int>()
        var latencyById = emptyMap<Int, DesktopServerLatencyResult>()
        val owner = DesktopProxyHomeLatencyJobOwner()
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
            subscriptionPingConcurrency = 8,
            tcpTester = DesktopProxyHomeTcpTester { serverId, _ ->
                events += "probe:$serverId"
                probeStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    events += "probe-cleanup"
                }
            },
            jobOwner = owner,
            readTestingServerIds = { testingIds },
            publishTestingServerIds = { ids ->
                testingIds = ids
                events += "testing:${ids.sorted()}"
            },
            readLatencyByServerId = { latencyById },
            publishLatencyByServerId = { latencyById = it; events += "results" },
            latencyDoneTemplate = "done:{count}",
            noTestableServersMessage = "no-testable",
            publishMessage = { events += "message:$it" },
        )

        val job = runner.start(listOf(target(7))) { events += "home-finished" }
        probeStarted.await()
        assertTrue(owner.hasActiveJobs)
        job.cancelAndJoin()

        assertEquals(
            listOf("testing:[7]", "probe:7", "probe-cleanup", "results", "testing:[]", "home-finished"),
            events,
        )
        assertTrue(latencyById[7] is DesktopServerLatencyResult.Error)
        assertFalse(owner.hasActiveJobs)
        assertTrue(testingIds.isEmpty())
    }

    @Test
    fun eachCompletedProbePublishesBeforeTheOtherProbeFinishes() = runBlocking {
        val fastResultPublished = CompletableDeferred<Unit>()
        val slowResultPublished = CompletableDeferred<Unit>()
        val slowProbeResult = CompletableDeferred<DesktopServerLatencyResult>()
        val fastProbeResult = CompletableDeferred<DesktopServerLatencyResult>()
        val fastProbeStarted = CompletableDeferred<Unit>()
        val slowProbeStarted = CompletableDeferred<Unit>()
        var testingIds = emptySet<Int>()
        var latencyById = emptyMap<Int, DesktopServerLatencyResult>()
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
            subscriptionPingConcurrency = 8,
            tcpTester = DesktopProxyHomeTcpTester { serverId, _ ->
                when (serverId) {
                    7 -> { slowProbeStarted.complete(Unit); slowProbeResult.await() }
                    8 -> { fastProbeStarted.complete(Unit); fastProbeResult.await() }
                    else -> error("Unexpected target: $serverId")
                }
            },
            jobOwner = DesktopProxyHomeLatencyJobOwner(),
            readTestingServerIds = { testingIds },
            publishTestingServerIds = { testingIds = it },
            readLatencyByServerId = { latencyById },
            publishLatencyByServerId = { values ->
                latencyById = values
                if (values[8] == DesktopServerLatencyResult.Success(8)) fastResultPublished.complete(Unit)
                if (values[7] == DesktopServerLatencyResult.Success(7)) slowResultPublished.complete(Unit)
            },
            latencyDoneTemplate = "done:{count}",
            noTestableServersMessage = "no-testable",
            publishMessage = {},
        )

        val job = runner.start(listOf(target(7), target(8)))
        withTimeout(5_000) { slowProbeStarted.await() }
        withTimeout(5_000) { fastProbeStarted.await() }
        fastProbeResult.complete(DesktopServerLatencyResult.Success(8))
        withTimeout(5_000) { fastResultPublished.await() }

        assertEquals(DesktopServerLatencyResult.Success(8), latencyById[8])
        assertFalse(7 in latencyById)
        assertEquals(setOf(7, 8), testingIds)

        slowProbeResult.complete(DesktopServerLatencyResult.Success(7))
        withTimeout(5_000) { slowResultPublished.await() }
        withTimeout(5_000) { job.join() }
        assertEquals(DesktopServerLatencyResult.Success(7), latencyById[7])
        assertTrue(testingIds.isEmpty())
    }

    @Test
    fun cancellationKeepsAlreadyPublishedProbeResultsAndClearsOnlyRunMarkers() = runBlocking {
        val completedResultPublished = CompletableDeferred<Unit>()
        val completedProbeStarted = CompletableDeferred<Unit>()
        val unfinishedProbeStarted = CompletableDeferred<Unit>()
        var testingIds = setOf(11)
        var latencyById: Map<Int, DesktopServerLatencyResult> = mapOf(11 to DesktopServerLatencyResult.Success(3))
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
            subscriptionPingConcurrency = 8,
            tcpTester = DesktopProxyHomeTcpTester { serverId, _ ->
                when (serverId) {
                    7 -> {
                        completedProbeStarted.complete(Unit)
                        DesktopServerLatencyResult.Success(7)
                    }
                    8 -> {
                        unfinishedProbeStarted.complete(Unit)
                        awaitCancellation()
                    }
                    else -> error("Unexpected target: $serverId")
                }
            },
            jobOwner = DesktopProxyHomeLatencyJobOwner(),
            readTestingServerIds = { testingIds },
            publishTestingServerIds = { testingIds = it },
            readLatencyByServerId = { latencyById },
            publishLatencyByServerId = { values ->
                latencyById = values
                if (values[7] == DesktopServerLatencyResult.Success(7)) completedResultPublished.complete(Unit)
            },
            latencyDoneTemplate = "done:{count}",
            noTestableServersMessage = "no-testable",
            publishMessage = {},
        )

        val job = runner.start(listOf(target(7), target(8)))
        withTimeout(5_000) { completedProbeStarted.await() }
        withTimeout(5_000) { unfinishedProbeStarted.await() }
        withTimeout(5_000) { completedResultPublished.await() }
        assertEquals(DesktopServerLatencyResult.Success(7), latencyById[7])
        assertEquals(DesktopServerLatencyResult.Success(3), latencyById[11])

        job.cancelAndJoin()

        assertEquals(DesktopServerLatencyResult.Success(7), latencyById[7])
        assertEquals(DesktopServerLatencyResult.Success(3), latencyById[11])
        assertTrue(latencyById[8] is DesktopServerLatencyResult.Error)
        assertEquals(setOf(11), testingIds)
    }

    @Test
    fun persistedConcurrencyIsClampedToTheSupportedRange() = runBlocking {
        val belowRangeMaximum = maximumConcurrentProbes(subscriptionPingConcurrency = 0, targetCount = 3)
        val aboveRangeMaximum = maximumConcurrentProbes(subscriptionPingConcurrency = 65, targetCount = 65)

        assertEquals(1, belowRangeMaximum)
        assertEquals(64, aboveRangeMaximum)
    }

    private suspend fun maximumConcurrentProbes(subscriptionPingConcurrency: Int, targetCount: Int): Int = coroutineScope {
        val allStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val active = java.util.concurrent.atomic.AtomicInteger()
        val maximum = java.util.concurrent.atomic.AtomicInteger()
        val owner = DesktopProxyHomeLatencyJobOwner()
        var testingIds = emptySet<Int>()
        val targets = (1..targetCount).map(::target)
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
            subscriptionPingConcurrency = subscriptionPingConcurrency,
            tcpTester = DesktopProxyHomeTcpTester { _, _ ->
                val count = active.incrementAndGet()
                maximum.updateAndGet { previous -> maxOf(previous, count) }
                if (count == targetCount.coerceAtMost(subscriptionPingConcurrency.coerceIn(1, 64))) {
                    allStarted.complete(Unit)
                }
                try {
                    release.await()
                    DesktopServerLatencyResult.Success(1)
                } finally {
                    active.decrementAndGet()
                }
            },
            jobOwner = owner,
            readTestingServerIds = { testingIds },
            publishTestingServerIds = { testingIds = it },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
            latencyDoneTemplate = "done:{count}",
            noTestableServersMessage = "no-testable",
            publishMessage = {},
        )

        val job = runner.start(targets)
        withTimeout(5_000) { allStarted.await() }
        val observedMaximum = maximum.get()
        release.complete(Unit)
        withTimeout(5_000) { job.join() }
        observedMaximum
    }

    private fun target(serverId: Int): Pair<Int, ProxyServer<*>> = serverId to ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@server-$serverId.example:443?security=tls#Server",
    )
}
