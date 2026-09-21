// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelController
import platform.TunnelFailure
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
        if (coreState().isRunning) return@synchronized Result.failure(IllegalStateException("Tunnel is already running"))
        latestSnapshot = TunnelSnapshot(phase = TunnelPhase.Connecting)
        val result = configForProfile(request.profileId).mapCatching { config ->
            startCore(config).getOrThrow()
            try {
                awaitSystemProxyEndpoint().getOrThrow()
                acquireSystemProxy().getOrThrow()
            } catch (error: Throwable) {
                stopCore().exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            Unit
        }
        latestSnapshot = result.fold(
            onSuccess = { TunnelSnapshot(phase = TunnelPhase.Connected) },
            onFailure = ::failedSnapshot,
        )
        result
    }

    override suspend fun disconnect(): Result<Unit> = synchronized(this) {
        latestSnapshot = TunnelSnapshot(phase = TunnelPhase.Disconnecting)
        val result = releaseSystemProxy().mapCatching {
            if (coreState().isRunning) stopCore().getOrThrow()
            Unit
        }
        latestSnapshot = result.fold(
            onSuccess = { TunnelSnapshot() },
            onFailure = ::failedSnapshot,
        )
        result
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
            latestSnapshot = failedSnapshot(stopped)
        } else if (latestSnapshot.phase == TunnelPhase.Connected) {
            // Counter reads are observational: a temporary stats error must not
            // tear down an otherwise healthy tunnel.
            readCoreTraffic().getOrNull()?.let { traffic ->
                latestSnapshot = latestSnapshot.copy(traffic = traffic)
            }
        }
        latestSnapshot
    }

    override fun supports(capability: TunnelCapability): Boolean =
        capability == TunnelCapability.SystemProxy && systemProxySupported()

    private fun failedSnapshot(error: Throwable): TunnelSnapshot = TunnelSnapshot(
        phase = TunnelPhase.Failed,
        failure = TunnelFailure(
            code = "desktop_core",
            message = error.message ?: error::class.simpleName.orEmpty(),
            recoverable = true,
        ),
    )
}
