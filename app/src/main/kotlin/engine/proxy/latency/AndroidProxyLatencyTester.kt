// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import android.content.Context
import android.os.SystemClock
import app.AppState
import app.ProxyServerState
import app.activeTrafficConfig
import features.logs.AndroidAppLogger
import engine.xray.XrayConfigRequest
import engine.xray.XraySpeedTestConfigFactory
import engine.xray.initializeAndroidXrayCoreEnvironment
import features.resources.runtime.prepareXrayResourceFilePaths
import features.resources.runtime.XrayResourceFileScope
import engine.xray.prepareXrayCoreLogPaths
import engine.network.NetworkDefaults
import engine.xray.strategyGroupMembers
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.proxy.server.model.connectionEndpointOrNull
import engine.vpn.OlcRtcReadinessTimeoutMillis
import engine.vpn.SkipiCoreRuntime
import engine.vpn.SkipiVpnService
import engine.vpn.buildLoopbackSocksOutbound
import engine.vpn.findAvailableLocalPort
import engine.vpn.olcRtcRawDnsEndpoint
import engine.vpn.toNativeRunnerConfigJson
import engine.vpn.toTunOptions
import engine.vpn.withNativeRunnerEndpointResolved
import engine.xray.XrayTags
import engine.xray.buildXrayOutboundPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import app.skipi.core.skipicore.Skipicore
import java.io.IOException
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

private val dnsDispatcher = Dispatchers.IO.limitedParallelism(32)
// Bounded thread pool: avoids spinning up thousands of native threads when
// testing large server lists. The semaphore on the caller side keeps
// concurrency at a reasonable level; this pool just prevents unbounded
// thread creation if semaphore is not provided.
private val httpPingExecutor = Executors.newFixedThreadPool(32) { runnable ->
    Thread(runnable, "cgo-http-ping").apply { isDaemon = true }
}

