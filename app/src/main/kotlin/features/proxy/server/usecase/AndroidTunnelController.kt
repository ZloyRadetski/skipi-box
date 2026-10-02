// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.content.Context
import app.AppState
import app.ProxyServerState
import app.skipi.app.runtime.AppRuntimeState
import app.skipi.app.repository.RuntimeStateRepository
import app.effects.resolveActiveNetworkConfig
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyEngineStatus
import engine.stats.CoreTrafficStatsSampler
import engine.stats.xrayTrafficExcludedInboundTags
import features.config.withActiveTrafficConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelController
import platform.TunnelFailure
import platform.TunnelLifecycleDecision
import platform.TunnelLifecyclePolicy
import platform.TunnelOperationException
import platform.TunnelOperationStage
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
    override suspend fun connect(request: TunnelConnectRequest): Result<Unit> = resultOf(TunnelOperationStage.Start) {
        if (request.configuration != null) {
            throw TunnelOperationException(
                TunnelFailure(
                    "capability_unavailable",
                    "Android VPN service resolves configuration from the selected app profile",
                    false,
                    TunnelOperationStage.PrepareConfiguration,
                    TunnelCapability.PreparedConfiguration.name,
                ),
            )
        }
        val state = try {
            prepareForConnection(readState())
        } catch (error: Throwable) {
            throw operationException(error, TunnelOperationStage.PrepareConfiguration)
        }
        val profileId = request.profileId.trim().toIntOrNull()
        val server = profileId
            ?.let { id -> state.proxyServers.firstOrNull { server -> server.id == id } }
            ?: throw TunnelOperationException(
                TunnelFailure("profile_unavailable", "Selected tunnel profile is unavailable", false, TunnelOperationStage.ResolveProfile),
            )
        val status = readStatus(state).fold(
            onSuccess = { it },
            onFailure = { error -> throw operationException(error, TunnelOperationStage.ReadStatus) },
        )
        val decision = TunnelLifecyclePolicy.beginConnect(
            TunnelSnapshot(phase = if (status.running) TunnelPhase.Connected else TunnelPhase.Disconnected),
            request.profileId,
        )
        if (decision is TunnelLifecycleDecision.Rejected) {
            synchronizeRuntime(running = status.running, resolvedState = status.appState)
            throw TunnelOperationException(decision.failure)
        }

        when (val result = startService(state, server)) {
            is ProxyServiceResult.Success -> {
                synchronizeRuntime(
                    running = result.proxyRunning,
                    resolvedState = result.appState,
                )
                check(result.proxyRunning) { "Android VPN service did not enter the running state" }
            }

            ProxyServiceResult.MissingServer -> throw TunnelOperationException(
                TunnelFailure("profile_unavailable", "Selected tunnel profile is unavailable", false, TunnelOperationStage.ResolveProfile),
            )

            is ProxyServiceResult.Failed -> {
                synchronizeRuntime(running = false)
                throw result.error
            }
        }
    }

    override suspend fun disconnect(): Result<Unit> = resultOf(TunnelOperationStage.Stop) {
        when (val result = stopService(readState().runMode)) {
            is ProxyServiceResult.Success -> {
                synchronizeRuntime(
                    running = result.proxyRunning,
                    resolvedState = result.appState,
                )
                check(!result.proxyRunning) { "Android VPN service is still running after disconnect" }
            }

            ProxyServiceResult.MissingServer -> throw TunnelOperationException(
                TunnelFailure("profile_unavailable", "Android VPN service cannot stop without a tunnel profile", false, TunnelOperationStage.Stop),
            )

            is ProxyServiceResult.Failed -> {
                synchronizeRuntime(running = false)
                throw result.error
            }
        }
    }

    override suspend fun snapshot(): TunnelSnapshot {
        val state = readState()
        val profileId = state.selectedProxyServerId?.toString()
        val status = readStatus(state).getOrElse { error ->
            return failedSnapshot(error, TunnelOperationStage.ReadStatus, profileId)
        }
        synchronizeRuntime(
            running = status.running,
            resolvedState = status.appState,
        )
        return if (status.running) {
            TunnelSnapshot(
                phase = TunnelPhase.Connected,
                traffic = readTraffic(),
                profileId = profileId,
            )
        } else {
            TunnelSnapshot(profileId = profileId)
        }
    }

    override fun capabilities(): Set<TunnelCapability> = setOf(
        TunnelCapability.Tun,
        TunnelCapability.SplitTunneling,
        TunnelCapability.KillSwitch,
        TunnelCapability.BackgroundExecution,
    )

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

    private fun failedSnapshot(
        error: Throwable,
        stage: TunnelOperationStage,
        profileId: String?,
    ): TunnelSnapshot = TunnelSnapshot(
        phase = TunnelPhase.Failed,
        failure = TunnelLifecyclePolicy.failure(error, stage, code = "android_runtime", platformCode = "android_vpn"),
        profileId = profileId,
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

/** Publishes live Android tunnel snapshots through the shared runtime repository contract. */
internal class AndroidTunnelRuntimeRepository(
    private val controller: TunnelController,
    scope: CoroutineScope,
    appState: kotlinx.coroutines.flow.StateFlow<AppState>,
) : RuntimeStateRepository, TunnelController by controller {
    private val snapshotMutex = Mutex()
    private val mutableState = MutableStateFlow(AppRuntimeState())
    override val state: StateFlow<AppRuntimeState> = mutableState.asStateFlow()

    init {
        scope.launch { refresh() }
        scope.launch {
            appState.map { it.proxyRunning }.distinctUntilChanged().collect { refresh() }
        }
    }

    override suspend fun connect(request: TunnelConnectRequest): Result<Unit> =
        controller.connect(request).also { refresh() }

    override suspend fun disconnect(): Result<Unit> =
        controller.disconnect().also { refresh() }

    override suspend fun snapshot(): TunnelSnapshot = refresh()

    override suspend fun update(transform: (AppRuntimeState) -> AppRuntimeState) {
        mutableState.value = transform(mutableState.value)
    }

    private suspend fun refresh(): TunnelSnapshot = snapshotMutex.withLock {
        val snapshot = controller.snapshot()
        mutableState.value = mutableState.value.copy(tunnel = snapshot)
        snapshot
    }
}

private suspend fun <T> resultOf(stage: TunnelOperationStage, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    Result.failure(
        if (error is TunnelOperationException) error
        else operationException(error, stage),
    )
}

private fun operationException(error: Throwable, stage: TunnelOperationStage) =
    if (error is TunnelOperationException) error
    else TunnelOperationException(
        TunnelLifecyclePolicy.failure(error, stage, code = "android_runtime", platformCode = "android_vpn"),
    )

/** Reads the sampler cache only; it never starts a second native Core poller. */
private fun sampledTunnelTraffic(): TunnelTraffic = CoreTrafficStatsSampler.samples.value
    ?.snapshot
    ?.inbound
    ?.aggregateCoreInboundTraffic(xrayTrafficExcludedInboundTags())
    ?.toTunnelTraffic()
    ?: TunnelTraffic()
