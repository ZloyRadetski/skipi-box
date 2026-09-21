// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.content.Context
import app.AppState
import app.ProxyServerState
import app.effects.resolveActiveNetworkConfig
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyEngineStatus
import engine.stats.CoreTrafficStatsSampler
import engine.stats.xrayTrafficExcludedInboundTags
import features.config.withActiveTrafficConfig
import kotlinx.coroutines.CancellationException
import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelController
import platform.TunnelFailure
import platform.TunnelPhase
import platform.TunnelSnapshot
import platform.TunnelTraffic
import platform.aggregateCoreInboundTraffic
import platform.toTunnelTraffic

/**
 * Android implementation of the shared tunnel lifecycle contract.
 *
 * The implementation retains Android-only concerns (VPN permission, active
 * network profiles and foreground services) behind the same interface that
 * Desktop fulfils through its in-process SKIPI Core controller.
 */
internal class AndroidTunnelController(
    private val readState: () -> AppState,
    private val updateState: ((AppState) -> AppState) -> Unit,
    private val prepareForConnection: suspend (AppState) -> AppState,
    private val readStatus: suspend (AppState) -> Result<ProxyEngineStatus>,
    private val startService: suspend (AppState, ProxyServerState) -> ProxyServiceResult,
    private val stopService: suspend (Int) -> ProxyServiceResult,
    private val readTraffic: () -> TunnelTraffic = ::sampledTunnelTraffic,
) : TunnelController {
    override suspend fun connect(request: TunnelConnectRequest): Result<Unit> = resultOf {
        val state = prepareForConnection(readState())
        val profileId = request.profileId.trim().toIntOrNull()
        val server = profileId
            ?.let { id -> state.proxyServers.firstOrNull { server -> server.id == id } }
            ?: throw IllegalArgumentException("Selected tunnel profile is unavailable")
        val alreadyRunning = readStatus(state).getOrThrow().running
        if (alreadyRunning) {
            synchronizeRuntime(running = true)
            throw IllegalStateException("Tunnel is already running")
        }

        when (val result = startService(state, server)) {
            is ProxyServiceResult.Success -> {
                synchronizeRuntime(
                    running = result.proxyRunning,
                    resolvedState = result.appState,
                )
                check(result.proxyRunning) { "Android VPN service did not enter the running state" }
            }

            ProxyServiceResult.MissingServer -> throw IllegalArgumentException("Selected tunnel profile is unavailable")

            is ProxyServiceResult.Failed -> {
                synchronizeRuntime(running = false)
                throw result.error
            }
        }
    }

    override suspend fun disconnect(): Result<Unit> = resultOf {
        when (val result = stopService(readState().runMode)) {
            is ProxyServiceResult.Success -> {
                synchronizeRuntime(
                    running = result.proxyRunning,
                    resolvedState = result.appState,
                )
                check(!result.proxyRunning) { "Android VPN service is still running after disconnect" }
            }

            ProxyServiceResult.MissingServer -> error("Android VPN service cannot stop without a tunnel profile")

            is ProxyServiceResult.Failed -> {
                synchronizeRuntime(running = false)
                throw result.error
            }
        }
    }

    override suspend fun snapshot(): TunnelSnapshot {
        val state = readState()
        val status = readStatus(state).getOrElse { error -> return failedSnapshot(error) }
        synchronizeRuntime(
            running = status.running,
            resolvedState = status.appState,
        )
        return if (status.running) {
            TunnelSnapshot(
                phase = TunnelPhase.Connected,
                traffic = readTraffic(),
            )
        } else {
            TunnelSnapshot()
        }
    }

    override fun supports(capability: TunnelCapability): Boolean = when (capability) {
        TunnelCapability.SystemProxy -> false
        TunnelCapability.Tun,
        TunnelCapability.SplitTunneling,
        TunnelCapability.KillSwitch,
        TunnelCapability.BackgroundExecution -> true
    }

    private fun synchronizeRuntime(
        running: Boolean,
        resolvedState: AppState? = null,
    ) {
        updateState { current ->
            val localProxyPort = resolvedState?.localProxyPort ?: current.localProxyPort
            if (current.proxyRunning == running && current.localProxyPort == localProxyPort) {
                current
            } else {
                current.copy(
                    proxyRunning = running,
                    localProxyPort = localProxyPort,
                )
            }
        }
    }

    private fun failedSnapshot(error: Throwable): TunnelSnapshot = TunnelSnapshot(
        phase = TunnelPhase.Failed,
        failure = TunnelFailure(
            code = "android_vpn",
            message = error.message ?: error::class.simpleName.orEmpty(),
            recoverable = true,
        ),
    )

    companion object {
        fun forApp(
            context: Context,
            proxyEngine: AndroidProxyEngine,
            proxyServiceUseCase: ProxyServiceUseCase,
            readState: () -> AppState,
            updateState: ((AppState) -> AppState) -> Unit,
        ): AndroidTunnelController {
            val appContext = context.applicationContext
            return AndroidTunnelController(
                readState = readState,
                updateState = updateState,
                prepareForConnection = { state ->
                    val resolved = state.resolveActiveNetworkConfig(appContext)
                    if (resolved.activeTrafficConfigId != state.activeTrafficConfigId) {
                        updateState { current ->
                            current.withActiveTrafficConfig(resolved.activeTrafficConfigId)
                        }
                    }
                    resolved
                },
                readStatus = { state ->
                    try {
                        Result.success(proxyEngine.status(appState = state))
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        Result.failure(error)
                    }
                },
                startService = proxyServiceUseCase::start,
                stopService = proxyServiceUseCase::stop,
            )
        }
    }
}

private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    Result.failure(error)
}

/** Reads the sampler cache only; it never starts a second native Core poller. */
private fun sampledTunnelTraffic(): TunnelTraffic = CoreTrafficStatsSampler.samples.value
    ?.snapshot
    ?.inbound
    ?.aggregateCoreInboundTraffic(xrayTrafficExcludedInboundTags())
    ?.toTunnelTraffic()
    ?: TunnelTraffic()
