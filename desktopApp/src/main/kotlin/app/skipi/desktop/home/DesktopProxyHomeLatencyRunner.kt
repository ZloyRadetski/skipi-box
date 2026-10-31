// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.LatencyResultBatcher
import app.skipi.app.home.ProxyHomeLatencyRunner
import app.skipi.app.home.ProxyHomeLatencyUpdateWindowMillis
import features.proxy.server.model.ProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.skipi.ui.text.formatTemplate

internal fun interface DesktopProxyHomeTcpTester {
    suspend fun test(serverId: Int, server: ProxyServer<*>): DesktopServerLatencyResult
}

/** Keeps Desktop I/O and Home state publication at the host boundary. */
internal class DesktopProxyHomeLatencyRunner(
    private val scope: CoroutineScope,
    private val subscriptionPingConcurrency: Int,
    private val tcpTester: DesktopProxyHomeTcpTester,
    private val jobOwner: DesktopProxyHomeLatencyJobOwner,
    private val readTestingServerIds: () -> Set<Int>,
    private val publishTestingServerIds: (Set<Int>) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
    private val latencyDoneTemplate: String,
    private val noTestableServersMessage: String,
    private val publishMessage: (String) -> Unit,
) {
    fun start(
        targets: List<Pair<Int, ProxyServer<*>>>,
        onFinished: () -> Unit = {},
    ): Job {
        val testingServerIds = readTestingServerIds()
        val eligible = targets
            .distinctBy { (serverId, _) -> serverId }
            .filter { (serverId, server) ->
                serverId !in testingServerIds && server.desktopTcpEndpointOrNull() != null
            }
        if (eligible.isEmpty()) {
            publishMessage(noTestableServersMessage)
            return scope.launch(start = CoroutineStart.UNDISPATCHED) { onFinished() }
        }

        val testedIds = eligible.map { (serverId, _) -> serverId }.toSet()
        publishTestingServerIds(readTestingServerIds() + testedIds)
        val publicationLock = Any()
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Like Android: answers are applied in short windows so a big subscription is not re-projected
            // once per answer, and a card leaves "testing" with its own result, not when the whole run ends.
            val batcher = LatencyResultBatcher<Int, DesktopServerLatencyResult>(
                scope = this,
                intervalMillis = ProxyHomeLatencyUpdateWindowMillis,
            ) { batch ->
                synchronized(publicationLock) {
                    publishLatencyByServerId(readLatencyByServerId() + batch)
                    publishTestingServerIds(readTestingServerIds() - batch.keys)
                }
            }
            try {
                ProxyHomeLatencyRunner<Pair<Int, ProxyServer<*>>, DesktopServerLatencyResult>(
                    concurrency = subscriptionPingConcurrency,
                    probe = { (serverId, server) -> tcpTester.test(serverId, server) },
                    onProbeFailure = { error ->
                        DesktopServerLatencyResult.Error(
                            message = error.message.orEmpty().ifBlank { error.javaClass.simpleName },
                            cause = error,
                        )
                    },
                    // A cancelled probe still reports its failure.
                    onResult = { (serverId, _), result -> withContext(NonCancellable) { batcher.add(serverId, result) } },
                ).execute(eligible)
                batcher.flush()
                publishMessage(latencyDoneTemplate.formatTemplate("count" to eligible.size))
            } finally {
                withContext(NonCancellable) { batcher.flush() }
                // Only a cancelled run still has markers to clear.
                synchronized(publicationLock) {
                    val current = readTestingServerIds()
                    if (current.any { it in testedIds }) publishTestingServerIds(current - testedIds)
                }
                onFinished()
            }
        }
        jobOwner.track(job)
        return job
    }
}
