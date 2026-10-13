// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProxyHomeLatencyRunnerTest {
    @Test
    fun resultsAndSummaryPublishBeforeTestingStateClearsAndHomeRunCompletes() = runBlocking {
        val events = mutableListOf<String>()
        val allowProbeToFinish = CompletableDeferred<DesktopServerLatencyResult>()
        val probeStarted = CompletableDeferred<Unit>()
        var testingIds = emptySet<Int>()
        var latencyById = mapOf(11 to DesktopServerLatencyResult.Success(8))
        val owner = DesktopProxyHomeLatencyJobOwner()
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
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
                "message:TCP-проверка: 1 доступны, 0 тайм-аут, 0 ошибок.",
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
    fun cancellingAProbeClearsTestingStateWithoutPublishingPartialResults() = runBlocking {
        val events = mutableListOf<String>()
        val probeStarted = CompletableDeferred<Unit>()
        var testingIds = emptySet<Int>()
        var latencyById = emptyMap<Int, DesktopServerLatencyResult>()
        val owner = DesktopProxyHomeLatencyJobOwner()
        val runner = DesktopProxyHomeLatencyRunner(
            scope = this,
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
            publishMessage = { events += "message:$it" },
        )

        val job = runner.start(listOf(target(7))) { events += "home-finished" }
        probeStarted.await()
        assertTrue(owner.hasActiveJobs)
        job.cancelAndJoin()

        assertEquals(
            listOf("testing:[7]", "probe:7", "probe-cleanup", "testing:[]", "home-finished"),
            events,
        )
        assertTrue(latencyById.isEmpty())
        assertFalse(owner.hasActiveJobs)
        assertTrue(testingIds.isEmpty())
    }

    private fun target(serverId: Int): Pair<Int, ProxyServer<*>> = serverId to ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@server-$serverId.example:443?security=tls#Server",
    )
}
