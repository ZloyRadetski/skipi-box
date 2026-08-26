// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import android.content.Context
import android.os.SystemClock
import app.AppState
import app.ProxyServerState
import features.logs.AndroidAppLogger
import engine.xray.XrayConfigRequest
import engine.xray.XraySpeedTestConfigFactory
import engine.xray.initializeAndroidXrayCoreEnvironment
import features.resources.runtime.prepareXrayResourceFilePaths
import engine.xray.prepareXrayCoreLogPaths
import engine.network.NetworkDefaults
import engine.network.toPortOrNull
import engine.xray.strategyGroupMembers
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.proxy.server.model.customXrayConfigProxyOutboundEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
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
    private val xrayDataDir by lazy {
        val resourceFilePaths = appContext.prepareXrayResourceFilePaths()
        appContext.initializeAndroidXrayCoreEnvironment(resourceFilePaths.dataDir)
        resourceFilePaths.dataDir
    }

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
            val elapsedMillis = when (mode) {
                ProxyServerLatencyTestMode.TcpConnect -> tcpConnectLatency(appState, server, dnsCache, failedDnsCache)
                ProxyServerLatencyTestMode.RealConnection -> realConnectionLatency(appState, server, dnsCache, failedDnsCache)
            }
            ProxyServerLatencyTestResult(elapsedMillis)
        }

        sessionCache?.put(server.id, result.elapsedMillis)
        recordLatencyForRestart(server, result.elapsedMillis)
        result
    }

    /**
     * Persists measured latencies so a balancer restart can land instantly on
     * a recently verified member instead of racing blind TCP probes. Group
     * rows are skipped; their members are cached by their own test() calls.
     */
    private fun recordLatencyForRestart(server: ProxyServerState, elapsedMillis: Long) {
        if (server.server is StrategyGroup) return
        runCatching {
            if (elapsedMillis >= 0) {
                ProxyPingResultCache.record(appContext, server.id, elapsedMillis)
            } else {
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
                    semaphore = null,
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

    suspend fun fastProbeStrategyGroupMembers(
        appState: AppState,
        strategyGroup: StrategyGroup,
    ): Map<Int, Long> = withContext(Dispatchers.IO) {
        val members = appState.strategyGroupMembers(strategyGroup)
            .filter { member -> member.server !is StrategyGroup }
        if (members.isEmpty()) return@withContext emptyMap()

        // Persistent fast-path: use the sorted ping cache built during previous
        // sessions. Verify each candidate with a single TCP connect; take the
        // first one that is still alive. Generous timeouts are needed on mobile
        // (LTE DNS + connect typically takes 300-600 ms).
        withTimeoutOrNull(CachedCandidateBudgetMillis) {
            val cachedCandidates = ProxyPingResultCache.freshest(appContext, members.map { member -> member.id })
                .entries
                .sortedBy { entry -> entry.value }
                .take(CachedCandidateAttempts)
            for ((candidateId, _) in cachedCandidates) {
                val candidate = members.firstOrNull { member -> member.id == candidateId } ?: continue
                val verifiedMillis = verifyCachedCandidate(candidate)
                if (verifiedMillis >= 0) {
                    ProxyPingResultCache.record(appContext, candidateId, verifiedMillis)
                    AndroidAppLogger.debug(
                        LogTag,
                        "Startup member from ping cache: serverId=$candidateId result=${verifiedMillis}ms",
                    )
                    return@withTimeoutOrNull mapOf(candidateId to verifiedMillis)
                }
                ProxyPingResultCache.invalidate(appContext, candidateId)
            }
            null
        }?.let { return@withContext it }

        // In-memory fast-path: if the user ran a ping test this session, use
        // the member with the lowest known latency immediately without any
        // network calls.
        val knownLatencies = members
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

        // Cold start: no cache, no known latencies. Return empty so the caller
        // picks the first member and starts immediately; Xray Observatory will
        // find the best server in the background within one probe interval.
        emptyMap()
    }

    /**
     * One short TCP connect against a cached balancer candidate. Confirms the
     * remembered server is still reachable before committing the tunnel start
     * to it; returns the measured round trip or [FailedDelayMillis].
     */
    private suspend fun verifyCachedCandidate(member: ProxyServerState): Long {
        val endpoint = member.server.endpoint() ?: return FailedDelayMillis
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
        val endpoint = server.server.endpoint() ?: return FailedDelayMillis
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
        val endpoint = server.server.endpoint()
        if (endpoint != null && failedDnsCache?.containsKey(endpoint.host) == true) {
            return FailedDelayMillis
        }

        val timeoutMs = appState.subscriptionPingTimeoutMillis.resolvedPingTimeoutMillis().toLong()
        val pingUrl = appState.subscriptionPingUrl.resolvedPingUrl()

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val future = httpPingExecutor.submit {
                    val millis = runCatching {
                        val configJson = XraySpeedTestConfigFactory.buildXraySpeedTestConfig(
                            XrayConfigRequest(
                                appState = appState,
                                selectedServer = server,
                                inbounds = emptyList<JsonObject>(),
                                coreLogPaths = appContext.prepareXrayCoreLogPaths(),
                                dataDir = xrayDataDir,
                            ),
                        )
                        Skipicore.measureOutboundDelay(configJson, pingUrl)
                    }.onSuccess { delay ->
                        AndroidAppLogger.debug(LogTag, "Real connection latency test serverId=${server.id} result=${delay}ms")
                    }.onFailure { error ->
                        AndroidAppLogger.warn(
                            LogTag,
                            "Real connection latency test failed serverId=${server.id}: ${error.logSummary()}",
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

private data class ProxyServerEndpoint(
    val host: String,
    val port: Int,
)

private fun ProxyServer<*>.endpoint(): ProxyServerEndpoint? {
    return when (this) {
        is HTTP -> endpoint(server, port)
        is Hysteria2 -> endpoint(server, port)
        is Shadowsocks -> endpoint(server, port)
        is Socks -> endpoint(server, port)
        is Trojan -> endpoint(server, port)
        is VLESS -> endpoint(server, port)
        is VMess -> endpoint(server, port)
        is Wireguard -> endpoint(server, port)
        is Custom -> customXrayConfigProxyOutboundEndpoint(configJson)
            ?.let { endpoint -> ProxyServerEndpoint(endpoint.host, endpoint.port) }
        else -> null
    }
}

private fun endpoint(host: String, port: String): ProxyServerEndpoint? {
    val parsedPort = port.toPortOrNull() ?: return null
    return host.trim()
        .takeIf(String::isNotEmpty)
        ?.let { ProxyServerEndpoint(it, parsedPort) }
}

private const val LogTag = "ProxyLatencyTest"
private const val FailedDelayMillis = -1L
private const val StartupCandidateCount = 1
private const val CachedCandidateAttempts = 8
/** Total budget for cached-candidate verification; covers LTE DNS + connect (300-600 ms each). */
private const val CachedCandidateBudgetMillis = 3_000L
private const val CachedCandidateDnsTimeoutMillis = 600L
private const val CachedCandidateConnectTimeoutMillis = 800
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
