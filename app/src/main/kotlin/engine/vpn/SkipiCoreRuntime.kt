// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import app.R
import features.logs.AppLog
import engine.xray.initializeAndroidXrayCoreEnvironment
import app.skipi.core.skipicore.CoreCallbackHandler
import app.skipi.core.skipicore.CoreController
import app.skipi.core.skipicore.Skipicore
import app.skipi.core.skipicore.SocketProtector
import app.skipi.core.skipicore.StateListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreTrafficSnapshot
import platform.parseCoreTrafficSnapshot

// Used by the standalone AmneziaWG latency probe; the VPN start path waits inside Core.
internal const val NativeBridgeReadinessTimeoutMillis = 8_000L
private const val NativeBridgeReadinessPollIntervalMillis = 200L
private const val NativeBridgeSocksConnectTimeoutMillis = 300

internal object SkipiCoreRuntime {
    @Volatile
    private var coreController: CoreController? = null
    @Volatile
    var activeAmneziaWgBridge: ActiveAmneziaWgBridge? = null
        private set
    @Volatile
    private var amneziaWgSocketProtectorConfigured = false

    suspend fun start(
        context: Context,
        config: VpnServiceStartConfig,
        tunFd: Int,
    ) {
        require(config.dataDir.isNotBlank()) {
            context.getString(R.string.error_skipi_core_data_dir_missing)
        }
        context.initializeAndroidXrayCoreEnvironment(config.dataDir)
        val plan = buildCoreSessionPlanJson(
            xrayConfigJson = config.xrayConfigJson,
            amneziaWgConfigJson = config.amneziaWgConfigJson,
            amneziaWgSocksPort = config.amneziaWgSocksPort,
            connectionLog = config.enableAccessLog,
        )
        val hasAmneziaWg = !config.amneziaWgConfigJson.isNullOrBlank() && config.amneziaWgSocksPort > 0
        val controller = Skipicore.newCoreController(SkipiCoreCallbackHandler())
        runCatching {
            check(!hasAmneziaWg || amneziaWgSocketProtectorConfigured) {
                "The AmneziaWG socket protector is not configured; refusing to create a VPN routing loop"
            }
            controller.setStateListener(SkipiCoreStateListener())
            // Core starts AmneziaWG, waits for its SOCKS listener, then Xray, and rolls back on failure.
            controller.startSession(plan, tunFd.toLong())
        }.onFailure { error ->
            activeAmneziaWgBridge = null
            runCatching { controller.stopLoopWithTimeout(CoreStopTimeoutMillis) }
                .onFailure { stopError ->
                    AppLog.warn(LogTag, "Failed to stop SKIPI Core after start failure", stopError)
                }
            if (error is CancellationException) throw error
            throw IllegalStateException(
                context.getString(R.string.error_skipi_core_start_failed, error.readableMessage()),
                error,
            )
        }
        coreController = controller
        activeAmneziaWgBridge = config.activeAmneziaWgBridge
    }

    /** Stops the session; Core stops AmneziaWG too and detaches a hung close after [CoreStopTimeoutMillis]. */
    fun stop() {
        activeAmneziaWgBridge = null
        val controller = coreController ?: return
        runCatching {
            controller.stopLoopWithTimeout(CoreStopTimeoutMillis)
        }.onFailure { error ->
            AppLog.error(LogTag, "Failed to stop SKIPI Core", error)
        }
        coreController = null
    }

    /** Emergency core step: ForceStop works in every state, including a hung start or stop. */
    fun forceStopCore() {
        activeAmneziaWgBridge = null
        val controller = coreController ?: return
        runCatching { controller.forceStop() }
            .onFailure { error -> AppLog.error(LogTag, "Failed to force-stop SKIPI Core", error) }
        coreController = null
    }

    /** Why leaked Core instances make the runtime dirty, or null when none leaked. */
    fun abandonedInstancesDirtyReason(): String? =
        runCatching { coreAbandonedDirtyReason(Skipicore.abandonedInstances()) }.getOrNull()

