// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.AppState
import app.effects.resolveActiveNetworkConfig
import app.withActiveTrafficConfigApplied
import app.ProxyServerState
import app.modes.RunModeVpnService
import engine.stats.ProxyTrafficStatsRuntime
import engine.stats.ProxyTrafficStatsRuntimeStore
import engine.stats.ProxyTrafficStatsService
import engine.stats.XrayStatsApiListenAddress
import engine.stats.resolveXrayStatsApiPort
import engine.stats.xrayStatsApiExcludedPorts
import engine.vpn.VpnXrayEngine
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import engine.proxy.latency.AndroidProxyLatencyTester
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants

/** VPN-only runtime for SKIPI. ROOT runtimes intentionally are not linked here. */
class AndroidProxyEngine(
    context: Context,
    requestVpnPermission: suspend (Intent) -> Boolean,
) {
    private val appContext = context.applicationContext
    private val vpnXrayEngine = VpnXrayEngine(appContext, requestVpnPermission)
    private val latencyTester by lazy { AndroidProxyLatencyTester(appContext) }
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus = globalOperationMutex.withLock {
        startUnlocked(request, restart = false)
    }

    suspend fun restart(request: ProxyEngineStartRequest): ProxyEngineStatus = globalOperationMutex.withLock {
        startUnlocked(request, restart = true)
    }

    suspend fun stop(preferredRunMode: Int? = null): ProxyEngineStatus = globalOperationMutex.withLock {
        withContext(Dispatchers.Default) {
            ProxyTrafficStatsService.reconcile(appContext, null)
            ProxyTrafficStatsRuntimeStore.clear(appContext)
            vpnXrayEngine.stop()
            ProxyEngineStatus(running = false, runMode = RunModeVpnService)
        }
    }

    suspend fun stopCurrentRunMode(runMode: Int): ProxyEngineStatus = stop(runMode)

    suspend fun shutdownCurrentRunMode(runMode: Int): ProxyEngineStatus = stop(runMode)

    suspend fun status(
        preferredRunMode: Int? = null,
        appState: AppState? = null,
    ): ProxyEngineStatus = globalOperationMutex.withLock {
        withContext(Dispatchers.Default) {
            vpnXrayEngine.status()
                .copy(runMode = RunModeVpnService)
                .withTrafficStatsReconciled(appState)
        }
    }

    private suspend fun startUnlocked(
        request: ProxyEngineStartRequest,
        restart: Boolean,
    ): ProxyEngineStatus = withContext(Dispatchers.Default) {
        val startRequestedAt = SystemClock.elapsedRealtime()
        // A paused notification deliberately outlives its VPN tunnel so the
        // user can resume it. A regular start replaces a live notification.
        if (ProxyTrafficStatsRuntimeStore.read(appContext)?.paused != true) {
            ProxyTrafficStatsService.reconcile(appContext, null)
        }
        val attemptedDynamicPorts = mutableSetOf<Int>()
        repeat(DynamicPortStartAttempts) { attempt ->
            val initialVpnState = request.appState
                .resolveActiveNetworkConfig(appContext)
                .withActiveTrafficConfigApplied()
                .copy(runMode = RunModeVpnService)
                .withResolvedDynamicLocalProxyPort(attemptedDynamicPorts)
            val initialSelectedServer = initialVpnState.proxyServers
                .firstOrNull { server -> server.id == request.selectedServer.id }
                ?: request.selectedServer
            // Starting temporary Xray cores here to validate several candidates
            // can serialize with the real core inside the native runtime. On a
            // phone those cancelled probes may keep running and turn a nominal
            // 3.5-second timeout into a 15-20 second tunnel start. A short,
            // parallel TCP race is sufficient for the initial fallbackTag; the
            // single real Xray core and its Observatory take over immediately.
            val (vpnState, startupMemberId) = (initialSelectedServer.server as? StrategyGroup)
                ?.takeIf { group ->
                    group.enableBurstProbe &&
                        group.strategy != StrategyGroupConstants.TYPE_SELECT
                }
                ?.let { group ->
                    val reachableCandidates = latencyTester.fastProbeStrategyGroupMembers(
                        appState = initialVpnState,
                        strategyGroup = group,
                    )
                    val memberId = reachableCandidates
                        .filterValues { latency -> latency >= 0 }
                        .minByOrNull { (_, latency) -> latency }
                        ?.key
                    initialVpnState.withStrategyGroupStartupFallback(
                        serverId = initialSelectedServer.id,
                        probeLatencies = reachableCandidates,
                    ) to memberId
                }
                ?: (initialVpnState to null)
            val selectedServer = vpnState.proxyServers
                .firstOrNull { server -> server.id == initialSelectedServer.id }
                ?: initialSelectedServer
            AndroidAppLogger.info(
                LogTag,
                "VPN start prepared in ${SystemClock.elapsedRealtime() - startRequestedAt}ms; " +
                    "selectedServerId=${selectedServer.id}, startupMemberId=$startupMemberId, source=tcp",
            )

            val (resolvedRequest, trafficStatsRuntime) = request.copy(
                appState = vpnState,
                selectedServer = selectedServer,
            ).withTrafficStatsConfig(
                startupStrategyMemberId = startupMemberId,
            )
            val startResult = runCatching {
                vpnXrayEngine.start(resolvedRequest).copy(appState = vpnState, runMode = RunModeVpnService)
            }
            startResult.onSuccess { status ->
                AndroidAppLogger.info(
                    LogTag,
                    "VPN start completed in ${SystemClock.elapsedRealtime() - startRequestedAt}ms; running=${status.running}",
                )
                if (status.running && trafficStatsRuntime != null) {
                    ProxyTrafficStatsRuntimeStore.write(appContext, trafficStatsRuntime)
                }
                engineScope.launch {
                    ProxyTrafficStatsService.reconcile(
                        appContext,
                        trafficStatsRuntime.takeIf { status.running && resolvedRequest.appState.enableTrafficStatsNotification },
                    )
                }
            }
            if (startResult.isSuccess) {
                return@withContext startResult.getOrThrow()
            }

            val error = checkNotNull(startResult.exceptionOrNull())
            val canRetryWithAnotherPort = request.appState.enableDynamicLocalProxyPort &&
                attempt + 1 < DynamicPortStartAttempts &&
                error.isLocalPortBindFailure()
            if (canRetryWithAnotherPort) {
                vpnState.localProxyPort.toIntOrNull()?.let(attemptedDynamicPorts::add)
                AndroidAppLogger.warn(
                    LogTag,
                    "Local proxy port ${vpnState.localProxyPort} was claimed during startup; retrying with a new port",
                    error,
                )
                return@repeat
            }

            ProxyTrafficStatsService.reconcile(appContext, null)
            ProxyTrafficStatsRuntimeStore.clear(appContext)
            throw error
        }
        error("Dynamic local proxy port startup exhausted without a result")
    }

    private fun ProxyEngineStartRequest.withTrafficStatsConfig(
        startupStrategyMemberId: Int?,
    ): Pair<ProxyEngineStartRequest, ProxyTrafficStatsRuntime?> {
        val port = resolveXrayStatsApiPort(
            preferredPort = ProxyTrafficStatsRuntimeStore.readPort(appContext),
            excludedPorts = appState.xrayStatsApiExcludedPorts(),
        )
        val request = copy(
            xrayStatsApiListenAddress = XrayStatsApiListenAddress,
            xrayStatsApiPort = port,
        )
        val statsApiConfig = checkNotNull(request.xrayStatsApiConfig())
        return request to ProxyTrafficStatsRuntime(
            listenAddress = statsApiConfig.listenAddress,
            port = statsApiConfig.port,
            serverName = selectedServer.trafficStatsServerName(),
            apiTag = statsApiConfig.apiTag,
            finalOutboundTag = appState.defaultRouteOutboundTag,
            selectedServerId = selectedServer.id,
            startupStrategyMemberId = startupStrategyMemberId,
        )
    }

    private fun ProxyEngineStatus.withTrafficStatsReconciled(appState: AppState?): ProxyEngineStatus {
        if (!running) {
            if (ProxyTrafficStatsRuntimeStore.read(appContext)?.paused != true) {
                ProxyTrafficStatsService.reconcile(appContext, null)
                ProxyTrafficStatsRuntimeStore.clear(appContext)
            }
        } else {
            if (appState?.enableTrafficStatsNotification == true) {
                ProxyTrafficStatsRuntimeStore.read(appContext)?.let { runtime ->
                    ProxyTrafficStatsService.reconcile(appContext, runtime)
                }
            } else {
                ProxyTrafficStatsService.reconcile(appContext, null)
            }
        }
        return this
    }

    companion object {
        private const val LogTag = "AndroidProxyEngine"
        private const val DynamicPortStartAttempts = 2
        private val globalOperationMutex = Mutex()
    }
}

internal fun Throwable.isLocalPortBindFailure(): Boolean {
    return generateSequence(this) { cause -> cause.cause }
        .mapNotNull(Throwable::message)
        .any { message ->
            val normalized = message.lowercase()
            "address already in use" in normalized ||
                "failed to listen" in normalized ||
                "listen tcp" in normalized
        }
}

private fun ProxyServerState.trafficStatsServerName(): String {
    val info = server.getInfo()
    return info.remarks.ifBlank { info.protocol }
}
