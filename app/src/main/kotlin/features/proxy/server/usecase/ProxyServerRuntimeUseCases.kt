// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerLatencyTesting
import app.ProxyServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
            if (!status.running) return@withLock

            val currentActiveServerId = ProxyTrafficStatsRuntimeStore.read(stateStore.context)?.selectedServerId
            if (currentActiveServerId == serverId) {
                return@withLock
            }

            val server = stateSnapshot.proxyServers.firstOrNull { it.id == serverId } ?: return@withLock
            try {
                val restartedStatus = proxyEngine.restart(
                    ProxyEngineStartRequest(
                        appState = stateSnapshot,
                        selectedServer = server,
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
) {
    if (targetServers.isEmpty()) {
        scope.launch {
            tipNotifier.show(noTestableServersMessage)
            withContext(Dispatchers.Main.immediate) {
                onFinished?.invoke()
            }
        }
        return
    }

    scope.launch {
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

            supervisorScope {
                targetServers.map { server ->
                    async {
                        val latency = semaphore.withPermit {
                            runCatching {
                                proxyLatencyTester.test(
                                    appState = stateSnapshot,
                                    server = server,
                                    mode = mode,
                                    sessionCache = sessionCache,
                                    semaphore = semaphore,
                                    dnsCache = dnsCache,
                                    failedDnsCache = failedDnsCache,
                                )
                            }.getOrElse {
                                ProxyServerLatencyTestResult.Failed
                            }.toLatencyText(latencyFailedMessage)
                        }
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
                    }
                }.awaitAll()
            }

            if (!showSingleResult) {
                tipNotifier.show(doneTemplate.formatTemplate("count" to targetServers.size))
            }
        } finally {
            stateStore.update(persist = true) { state ->
                state.copy(
                    proxyServers = state.proxyServers.map { server ->
                        if (server.latency == ProxyServerLatencyTesting) server.copy(latency = "") else server
                    },
                )
            }
            withContext(Dispatchers.Main.immediate) {
                onFinished?.invoke()
            }
        }
    }
}

private fun ProxyServerLatencyTestResult.toLatencyText(failedMessage: String): String {
    return if (elapsedMillis >= 0) "$elapsedMillis ms" else failedMessage
}
