// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.proxy.LocalProxyStartRetryPolicy
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
import java.nio.file.Path

/** The exact configuration and optional profile-scoped assets for one Core start attempt. */
data class DesktopCoreStartRequest(
    val configuration: String,
    val assetsDirectory: Path? = null,
    /** The AmneziaWG runner the configuration reaches through a loopback SOCKS outbound, started before Core. */
    val amneziaWg: DesktopAmneziaWgBridge? = null,
    /**
     * Set in TUN mode, where Core runs HEV TUN itself: the descriptor the opened device handed over, or -1 where hev
     * creates the interface (Windows, macOS). Null in proxy mode.
     */
    val tunFd: Int? = null,
)

/** Immutable profile-start snapshot whose rebuild excludes failed dynamic listener ports. */
data class DesktopTunnelStartPlan(
    val configuration: String,
    val dynamicPortEnabled: Boolean,
    val localProxyPort: Int,
    val rebuild: (Set<Int>) -> DesktopTunnelStartPlan,
    val assetsDirectory: Path? = null,
    val amneziaWg: DesktopAmneziaWgBridge? = null,
    /** Rules the configuration does not apply as written; shown on the rules screen while the tunnel runs. */
    val unappliedRules: List<String> = emptyList(),
)

/**
 * Desktop engine adapter for the shared tunnel contract. Both Android and
 * Desktop fulfil it through SKIPI Core; Desktop enters Core through its native
 * in-process ABI.
 */
