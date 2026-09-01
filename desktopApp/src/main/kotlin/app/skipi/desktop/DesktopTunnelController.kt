// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelController
import platform.TunnelFailure
import platform.TunnelPhase
import platform.TunnelSnapshot

/**
 * Desktop engine adapter for the shared tunnel contract.
 * Android fulfils this contract through SKIPI Core; Windows fulfils it through its bundled Xray.
 */
class DesktopTunnelController(
    private val configForProfile: (String) -> Result<String>,
    private val startProcess: (String) -> Result<DesktopXrayProcessState>,
    private val stopProcess: () -> Result<DesktopXrayProcessState>,
    private val processState: () -> DesktopXrayProcessState,
    /**
     * Verifies the local HTTP endpoint before Windows is allowed to point at it.
     * The desktop app supplies a loopback TCP probe only when system-proxy mode
     * is enabled; tests and non-Windows callers keep the no-op default.
     */
    private val awaitSystemProxyEndpoint: () -> Result<Unit> = { Result.success(Unit) },
    /** Called only after [awaitSystemProxyEndpoint], so Windows never points at a dead endpoint. */
    private val acquireSystemProxy: () -> Result<Unit> = { Result.success(Unit) },
    /** Always called before stopping Xray, including after a settings change. */
    private val releaseSystemProxy: () -> Result<Unit> = { Result.success(Unit) },
    private val systemProxySupported: () -> Boolean = { false },
) : TunnelController {
    private var latestSnapshot = TunnelSnapshot()

    override suspend fun connect(request: TunnelConnectRequest): Result<Unit> = synchronized(this) {
        if (processState().isRunning) return@synchronized Result.failure(IllegalStateException("Tunnel is already running"))
        latestSnapshot = TunnelSnapshot(phase = TunnelPhase.Connecting)
        val result = configForProfile(request.profileId).mapCatching { config ->
            startProcess(config).getOrThrow()
            try {
                awaitSystemProxyEndpoint().getOrThrow()
                acquireSystemProxy().getOrThrow()
            } catch (error: Throwable) {
                stopProcess().exceptionOrNull()?.let(error::addSuppressed)
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
            if (processState().isRunning) stopProcess().getOrThrow()
            Unit
        }
        latestSnapshot = result.fold(
            onSuccess = { TunnelSnapshot() },
            onFailure = ::failedSnapshot,
        )
        result
    }

    override suspend fun snapshot(): TunnelSnapshot = synchronized(this) {
        if (latestSnapshot.phase == TunnelPhase.Connected && !processState().isRunning) {
            val proxyRestoreFailure = releaseSystemProxy().exceptionOrNull()
            val stopped = if (proxyRestoreFailure == null) {
                IllegalStateException("Xray process stopped unexpectedly")
            } else {
                IllegalStateException(
                    "Xray process stopped unexpectedly; Windows system proxy may still point to SKIPI: " +
                        proxyRestoreFailure.message.orEmpty(),
                    proxyRestoreFailure,
                )
            }
            latestSnapshot = failedSnapshot(stopped)
        }
        latestSnapshot
    }

    override fun supports(capability: TunnelCapability): Boolean =
        capability == TunnelCapability.SystemProxy && systemProxySupported()

    private fun failedSnapshot(error: Throwable): TunnelSnapshot = TunnelSnapshot(
        phase = TunnelPhase.Failed,
        failure = TunnelFailure(
            code = "desktop_xray",
            message = error.message ?: error::class.simpleName.orEmpty(),
            recoverable = true,
        ),
    )
}
