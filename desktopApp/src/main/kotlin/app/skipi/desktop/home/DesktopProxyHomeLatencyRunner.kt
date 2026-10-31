// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeLatencyRunner
import features.proxy.server.model.ProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
                    onResult = { (serverId, _), result ->
                        synchronized(publicationLock) {
                            publishLatencyByServerId(readLatencyByServerId() + (serverId to result))
                        }
                    },
                ).execute(eligible)
                publishMessage(latencyDoneTemplate.formatTemplate("count" to eligible.size))
            } finally {
                publishTestingServerIds(readTestingServerIds() - testedIds)
                onFinished()
            }
        }
        jobOwner.track(job)
        return job
    }
}
