// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

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

/**
 * Desktop engine adapter for the shared tunnel contract. Both Android and
 * Desktop fulfil it through SKIPI Core; Desktop enters Core through its native
 * in-process ABI.
 */
class DesktopTunnelController(
    private val configForProfile: (String) -> Result<String>,
    private val startCore: (String) -> Result<DesktopCoreState>,
    private val stopCore: () -> Result<DesktopCoreState>,
    private val coreState: () -> DesktopCoreState,
    private val readCoreTraffic: () -> Result<TunnelTraffic> = { Result.success(TunnelTraffic()) },
    /**
     * Verifies the local HTTP endpoint before Windows is allowed to point at it.
     * The desktop app supplies a loopback TCP probe only when system-proxy mode
     * is enabled; tests and non-Windows callers keep the no-op default.
     */
    private val awaitSystemProxyEndpoint: () -> Result<Unit> = { Result.success(Unit) },
    /** Called only after [awaitSystemProxyEndpoint], so Windows never points at a dead endpoint. */
    private val acquireSystemProxy: () -> Result<Unit> = { Result.success(Unit) },
    /** Always called before stopping SKIPI Core, including after a settings change. */
    private val releaseSystemProxy: () -> Result<Unit> = { Result.success(Unit) },
    private val systemProxySupported: () -> Boolean = { false },
) : TunnelController {
    private var latestSnapshot = TunnelSnapshot()

    override suspend fun connect(request: TunnelConnectRequest): Result<Unit> = synchronized(this) {
        when (val decision = TunnelLifecyclePolicy.beginConnect(latestSnapshot, request.profileId)) {
            is TunnelLifecycleDecision.Rejected -> return@synchronized Result.failure(TunnelOperationException(decision.failure))
            is TunnelLifecycleDecision.Accepted -> latestSnapshot = decision.snapshot
        }
        if (coreState().isRunning) {
            val error = TunnelOperationException(
                TunnelFailure("already_running", "Tunnel is already running", false, TunnelOperationStage.Start),
            )
            latestSnapshot = TunnelLifecyclePolicy.connectFailed(latestSnapshot, error.failure)
            return@synchronized Result.failure(error)
        }
        var operationStage = TunnelOperationStage.PrepareConfiguration
        val result = runCatching {
            val config = request.configuration?.let { configuration ->
                require(configuration.content.isNotBlank()) { "Tunnel configuration must not be blank" }
                configuration.content
            } ?: configForProfile(request.profileId).getOrThrow()
            require(config.isNotBlank()) { "Tunnel configuration must not be blank" }
            operationStage = TunnelOperationStage.Start
            startCore(config).getOrThrow()
            try {
                operationStage = TunnelOperationStage.AcquireSystemProxy
                awaitSystemProxyEndpoint().getOrThrow()
                acquireSystemProxy().getOrThrow()
            } catch (error: Throwable) {
                stopCore().exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            Unit
        }
        val operationResult = result.mapFailure(operationStage)
        latestSnapshot = operationResult.fold(
            onSuccess = { TunnelLifecyclePolicy.connected(latestSnapshot) },
            onFailure = { error -> TunnelLifecyclePolicy.connectFailed(latestSnapshot, failure(error, operationStage)) },
        )
        operationResult
    }

    override suspend fun disconnect(): Result<Unit> = synchronized(this) {
        latestSnapshot = TunnelLifecyclePolicy.beginDisconnect(latestSnapshot)
        val result = releaseSystemProxy().mapCatching {
            if (coreState().isRunning) stopCore().getOrThrow()
            Unit
        }
        latestSnapshot = result.fold(
            onSuccess = { TunnelLifecyclePolicy.disconnected(latestSnapshot) },
            onFailure = { error -> TunnelLifecyclePolicy.disconnectFailed(latestSnapshot, failure(error, TunnelOperationStage.Stop)) },
        )
        result.mapFailure(TunnelOperationStage.Stop)
    }

    override suspend fun snapshot(): TunnelSnapshot = synchronized(this) {
        if (latestSnapshot.phase == TunnelPhase.Connected && !coreState().isRunning) {
            val proxyRestoreFailure = releaseSystemProxy().exceptionOrNull()
            val stopped = if (proxyRestoreFailure == null) {
                IllegalStateException("SKIPI Core stopped unexpectedly")
            } else {
                IllegalStateException(
                    "SKIPI Core stopped unexpectedly; system proxy may still point to SKIPI: " +
                        proxyRestoreFailure.message.orEmpty(),
                    proxyRestoreFailure,
                )
            }
            latestSnapshot = TunnelLifecyclePolicy.connectFailed(
                latestSnapshot,
                failure(stopped, TunnelOperationStage.ReadStatus),
            )
        } else if (latestSnapshot.phase == TunnelPhase.Connected) {
            // Counter reads are observational: a temporary stats error must not
            // tear down an otherwise healthy tunnel.
            readCoreTraffic().getOrNull()?.let { traffic ->
                latestSnapshot = latestSnapshot.copy(traffic = traffic)
            }
        }
        latestSnapshot
    }

    override fun capabilities(): Set<TunnelCapability> = buildSet {
        add(TunnelCapability.PreparedConfiguration)
        if (systemProxySupported()) add(TunnelCapability.SystemProxy)
    }

    private fun failure(error: Throwable, stage: TunnelOperationStage): TunnelFailure =
        TunnelLifecyclePolicy.failure(error, stage, code = "desktop_runtime", platformCode = "desktop_core")
}

private fun Result<Unit>.mapFailure(stage: TunnelOperationStage): Result<Unit> = fold(
    onSuccess = { Result.success(Unit) },
    onFailure = { error ->
        Result.failure(
            if (error is TunnelOperationException) error
            else TunnelOperationException(
                TunnelLifecyclePolicy.failure(error, stage, code = "desktop_runtime", platformCode = "desktop_core"),
            ),
        )
    },
)