class DesktopTunnelController(
    private val configForProfile: (String) -> Result<String>,
    private val startCore: (DesktopCoreStartRequest) -> Result<DesktopCoreState>,
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
    /**
     * Runs before anything starts, in both modes: a system proxy SKIPI left from an earlier session
     * points at a port that no longer listens (a dynamic port moves at every start) and would divert or
     * break the new session's traffic. Best effort: a failure is only logged.
     */
    private val reconcileSystemProxy: () -> Result<Unit> = { Result.success(Unit) },
    private val startPlanForProfile: ((String) -> Result<DesktopTunnelStartPlan>)? = null,
    /** Read once per connect: a TUN session replaces the system proxy and is stopped as it started. */
    private val tunMode: () -> Boolean = { false },
    /**
     * Runs after Core started and before the tunnel does, in TUN mode: the tunnel is sent to Core's local SOCKS
     * endpoint, and a wrong port or login leaves it up and silent. A failure stops Core and fails the start.
     */
    private val verifyTunEndpoint: () -> Result<Unit> = { Result.success(Unit) },
    /** Runs before Core starts in TUN mode, so Core never makes a connection that could loop into the tunnel. */
    private val prepareTun: () -> Result<Unit> = { Result.success(Unit) },
    /** Undoes [prepareTun] when Core or the tunnel could not be started. */
    private val releaseTun: () -> Result<Unit> = { Result.success(Unit) },
    /**
     * Runs once after [prepareTun], before Core: brings the device up and returns the descriptor for the session
     * (-1 where hev makes the interface). Core runs HEV TUN in the same session call.
     */
    private val openTunForCore: () -> Result<Int> = { Result.failure(IllegalStateException("TUN is not available")) },
    /** Runs once the session is up, to finish the setup (the routes on Windows; nothing on Linux and macOS). */
    private val attachTun: () -> Result<Unit> = { Result.success(Unit) },
    private val stopTun: () -> Result<Unit> = { Result.success(Unit) },
    private val tunRunning: () -> Boolean = { false },
    private val tunSupported: () -> Boolean = { false },
    /** The rules the running configuration does not apply; empty whenever no session built from a profile is up. */
    private val onUnappliedRulesChanged: (List<String>) -> Unit = {},
) : TunnelController {
    private var latestSnapshot = TunnelSnapshot()
    private var tunActive = false

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
        val useTun = tunMode()
        reconcileSystemProxy().exceptionOrNull()?.let { error ->
            DesktopLogger.warn("Tunnel", "The system proxy could not be reconciled before the start", error)
        }
        var tunPrepared = false
        var coreTunFd: Int? = null
        var startedUnappliedRules: List<String> = emptyList()
        val result = runCatching {
            if (useTun) {
                operationStage = TunnelOperationStage.Start
                prepareTun().getOrThrow()
                tunPrepared = true
                coreTunFd = openTunForCore().getOrThrow()
                operationStage = TunnelOperationStage.PrepareConfiguration
            }
            var startPlan = if (request.configuration == null) {
                startPlanForProfile?.invoke(request.profileId)?.getOrThrow()
            } else {
                null
            }
            var config = request.configuration?.let { configuration ->
                require(configuration.content.isNotBlank()) { "Tunnel configuration must not be blank" }
                configuration.content
            } ?: startPlan?.configuration ?: configForProfile(request.profileId).getOrThrow()
            require(config.isNotBlank()) { "Tunnel configuration must not be blank" }
            var completedAttempts = 0
            while (true) {
                operationStage = TunnelOperationStage.Start
                val startResult = startCore(
                    DesktopCoreStartRequest(
                        configuration = config,
                        assetsDirectory = startPlan?.assetsDirectory,
                        amneziaWg = startPlan?.amneziaWg,
                        tunFd = coreTunFd,
                    ),
                )
                completedAttempts += 1
                if (startResult.isSuccess) break

                val error = checkNotNull(startResult.exceptionOrNull())
                val failedPlan = startPlan
                if (failedPlan == null || !LocalProxyStartRetryPolicy.shouldRetry(
                        dynamicPortEnabled = failedPlan.dynamicPortEnabled,
                        completedAttempts = completedAttempts,
                        failure = error,
                    )
                ) {
                    throw error
                }

                operationStage = TunnelOperationStage.PrepareConfiguration
                startPlan = failedPlan.rebuild(setOf(failedPlan.localProxyPort))
                config = startPlan.configuration
                require(config.isNotBlank()) { "Tunnel configuration must not be blank" }
            }
            startedUnappliedRules = startPlan?.unappliedRules.orEmpty()
            try {
                if (useTun) {
                    operationStage = TunnelOperationStage.Start
                    verifyTunEndpoint().getOrThrow()
                    // Core already runs hev in its session; the app only finishes the interface (Windows routes).
                    attachTun().getOrThrow()
                    tunActive = true
                } else {
                    operationStage = TunnelOperationStage.AcquireSystemProxy
                    awaitSystemProxyEndpoint().getOrThrow()
                    acquireSystemProxy().getOrThrow()
                }
            } catch (error: Throwable) {
                stopCore().exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            Unit
        }
        if (result.isFailure && tunPrepared && !tunActive) releaseTun()
        val operationResult = result.mapFailure(operationStage)
        onUnappliedRulesChanged(if (result.isSuccess) startedUnappliedRules else emptyList())
        latestSnapshot = operationResult.fold(
            onSuccess = { TunnelLifecyclePolicy.connected(latestSnapshot) },
            onFailure = { error -> TunnelLifecyclePolicy.connectFailed(latestSnapshot, failure(error, operationStage)) },
        )
        operationResult
    }

    override suspend fun disconnect(): Result<Unit> = synchronized(this) {
        latestSnapshot = TunnelLifecyclePolicy.beginDisconnect(latestSnapshot)
        // In proxy mode a proxy that cannot be restored keeps Core serving it. A TUN session never took the
        // system proxy, so that cleanup failing must not keep the tunnel and Core alive.
        val proxyRelease = releaseSystemProxy().let { release -> if (tunActive) Result.success(Unit) else release }
        val result = proxyRelease.mapCatching {
            if (tunActive) {
                // Core stops hev first, then Xray; only then the routes and the interface go. A failed Core stop
                // still closes the device, and its error is the one reported.
                val coreFailure = if (coreState().isRunning) stopCore().exceptionOrNull() else null
                val deviceFailure = stopTun().exceptionOrNull()
                coreFailure?.let { failure -> deviceFailure?.let(failure::addSuppressed) }
                (coreFailure ?: deviceFailure)?.let { throw it }
                tunActive = false
            } else if (coreState().isRunning) {
                stopCore().getOrThrow()
            }
            Unit
        }
        if (result.isSuccess) onUnappliedRulesChanged(emptyList())
        latestSnapshot = result.fold(
            onSuccess = { TunnelLifecyclePolicy.disconnected(latestSnapshot) },
            onFailure = { error -> TunnelLifecyclePolicy.disconnectFailed(latestSnapshot, failure(error, TunnelOperationStage.Stop)) },
        )
        result.mapFailure(TunnelOperationStage.Stop)
    }

    override suspend fun snapshot(): TunnelSnapshot = synchronized(this) {
        if (latestSnapshot.phase == TunnelPhase.Connected && !coreState().isRunning) {
            onUnappliedRulesChanged(emptyList())
            val proxyRestoreFailure = releaseSystemProxy().exceptionOrNull()
            // A TUN session whose Core died carries nothing, yet keeps the system routed into it: release it too.
            val tunStopFailure = if (tunActive) stopTun().exceptionOrNull() else null
            tunActive = false
            val stopped = if (proxyRestoreFailure == null) {
                IllegalStateException("SKIPI Core stopped unexpectedly").also { error ->
                    tunStopFailure?.let(error::addSuppressed)
                }
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
        } else if (latestSnapshot.phase == TunnelPhase.Connected && tunActive && !tunRunning()) {
            // hev died under a live Core: the interface is useless, so release it instead of leaving
            // the system routed into a tunnel that carries nothing.
            onUnappliedRulesChanged(emptyList())
            val stopped = IllegalStateException("The TUN tunnel stopped unexpectedly")
            stopTun().exceptionOrNull()?.let(stopped::addSuppressed)
            tunActive = false
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
        if (tunSupported()) add(TunnelCapability.Tun)
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