internal class AndroidProxyLatencyTester(
    context: Context,
) {
    private val appContext = context.applicationContext
    private fun xrayDataDir(appState: AppState): String {
        val scope = appState.activeTrafficConfig()?.let { config ->
            XrayResourceFileScope(
                trafficConfigId = config.id,
                resourceFileSource = config.resourceSettings.source,
            )
        }
        val resourceFilePaths = appContext.prepareXrayResourceFilePaths(scope = scope)
        appContext.initializeAndroidXrayCoreEnvironment(resourceFilePaths.dataDir)
        return resourceFilePaths.dataDir
    }
    private val tempOlcRtcMutex = Mutex()
    private val tempAmneziaWgMutex = Mutex()

    suspend fun test(
        appState: AppState,
        server: ProxyServerState,
        mode: ProxyServerLatencyTestMode,
        sessionCache: ConcurrentMap<Int, Long>? = null,
        semaphore: Semaphore? = null,
        dnsCache: ConcurrentMap<String, java.net.InetAddress>? = null,
        failedDnsCache: ConcurrentMap<String, Boolean>? = null,
    ): ProxyServerLatencyTestResult = withContext(Dispatchers.IO) {
        sessionCache?.get(server.id)?.let { cachedMillis ->
            return@withContext ProxyServerLatencyTestResult(cachedMillis)
        }

        val result = if (server.server is StrategyGroup) {
            testStrategyGroupLatency(appState, server, mode, sessionCache, semaphore, dnsCache, failedDnsCache)
        } else {
            val elapsedMillis = if (semaphore != null) {
                semaphore.withPermit {
                    measureServerLatency(appState, server, mode, dnsCache, failedDnsCache)
                }
            } else {
                measureServerLatency(appState, server, mode, dnsCache, failedDnsCache)
            }
            ProxyServerLatencyTestResult(elapsedMillis)
        }

        sessionCache?.put(server.id, result.elapsedMillis)
        recordLatencyForRestart(server, result.elapsedMillis, mode)
        result
    }

    /**
     * Persists measured latencies so a balancer restart can land instantly on
     * a recently verified member instead of racing blind TCP probes. Group
     * rows are skipped; their members are cached by their own test() calls.
     *
     * Invalidation only happens on [ProxyServerLatencyTestMode.TcpConnect]
     * failures because those probes run directly to the server, outside the
     * VPN tunnel. [ProxyServerLatencyTestMode.RealConnection] failures are
     * unreliable indicators of a dead server: they go *through* the active
     * tunnel, so a broken tunnel poisons every entry even though the servers
     * themselves are fine.
     */
    private fun recordLatencyForRestart(
        server: ProxyServerState,
        elapsedMillis: Long,
        mode: ProxyServerLatencyTestMode,
    ) {
        // A TCP socket check is not evidence that a UDP/QUIC/WebRTC proxy is
        // usable. Do not let such a result become a future startup fallback.
        if (!server.server.supportsTcpStartupProbe()) return
        runCatching {
            if (elapsedMillis >= 0) {
                ProxyPingResultCache.record(appContext, server.id, elapsedMillis)
            } else if (mode == ProxyServerLatencyTestMode.TcpConnect) {
                // Only a direct TCP failure is a reliable signal that the
                // server is unreachable. Skip invalidation for RealConnection
                // failures to avoid wiping good cached entries when the VPN
                // tunnel itself is broken.
                ProxyPingResultCache.invalidate(appContext, server.id)
            }
        }
    }

    private suspend fun testStrategyGroupLatency(
        appState: AppState,
        server: ProxyServerState,
        mode: ProxyServerLatencyTestMode,
        sessionCache: ConcurrentMap<Int, Long>?,
        semaphore: Semaphore?,
        dnsCache: ConcurrentMap<String, java.net.InetAddress>?,
        failedDnsCache: ConcurrentMap<String, Boolean>?,
    ): ProxyServerLatencyTestResult = coroutineScope {
        val strategyGroup = server.server as? StrategyGroup ?: return@coroutineScope ProxyServerLatencyTestResult.Failed
        val members = appState.strategyGroupMembers(strategyGroup)
            .filter { member -> member.server !is StrategyGroup }
        if (members.isEmpty()) {
            return@coroutineScope ProxyServerLatencyTestResult.Failed
        }

        val results = members.map { member ->
            async {
                test(
                    appState = appState,
                    server = member,
                    mode = mode,
                    sessionCache = sessionCache,
                    semaphore = semaphore,
                    dnsCache = dnsCache,
                    failedDnsCache = failedDnsCache,
                )
            }
        }.awaitAll()

        val validResults = results.filter { it.elapsedMillis >= 0 }
        if (validResults.isEmpty()) {
            return@coroutineScope ProxyServerLatencyTestResult.Failed
        }

        val chosenDelay = when (strategyGroup.strategy) {
            StrategyGroupConstants.TYPE_LEAST_PING -> validResults.minOf { it.elapsedMillis }
            StrategyGroupConstants.TYPE_ROUND_ROBIN,
            StrategyGroupConstants.TYPE_RANDOM,
            StrategyGroupConstants.TYPE_LEAST_LOAD -> validResults.minOf { it.elapsedMillis }
            else -> validResults.minOf { it.elapsedMillis }
        }

        ProxyServerLatencyTestResult(chosenDelay)
    }

    private suspend fun measureServerLatency(
        appState: AppState,
        server: ProxyServerState,
        mode: ProxyServerLatencyTestMode,
        dnsCache: ConcurrentMap<String, java.net.InetAddress>?,
        failedDnsCache: ConcurrentMap<String, Boolean>?,
    ): Long {
        return when (mode) {
            ProxyServerLatencyTestMode.TcpConnect -> tcpConnectLatency(appState, server, dnsCache, failedDnsCache)
            ProxyServerLatencyTestMode.RealConnection -> realConnectionLatency(appState, server, dnsCache, failedDnsCache)
        }
    }

    suspend fun fastProbeStrategyGroupMembers(
        appState: AppState,
        strategyGroup: StrategyGroup,
    ): Map<Int, Long> = withContext(Dispatchers.IO) {
        val members = appState.strategyGroupMembers(strategyGroup)
            .filter { member -> member.server !is StrategyGroup }
        if (members.isEmpty()) return@withContext emptyMap()
        val tcpProbeMembers = members.filter { member -> member.server.supportsTcpStartupProbe() }
        if (tcpProbeMembers.isEmpty()) {
            AndroidAppLogger.debug(
                LogTag,
                "Startup TCP probe skipped: group has no TCP-compatible members",
            )
            return@withContext emptyMap()
        }

        // 1. Persistent fast-path: use the first cached TCP-compatible member
        // that answers. Waiting for every losing candidate delayed startup even
        // after a healthy member had already replied.
        val cachedCandidates = ProxyPingResultCache.freshest(appContext, tcpProbeMembers.map { member -> member.id })
            .entries
            .sortedBy { entry -> entry.value }
            .take(CachedCandidateAttempts)

        if (cachedCandidates.isNotEmpty()) {
            val verifiedWinner = firstReachableTcpStartupCandidate(
                candidates = cachedCandidates.mapNotNull { (candidateId, _) ->
                    tcpProbeMembers.firstOrNull { member -> member.id == candidateId }
                },
                budgetMillis = CachedCandidateBudgetMillis,
                updatePersistentCache = true,
            )

            if (verifiedWinner != null) {
                AndroidAppLogger.debug(
                    LogTag,
                    "Startup member from ping cache: serverId=${verifiedWinner.first} result=${verifiedWinner.second}ms",
                )
                return@withContext mapOf(verifiedWinner.first to verifiedWinner.second)
            }
        }

        // 2. In-memory fast-path: if the user ran a ping test this session, use
        // the member with the lowest known latency immediately without any network calls.
        val knownLatencies = tcpProbeMembers
            .mapNotNull { member ->
                val parsed = member.latency.trim().removeSuffix("ms").trim().toLongOrNull()
                if (parsed != null && parsed >= 0) member.id to parsed else null
            }
        if (knownLatencies.isNotEmpty()) {
            val best = knownLatencies.minByOrNull { it.second }
            if (best != null) {
                return@withContext mapOf(best.first to best.second)
            }
        }

        // 3. Cold start: fast race among TCP-compatible group candidates with a
        // strict budget. UDP-only transports are left to Xray Observatory or a
        // previously observed real-traffic member.
        val coldCandidates = tcpProbeMembers.take(4)
        if (coldCandidates.isNotEmpty()) {
            val coldWinner = firstReachableTcpStartupCandidate(
                candidates = coldCandidates,
                budgetMillis = ColdCandidateBudgetMillis,
                updatePersistentCache = true,
            )

            if (coldWinner != null) {
                AndroidAppLogger.debug(
                    LogTag,
                    "Startup member from cold race: serverId=${coldWinner.first} result=${coldWinner.second}ms",
                )
                return@withContext mapOf(coldWinner.first to coldWinner.second)
            }
        }

        emptyMap()
    }

    private suspend fun firstReachableTcpStartupCandidate(
        candidates: List<ProxyServerState>,
        budgetMillis: Long,
        updatePersistentCache: Boolean,
    ): Pair<Int, Long>? {
        if (candidates.isEmpty()) return null
        return withTimeoutOrNull(budgetMillis) {
            supervisorScope {
                val winner = CompletableDeferred<Pair<Int, Long>?>()
                val remaining = AtomicInteger(candidates.size)
                candidates.forEach { candidate ->
                    launch {
                        val verifiedMillis = runCatching {
                            verifyTcpStartupCandidate(candidate)
                        }.getOrDefault(FailedDelayMillis)
                        val result = if (verifiedMillis >= 0) {
                            if (updatePersistentCache) {
                                ProxyPingResultCache.record(appContext, candidate.id, verifiedMillis)
                            }
                            candidate.id to verifiedMillis
                        } else {
                            if (updatePersistentCache) {
                                ProxyPingResultCache.invalidate(appContext, candidate.id)
                            }
                            null
                        }
                        result?.let(winner::complete)
                        if (remaining.decrementAndGet() == 0) {
                            winner.complete(null)
                        }
                    }
                }
                try {
                    winner.await()
                } finally {
                    // The startup fallback is only a short hint. Once a member
                    // answers, abandoned probes must not compete with the real core.
                    coroutineContext.cancelChildren()
                }
            }
        }
    }

    /**
     * One short TCP connect against a TCP-compatible balancer candidate.
     * This is intentionally never called for Hysteria, WireGuard, AmneziaWG,
     * OLCRTC, or an opaque custom outbound.
     */
    private suspend fun verifyTcpStartupCandidate(member: ProxyServerState): Long {
        val endpoint = member.server.connectionEndpointOrNull() ?: return FailedDelayMillis
        return withContext(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            val address = resolveHost(
                host = endpoint.host,
                timeoutMs = CachedCandidateDnsTimeoutMillis,
                dnsCache = null,
                failedDnsCache = null,
            ) ?: return@withContext FailedDelayMillis
            val dnsElapsed = SystemClock.elapsedRealtime() - startedAt
            val connectElapsed = nioSocketConnectTime(address, endpoint.port, CachedCandidateConnectTimeoutMillis)
            if (connectElapsed >= 0) dnsElapsed + connectElapsed else FailedDelayMillis
        }
    }

    private suspend fun tcpConnectLatency(
        appState: AppState,
        server: ProxyServerState,
        dnsCache: ConcurrentMap<String, java.net.InetAddress>?,
        failedDnsCache: ConcurrentMap<String, Boolean>?,
    ): Long {
        val endpoint = server.server.connectionEndpointOrNull() ?: return FailedDelayMillis
        val timeoutMs = appState.subscriptionPingTimeoutMillis.resolvedPingTimeoutMillis().toLong()
        val startedAt = SystemClock.elapsedRealtime()

        val dnsTimeoutMs = (timeoutMs / 2).coerceIn(300L, 1200L)
        val address = resolveHost(endpoint.host, dnsTimeoutMs, dnsCache, failedDnsCache) ?: return FailedDelayMillis

        val dnsElapsed = SystemClock.elapsedRealtime() - startedAt
        val remainingTimeout = (timeoutMs - dnsElapsed).coerceAtLeast(150L).toInt()

        val connectElapsed = withTimeoutOrNull(remainingTimeout.toLong()) {
            withContext(Dispatchers.IO) {
                nioSocketConnectTime(address, endpoint.port, remainingTimeout)
            }
        } ?: FailedDelayMillis

        val totalLatency = if (connectElapsed >= 0) dnsElapsed + connectElapsed else FailedDelayMillis
        AndroidAppLogger.debug(LogTag, "TCP latency test serverId=${server.id} result=${totalLatency}ms (dns=${dnsElapsed}ms, connect=${connectElapsed}ms)")
        return totalLatency
    }

    private fun nioSocketConnectTime(address: java.net.InetAddress, port: Int, timeoutMillis: Int): Long {
        val started = SystemClock.elapsedRealtime()
        return runCatching {
            SocketChannel.open().use { channel ->
                channel.configureBlocking(false)
                val socketAddress = InetSocketAddress(address, port)
                val connectedImmediately = channel.connect(socketAddress)
                if (connectedImmediately) {
                    val elapsed = SystemClock.elapsedRealtime() - started
                    return elapsed.coerceAtLeast(0L)
                }

                Selector.open().use { selector ->
                    channel.register(selector, SelectionKey.OP_CONNECT)
                    val selected = selector.select(timeoutMillis.toLong())
                    if (selected > 0 && channel.finishConnect()) {
                        val elapsed = SystemClock.elapsedRealtime() - started
                        elapsed.coerceAtLeast(0L)
                    } else {
                        FailedDelayMillis
                    }
                }
            }
        }.onFailure { error ->
            when (error) {
                is UnknownHostException -> AndroidAppLogger.debug(LogTag, "Unknown host for TCP latency test: ${address.hostAddress}")
                is IOException -> AndroidAppLogger.debug(LogTag, "TCP latency test IO failure: ${address.hostAddress}:$port ${error.message}")
                else -> AndroidAppLogger.warn(LogTag, "TCP latency test failed: ${address.hostAddress}:$port ${error.logSummary()}")
            }
        }.getOrDefault(FailedDelayMillis)
    }

    private suspend fun resolveHost(
        host: String,
        timeoutMs: Long,
        dnsCache: ConcurrentMap<String, java.net.InetAddress>?,
        failedDnsCache: ConcurrentMap<String, Boolean>?,
    ): java.net.InetAddress? {
        val cleanHost = host.trim().trim('[', ']')
        if (cleanHost.isEmpty()) return null

        if (isIpAddress(cleanHost)) {
            return runCatching { java.net.InetAddress.getByName(cleanHost) }.getOrNull()
        }

        if (failedDnsCache?.containsKey(cleanHost) == true) {
            return null
        }

        val cached = dnsCache?.get(cleanHost)
        if (cached != null) {
            return cached
        }

        val resolved = withTimeoutOrNull(timeoutMs) {
            withContext(dnsDispatcher) {
                runCatching {
                    java.net.InetAddress.getByName(cleanHost)
                }.getOrNull()
            }
        }

        if (resolved != null) {
            dnsCache?.put(cleanHost, resolved)
        } else {
            failedDnsCache?.put(cleanHost, true)
        }

        return resolved
    }

    private fun isIpAddress(host: String): Boolean {
        return IPv4Regex.matches(host) || (host.contains(':') && IPv6Regex.matches(host))
    }

    private suspend fun realConnectionLatency(
        appState: AppState,
        server: ProxyServerState,
        dnsCache: ConcurrentMap<String, java.net.InetAddress>?,
        failedDnsCache: ConcurrentMap<String, Boolean>?,
    ): Long {
        val endpoint = server.server.connectionEndpointOrNull()
        if (endpoint != null && failedDnsCache?.containsKey(endpoint.host) == true) {
            return FailedDelayMillis
        }

        val timeoutMs = appState.subscriptionPingTimeoutMillis.resolvedPingTimeoutMillis().toLong()
        val pingUrl = appState.subscriptionPingUrl.resolvedPingUrl()

        val olcServer = server.server as? OlcRtc
        val activeBridge = SkipiCoreRuntime.activeOlcRtcBridge
        val isBridgeActiveForServer = olcServer != null &&
            activeBridge != null &&
            SkipiCoreRuntime.isRunning() &&
            olcServer.matchesRuntimeConfig(activeBridge.server)

        if (olcServer != null && !isBridgeActiveForServer) {
            if (SkipiCoreRuntime.isRunning()) {
                return FailedDelayMillis
            }
            return measureStandaloneOlcRtcLatency(appState, server, olcServer, pingUrl, timeoutMs)
        }

        val amneziaWgServer = server.server as? AmneziaWg
        val activeAmneziaWgBridge = SkipiCoreRuntime.activeAmneziaWgBridge
        val isAmneziaWgBridgeActiveForServer = amneziaWgServer != null &&
            activeAmneziaWgBridge != null &&
            SkipiCoreRuntime.isRunning() &&
            amneziaWgServer.matchesRuntimeConfig(activeAmneziaWgBridge.server)

        if (amneziaWgServer != null && !isAmneziaWgBridgeActiveForServer) {
            if (SkipiCoreRuntime.isRunning()) {
                return FailedDelayMillis
            }
            return measureStandaloneAmneziaWgLatency(appState, server, amneziaWgServer, pingUrl, timeoutMs)
        }

        val request = XrayConfigRequest(
            appState = appState,
            selectedServer = server,
            inbounds = emptyList<JsonObject>(),
            coreLogPaths = appContext.prepareXrayCoreLogPaths(),
            dataDir = xrayDataDir(appState),
        )
        return executeXrayHttpPing(request, server.id, pingUrl, timeoutMs)
    }

    private suspend fun measureStandaloneOlcRtcLatency(
        appState: AppState,
        server: ProxyServerState,
        olcServer: OlcRtc,
        pingUrl: String,
        timeoutMs: Long,
    ): Long = tempOlcRtcMutex.withLock {
        if (SkipiCoreRuntime.isRunning()) {
            return FailedDelayMillis
        }
        val preferredPort = olcServer.localSocksPort.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 10808
        val tempPort = findAvailableLocalPort(preferredPort, emptySet())
        val tempUser = "skipi_rtc_test_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        val tempPass = java.util.UUID.randomUUID().toString().replace("-", "")
        val tempYaml = olcServer.toOlcRtcYamlConfig(
            socksPort = tempPort,
            socksUser = tempUser,
            socksPass = tempPass,
            dnsServer = olcRtcRawDnsEndpoint(appState.toTunOptions()),
        )

        try {
            SkipiCoreRuntime.startOlcRtc(tempYaml, tempPort)
            check(SkipiCoreRuntime.awaitOlcRtcReady(tempPort)) {
                "OLCRTC local SOCKS listener did not become ready within ${OlcRtcReadinessTimeoutMillis}ms"
            }

            val customOutbound = olcServer.toXrayOutboundWithPortAndAuth(
                tag = XrayTags.PROXY,
                port = tempPort,
                user = tempUser,
                pass = tempPass,
            ).toJsonObject()

            val speedTestState = appState.copy(
                enableMux = false,
                enableFakeDns = false,
                enableDirectDnsForProxyServerDomains = true,
            )
            val basePlan = speedTestState.buildXrayOutboundPlan(server)
            val updatedPlan = basePlan.copy(
                proxyOutbounds = basePlan.proxyOutbounds.map { item ->
                    val candidate = item.server as? OlcRtc
                    if (candidate != null && candidate.matchesRuntimeConfig(olcServer)) {
                        item.copy(customOutbound = customOutbound)
                    } else {
                        item
                    }
                },
            )

            val request = XrayConfigRequest(
                appState = appState,
                selectedServer = server,
                inbounds = emptyList<JsonObject>(),
                coreLogPaths = appContext.prepareXrayCoreLogPaths(),
                dataDir = xrayDataDir(appState),
                outboundPlan = updatedPlan,
            )
            executeXrayHttpPing(request, server.id, pingUrl, timeoutMs)
        } catch (e: Throwable) {
            AndroidAppLogger.warn(LogTag, "Standalone olcRTC latency test failed: ${e.logSummary()}")
            FailedDelayMillis
        } finally {
            if (!SkipiCoreRuntime.isRunning() && !SkipiVpnService.isRunning() && SkipiCoreRuntime.activeOlcRtcBridge == null) {
                SkipiCoreRuntime.stopOlcRtc()
            }
        }
    }

    private suspend fun measureStandaloneAmneziaWgLatency(
        appState: AppState,
        server: ProxyServerState,
        amneziaWgServer: AmneziaWg,
        pingUrl: String,
        timeoutMs: Long,
    ): Long = tempAmneziaWgMutex.withLock {
        if (SkipiCoreRuntime.isRunning() || SkipiVpnService.isRunning()) {
            return FailedDelayMillis
        }
        val tempPort = findAvailableLocalPort(preferredPort = 10809, reservedPorts = emptySet())
        val runnerConfigJson = amneziaWgServer.withNativeRunnerEndpointResolved().toNativeRunnerConfigJson(
            tag = "skipi_awg_latency",
            dnsServers = appState.toTunOptions().dnsServers,
        )

        try {
            // There is no active VpnService during a standalone latency test,
            // so no socket needs exclusion from a TUN. The runtime installs a
            // no-op bridge only for this isolated native run.
            SkipiCoreRuntime.startAmneziaWg(
                configJson = runnerConfigJson,
                socksPort = tempPort,
                requireSocketProtector = false,
            )
            check(SkipiCoreRuntime.awaitAmneziaWgReady(tempPort)) {
                "AmneziaWG local SOCKS listener did not become ready within ${OlcRtcReadinessTimeoutMillis}ms"
            }

            val speedTestState = appState.copy(
                enableMux = false,
                enableFakeDns = false,
                enableDirectDnsForProxyServerDomains = true,
            )
            val basePlan = speedTestState.buildXrayOutboundPlan(server)
            val updatedPlan = basePlan.copy(
                proxyOutbounds = basePlan.proxyOutbounds.map { item ->
                    val candidate = item.server as? AmneziaWg
                    if (candidate != null && candidate.matchesRuntimeConfig(amneziaWgServer)) {
                        item.copy(customOutbound = buildLoopbackSocksOutbound(tag = item.tag, port = tempPort))
                    } else {
                        item
                    }
                },
            )
            val request = XrayConfigRequest(
                appState = appState,
                selectedServer = server,
                inbounds = emptyList<JsonObject>(),
                coreLogPaths = appContext.prepareXrayCoreLogPaths(),
                dataDir = xrayDataDir(appState),
                outboundPlan = updatedPlan,
            )
            executeXrayHttpPing(request, server.id, pingUrl, timeoutMs)
        } catch (error: Throwable) {
            AndroidAppLogger.warn(LogTag, "Standalone AmneziaWG latency test failed: ${error.logSummary()}")
            FailedDelayMillis
        } finally {
            if (!SkipiCoreRuntime.isRunning() && !SkipiVpnService.isRunning() && SkipiCoreRuntime.activeAmneziaWgBridge == null) {
                SkipiCoreRuntime.stopAmneziaWg()
            }
        }
    }

    private suspend fun executeXrayHttpPing(
        request: XrayConfigRequest,
        serverId: Int,
        pingUrl: String,
        timeoutMs: Long,
    ): Long {
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val future = httpPingExecutor.submit {
                    val millis = runCatching {
                        val configJson = XraySpeedTestConfigFactory.buildXraySpeedTestConfig(request)
                        Skipicore.measureOutboundDelay(configJson, pingUrl)
                    }.onSuccess { delay ->
                        AndroidAppLogger.debug(LogTag, "Real connection latency test serverId=$serverId result=${delay}ms")
                    }.onFailure { error ->
                        AndroidAppLogger.warn(
                            LogTag,
                            "Real connection latency test failed serverId=$serverId: ${error.logSummary()}",
                        )
                    }.getOrDefault(FailedDelayMillis)

                    if (continuation.isActive) {
                        continuation.resume(if (millis < 0) FailedDelayMillis else millis)
                    }
                }
                continuation.invokeOnCancellation {
                    future.cancel(true)
                }
            }
        } ?: FailedDelayMillis

        return result
    }
}

