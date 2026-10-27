// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerLatencyTesting
import app.ProxyServerState
import app.skipi.app.home.ProxyHomeLatencyRunner
import app.skipi.app.home.ProxyServerSelectionRuntimeAction
import app.skipi.app.home.decideProxyServerSelectionRuntimeAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import engine.proxy.latency.AndroidProxyLatencyTester
import engine.proxy.latency.ProxyServerLatencyTestMode
import engine.proxy.latency.ProxyServerLatencyTestResult
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyEngineStartRequest
import engine.stats.ProxyTrafficStatsRuntimeStore
import data.AndroidAppStateStore
import ui.feedback.AndroidToastTipNotifier
import ui.text.formatTemplate
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val TcpLatencyTestConcurrency = 16
private const val RealConnectionLatencyTestConcurrency = 8

internal fun restartProxyServiceAfterSelection(
    previousServerId: Int,
    serverId: Int,
    scope: CoroutineScope,
    serviceRestartMutex: Mutex,
    stateStore: AndroidAppStateStore,
    proxyEngine: AndroidProxyEngine,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    scope.launch {
        serviceRestartMutex.withLock {
            val stateSnapshot = stateStore.state.value
            if (stateSnapshot.selectedProxyServerId != serverId) {
                return@withLock
            }
            val status = try {
                proxyEngine.status(stateSnapshot.runMode, stateSnapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                return@withLock
            }
            val currentActiveServerId = ProxyTrafficStatsRuntimeStore.read(stateStore.context)?.selectedServerId
            val server = stateSnapshot.proxyServers.firstOrNull { it.id == serverId }
            val decision = decideProxyServerSelectionRuntimeAction(
                previousSelectedServerId = previousServerId,
                selectedServerId = stateSnapshot.selectedProxyServerId,
                requestedServerId = serverId,
                serverExists = server != null,
                runtimeRunning = status.running,
                activeServerId = currentActiveServerId,
            )
            if (decision != ProxyServerSelectionRuntimeAction.Restart) return@withLock

            val selectedServer = server ?: return@withLock
            try {
                val restartedStatus = proxyEngine.restart(
                    ProxyEngineStartRequest(
                        appState = stateSnapshot,
                        selectedServer = selectedServer,
                    ),
                )
                updateAppState { state ->
                    if (state.selectedProxyServerId == serverId) {
                        state.copy(
                            proxyRunning = restartedStatus.running,
                            localProxyPort = restartedStatus.appState?.localProxyPort ?: state.localProxyPort,
                        )
                    } else {
                        state
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                updateAppState { state ->
                    if (state.selectedProxyServerId == serverId) {
                        state.copy(proxyRunning = false)
                    } else {
                        state
                    }
                }
            }
        }
    }
}

internal fun runProxyServerLatencyTest(
    targetServers: List<ProxyServerState>,
    mode: ProxyServerLatencyTestMode,
    doneTemplate: String,
    showSingleResult: Boolean,
    scope: CoroutineScope,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyLatencyTester: AndroidProxyLatencyTester,
    tipNotifier: AndroidToastTipNotifier,
    noTestableServersMessage: String,
    latencyResultTemplate: String,
    latencyFailedMessage: String,
    onFinished: (() -> Unit)? = null,
): Job {
    if (targetServers.isEmpty()) {
        return scope.launch {
            tipNotifier.show(noTestableServersMessage)
            withContext(Dispatchers.Main.immediate) {
                onFinished?.invoke()
            }
        }
    }

    val previousLatencies = targetServers.associate { it.id to it.latency }

    val job = scope.launch {
        try {
            val stateSnapshot = stateStore.state.value
            val targetIds = targetServers.map { server -> server.id }.toSet()
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = state.proxyServers.map { server ->
                        if (server.id in targetIds) server.copy(latency = ProxyServerLatencyTesting) else server
                    },
                )
            }

            val concurrency = stateSnapshot.subscriptionPingConcurrency.coerceIn(1, 64)
            val semaphore = Semaphore(concurrency)
            val sessionCache = ConcurrentHashMap<Int, Long>()
            val dnsCache = ConcurrentHashMap<String, java.net.InetAddress>()
            val failedDnsCache = ConcurrentHashMap<String, Boolean>()

            ProxyHomeLatencyRunner<ProxyServerState, ProxyServerLatencyTestResult>(
                concurrency = concurrency,
                probe = { server ->
                    proxyLatencyTester.test(
                        appState = stateSnapshot,
                        server = server,
                        mode = mode,
                        sessionCache = sessionCache,
                        semaphore = semaphore,
                        dnsCache = dnsCache,
                        failedDnsCache = failedDnsCache,
                    )
                },
                onProbeFailure = { ProxyServerLatencyTestResult.Failed },
                onResult = { server, result ->
                    val latency = result.toLatencyText(latencyFailedMessage)
                    stateStore.update(persist = false) { state ->
                        state.copy(
                            proxyServers = state.proxyServers.map {
                                if (it.id == server.id) it.copy(latency = latency) else it
                            },
                        )
                    }
                    if (showSingleResult) {
                        val serverName = server.server.getInfo().remarks.ifBlank { server.server.getInfo().protocol }
                        tipNotifier.show(
                            latencyResultTemplate.formatTemplate(
                                "name" to serverName,
                                "latency" to latency,
                            ),
                        )
                    }
                },
                probeLimiter = { runProbe -> runProbe() },
            ).execute(targetServers)

            if (!showSingleResult) {
                tipNotifier.show(doneTemplate.formatTemplate("count" to targetServers.size))
            }
        } finally {
            withContext(NonCancellable) {
                // Latency results and the in-progress marker are host UI runtime data.
                // Keep them in this process without creating another persisted proxy-catalog writer.
                stateStore.update(persist = false) { state ->
                    state.copy(
                        proxyServers = state.proxyServers.map { server ->
                            if (server.latency == ProxyServerLatencyTesting) {
                                val prev = previousLatencies[server.id]
                                server.copy(latency = if (prev != null && prev != ProxyServerLatencyTesting) prev else "")
                            } else {
                                server
                            }
                        },
                    )
                }
                withContext(Dispatchers.Main.immediate) {
                    onFinished?.invoke()
                }
            }
        }
    }
    ProxyServerLatencyTracker.register(job)
    return job
}

private fun ProxyServerLatencyTestResult.toLatencyText(failedMessage: String): String {
    return if (elapsedMillis >= 0) "$elapsedMillis ms" else failedMessage
}
