// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
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
        // A paused notification deliberately outlives its VPN tunnel so the
        // user can resume it. A regular start replaces a live notification.
        if (ProxyTrafficStatsRuntimeStore.read(appContext)?.paused != true) {
            ProxyTrafficStatsService.reconcile(appContext, null)
        }
        val initialVpnState = request.appState
            .resolveActiveNetworkConfig(appContext)
            .withActiveTrafficConfigApplied()
            .copy(runMode = RunModeVpnService)
            .withResolvedDynamicLocalProxyPort()
        val initialSelectedServer = initialVpnState.proxyServers
            .firstOrNull { server -> server.id == request.selectedServer.id }
            ?: request.selectedServer
        val (vpnState, verifiedStartupMemberId) = (initialSelectedServer.server as? StrategyGroup)
            ?.takeIf { group ->
                group.enableBurstProbe &&
                    group.strategy !in setOf(
                        StrategyGroupConstants.TYPE_SELECT,
                        StrategyGroupConstants.TYPE_FALLBACK,
                    )
            }
            ?.let { group ->
                val tcpCandidates = latencyTester.fastProbeStrategyGroupMembers(
                    appState = initialVpnState,
                    strategyGroup = group,
                    maxConcurrency = initialVpnState.subscriptionPingConcurrency,
                )
                val verifiedCandidates = latencyTester.verifyStrategyGroupStartupCandidates(
                    appState = initialVpnState,
                    strategyGroup = group,
                    tcpCandidates = tcpCandidates,
                )
                val verifiedMemberId = verifiedCandidates
                    .filterValues { latency -> latency >= 0 }
                    .minByOrNull { (_, latency) -> latency }
                    ?.key
                initialVpnState.withStrategyGroupStartupFallback(initialSelectedServer.id, verifiedCandidates) to verifiedMemberId
            }
            ?: (initialVpnState to null)
        val selectedServer = vpnState.proxyServers
            .firstOrNull { server -> server.id == initialSelectedServer.id }
            ?: initialSelectedServer

        val (resolvedRequest, trafficStatsRuntime) = request.copy(
            appState = vpnState,
            selectedServer = selectedServer,
        ).withTrafficStatsConfig(startupStrategyMemberId = verifiedStartupMemberId)
        runCatching { vpnXrayEngine.start(resolvedRequest).copy(appState = vpnState, runMode = RunModeVpnService) }
            .onSuccess { status ->
                if (status.running && trafficStatsRuntime != null) {
                    ProxyTrafficStatsRuntimeStore.write(appContext, trafficStatsRuntime)
                }
                ProxyTrafficStatsService.reconcile(
                    appContext,
                    trafficStatsRuntime.takeIf { status.running && resolvedRequest.appState.enableTrafficStatsNotification },
                )
            }
            .onFailure {
                ProxyTrafficStatsService.reconcile(appContext, null)
                ProxyTrafficStatsRuntimeStore.clear(appContext)
            }
            .getOrThrow()
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
        private val globalOperationMutex = Mutex()
    }
}

private fun ProxyServerState.trafficStatsServerName(): String {
    val info = server.getInfo()
    return info.remarks.ifBlank { info.protocol }
}