private fun Throwable.logSummary(): String {
    val type = this::class.simpleName.orEmpty().ifBlank { "Throwable" }
    val detail = message
        ?.replace(WhitespaceRegex, " ")
        ?.trim()
        .orEmpty()
    return if (detail.isEmpty()) type else "$type: $detail"
}

enum class ProxyServerLatencyTestMode {
    TcpConnect,
    RealConnection,
}

data class ProxyServerLatencyTestResult(
    val elapsedMillis: Long,
) {
    companion object {
        val Failed = ProxyServerLatencyTestResult(elapsedMillis = -1L)
    }
}

/** A raw TCP connect may only warm startup fallback for a TCP transport. */
internal fun ProxyServer<*>.supportsTcpStartupProbe(): Boolean {
    return when (this) {
        is HTTP,
        is Socks -> true

        is VLESS -> parms.usesTcpStartupTransport()
        is VMess -> parms.usesTcpStartupTransport()
        is Trojan -> parms.usesTcpStartupTransport()
        is Shadowsocks -> parms.usesTcpStartupTransport()

        // Hysteria2, WireGuard, AmneziaWG and OLCRTC are UDP/QUIC/WebRTC
        // transports. Custom JSON is opaque, so treating it as TCP would be
        // just as unsafe.
        is Hysteria2,
        is Wireguard,
        is AmneziaWg,
        is OlcRtc,
        is Custom,
        is StrategyGroup -> false

        else -> false
    }
}