    /**
     * Emergency path, right before the process is killed: ForceStop the
     * controller, bounded to about a second on its own daemon thread so a stuck
     * native call cannot hold up the kill.
     */
    fun forceStopAbandonedCoreInstances() {
        val worker = Thread({ forceStopCore() }, "skipi-core-force-stop").apply { isDaemon = true }
        worker.start()
        try {
            worker.join(EmergencyStopStepDeadlineMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (worker.isAlive) {
            AppLog.warn(LogTag, "Core force stop did not finish within ${EmergencyStopStepDeadlineMillis}ms")
        }
    }

    /** Connections routed since [afterSeq], as the JSON Core returns; a failure is a failed read. */
    fun readConnectionLog(afterSeq: Long): Result<String> = runCatching {
        checkNotNull(coreController) { "SKIPI Core is not running" }.readConnectionLog(afterSeq)
    }

    fun setSocketProtector(protector: (Int) -> Boolean) {
        runCatching {
            Skipicore.setAmneziaWgSocketProtector(SocketProtector { fd -> protector(fd.toInt()) })
            amneziaWgSocketProtectorConfigured = true
            AppLog.info(LogTag, "Configured the AmneziaWG socket protector")
        }.onFailure { error ->
            amneziaWgSocketProtectorConfigured = false
            AppLog.warn(LogTag, "Failed to set socket protector on Skipicore", error)
        }
    }

    /** Replaces the service-bound protector with a no-op so the native singleton no longer references the service. */
    fun clearSocketProtector() {
        runCatching { Skipicore.setAmneziaWgSocketProtector(SocketProtector { true }) }
        amneziaWgSocketProtectorConfigured = false
    }

    /** Starts the native AWG runner and its private local SOCKS listener (standalone use, e.g. latency probes). */
    fun startAmneziaWg(
        configJson: String,
        socksPort: Int,
        requireSocketProtector: Boolean = true,
    ) {
        check(!requireSocketProtector || amneziaWgSocketProtectorConfigured) {
            "The AmneziaWG socket protector is not configured; refusing to create a VPN routing loop"
        }
        if (!requireSocketProtector) {
            Skipicore.setAmneziaWgSocketProtector(SocketProtector { true })
        }
        try {
            Skipicore.startAmneziaWg(configJson, socksPort.toLong())
            AppLog.info(LogTag, "AmneziaWG started on socks port $socksPort")
        } catch (error: Throwable) {
            AppLog.error(LogTag, "Failed to start AmneziaWG", error)
            throw error
        }
    }

    fun stopAmneziaWg() {
        runCatching {
            Skipicore.stopAmneziaWg()
            AppLog.info(LogTag, "AmneziaWG stopped")
        }.onFailure { error ->
            AppLog.warn(LogTag, "Failed to stop AmneziaWG", error)
        }
    }

    internal suspend fun awaitAmneziaWgReady(
        port: Int,
        timeoutMs: Long = NativeBridgeReadinessTimeoutMillis,
    ): Boolean = awaitLocalSocksReady("AmneziaWG", port, timeoutMs)

    private suspend fun awaitLocalSocksReady(
        runtimeName: String,
        port: Int,
        timeoutMs: Long,
    ): Boolean {
        AppLog.info(LogTag, "Waiting for $runtimeName local SOCKS 127.0.0.1:$port to accept connections (timeout ${timeoutMs}ms)")
        val ready = awaitNativeBridgeReadiness(timeoutMs) {
            withContext(Dispatchers.IO) {
                try {
                    java.net.Socket().use { socket ->
                        socket.connect(
                            java.net.InetSocketAddress("127.0.0.1", port),
                            NativeBridgeSocksConnectTimeoutMillis,
                        )
                    }
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            }
        }
        if (ready) {
            AppLog.info(LogTag, "$runtimeName local SOCKS 127.0.0.1:$port is ready")
        } else {
            AppLog.warn(LogTag, "$runtimeName local SOCKS 127.0.0.1:$port did not become ready within ${timeoutMs}ms")
        }
        return ready
    }

    fun isRunning(): Boolean {
        return coreController?.isRunning == true
    }

    fun readMemoryStats(): String {
        return runCatching { Skipicore.readMemoryStats() }.getOrNull().orEmpty()
    }

    /** Returns Xray traffic counters without creating an Xray API socket. */
    fun queryTrafficStats(): CoreTrafficSnapshot? {
        val controller = coreController ?: return null
        return parseCoreTrafficSnapshot(controller.queryTrafficStats())
    }

    fun forceFreeMemory() {
        runCatching { Skipicore.forceFreeMemory() }
    }

    fun fastSelectBestOutbound(
        configJson: String,
        candidateTags: String,
        probeUrl: String = "",
        timeoutMs: Long = 1200L,
    ): String {
        return runCatching {
            Skipicore.fastSelectBestOutbound(configJson, candidateTags, probeUrl, timeoutMs)
        }.getOrNull().orEmpty()
    }

    private const val LogTag = "SkipiCore"
}

/**
 * Waits for a native bridge's local SOCKS listener with one bounded, cancellable deadline
 * (standalone AmneziaWG latency probe; the VPN session start waits inside Core).
 */
internal suspend fun awaitNativeBridgeReadiness(
    timeoutMs: Long,
    pollIntervalMs: Long = NativeBridgeReadinessPollIntervalMillis,
    isReady: suspend () -> Boolean,
): Boolean {
    require(timeoutMs > 0) { "timeoutMs must be positive" }
    require(pollIntervalMs > 0) { "pollIntervalMs must be positive" }
    return withTimeoutOrNull<Boolean>(timeoutMs) {
        while (!isReady()) {
            delay(pollIntervalMs)
        }
        true
    } ?: false
}

private class SkipiCoreCallbackHandler : CoreCallbackHandler {
    override fun startup(): Long {
        AppLog.info("SkipiCore", "SKIPI Core started")
        return 0
    }

    override fun shutdown(): Long {
        AppLog.info("SkipiCore", "SKIPI Core stopped")
        return 0
    }

    override fun onEmitStatus(code: Long, message: String?): Long {
        val text = message.orEmpty().ifBlank { "status code: $code" }
        AppLog.info("SkipiCore", text)
        return 0
    }
}

private class SkipiCoreStateListener : StateListener {
    override fun onStateChanged(state: Long, detail: String?) {
        AppLog.info("SkipiCore", "Session state $state ${detail.orEmpty()}".trim())
    }
}

private fun Throwable.readableMessage(): String {
    return message ?: javaClass.simpleName.orEmpty()
}

/** How often the Access logs page asks Core for new connections. */
internal const val ConnectionAccessLogPollMillis = 1_000L

internal fun formatConnectionLogTime(timeMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(timeMillis))