private fun features.proxy.server.model.V2RayParameters.usesTcpStartupTransport(): Boolean {
    return type
        .trim()
        .lowercase()
        .ifBlank { "raw" } in TcpStartupTransports
}

private val TcpStartupTransports = setOf(
    "raw",
    "tcp",
    "ws",
    "websocket",
    "httpupgrade",
    "xhttp",
    "splithttp",
    "grpc",
)

private const val LogTag = "ProxyLatencyTest"
private const val FailedDelayMillis = -1L
private const val StartupCandidateCount = 1
private const val CachedCandidateAttempts = 4
/** Total budget for parallel cached-candidate verification. */
private const val CachedCandidateBudgetMillis = 800L
/** Total budget for cold-start parallel probe race. */
private const val ColdCandidateBudgetMillis = 350L
private const val CachedCandidateDnsTimeoutMillis = 400L
private const val CachedCandidateConnectTimeoutMillis = 500
private const val DefaultPingTimeoutMillis = 5_000
private const val MinPingTimeoutMillis = 500
private const val MaxPingTimeoutMillis = 60_000
private val WhitespaceRegex = Regex("\\s+")
private val IPv4Regex = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
private val IPv6Regex = Regex("^[0-9a-fA-F:]+$")

private fun String.resolvedPingUrl(): String {
    return trim().takeIf { value -> value.startsWith("https://") || value.startsWith("http://") }
        ?: NetworkDefaults.CONNECTIVITY_CHECK_URL
}

private fun String.resolvedPingTimeoutMillis(): Int {
    return trim().toIntOrNull()?.coerceIn(MinPingTimeoutMillis, MaxPingTimeoutMillis) ?: DefaultPingTimeoutMillis
}
