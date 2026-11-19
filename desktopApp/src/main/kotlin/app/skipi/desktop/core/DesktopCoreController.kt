// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.concurrent.withLock
import java.nio.file.Path
import platform.TunnelTraffic
import platform.aggregateCoreInboundTraffic
import platform.parseCoreTrafficSnapshot
import platform.toTunnelTraffic
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class DesktopCoreState(
    val isRunning: Boolean,
    val sessionId: Long? = null,
    val coreVersion: String? = null,
    val lastError: String = "",
)

/** What the AmneziaWG runner needs before Core starts: its runtime config and loopback SOCKS port. */
data class DesktopAmneziaWgBridge(
    val configJson: String,
    val socksPort: Int,
)

/**
 * HEV TUN as Core runs it inside a session. [tunFd] is the descriptor Core's TUN device opened (Linux, in process) or
 * -1 where hev creates the interface itself; [executable] is the shipped hev program Core supervises (Windows).
 */
data class DesktopCoreHevPlan(
    val configYaml: String,
    val tunFd: Int,
    val executable: String? = null,
)

/** Deadline for each step of a session stop (HEV TUN, Xray, AmneziaWG). */
internal const val DesktopCoreSessionStopTimeoutMillis = 5_000L

private fun sessionPlanJson(
    configJson: String,
    amneziaWg: DesktopAmneziaWgBridge?,
    connectionLog: Boolean,
    hev: DesktopCoreHevPlan,
): String = buildJsonObject {
    put("xrayConfig", configJson)
    if (amneziaWg != null) {
        put(
            "amneziaWg",
            buildJsonObject {
                put("config", amneziaWg.configJson)
                put("socksPort", amneziaWg.socksPort)
            },
        )
    }
    put("connectionLog", connectionLog)
    put(
        "hev",
        buildJsonObject {
            put("configYaml", hev.configYaml)
            put("tunFd", hev.tunFd)
            hev.executable?.let { put("executable", it) }
        },
    )
}.toString()

/**
 * Owns the one SKIPI Core session running inside the Desktop JVM process.
 *
 * The native Core has process-global Xray state, so this controller never
 * creates a second session and always destroys an old handle after stopping it.
 */
internal class DesktopCoreController(
    private val runtimeProvider: () -> Result<DesktopCoreRuntime> = DesktopCoreRuntimes::discover,
    private val nativeLoader: (Path) -> DesktopCoreNative = ::DesktopCoreFfmNative,
) : AutoCloseable {
    // Lifecycle calls (start/stop/close) hold the controller monitor for as long as the native call takes.
    // [stateLock] guards only the fields below, so the UI thread can poll [state] without waiting for them.
    private val stateLock = Any()

    @Volatile private var native: DesktopCoreNative? = null
    private var loadedLibrary: Path? = null
    @Volatile private var activeHandle: Long? = null
    @Volatile private var connectionLogEnabled = false
    @Volatile private var activeCoreVersion: String? = null
    @Volatile private var diagnostic: String = ""
    @Volatile private var amneziaWgActive = false

    // A session started with HEV TUN is stopped with the bounded call and is read through Core's session state.
    @Volatile private var sessionMode = false

    // The handle of a start that has not returned yet, so the emergency stop can reach a start that hangs.
    @Volatile private var startingHandle: Long? = null

    // The AmneziaWG runner is one per process: the tunnel and a latency probe take turns under this lock.
    // Probes only try it, so a probe in flight never makes Stop or Start wait on a hung native call.
    private val amneziaWgLock = java.util.concurrent.locks.ReentrantLock()

    fun state(): DesktopCoreState = synchronized(stateLock) {
        val handle = activeHandle ?: return@synchronized inactiveState()
        val core = native ?: return@synchronized resetAfterUnexpectedStop(
            handle = handle,
            error = IllegalStateException("SKIPI Core native library was released while a session was active"),
        )
        val running = runCatching { core.isRunning(handle) }
            .onFailure { error -> diagnostic = nativeDiagnostic("read Core state", error) }
            .getOrDefault(false)
        if (running) {
            DesktopCoreState(
                isRunning = true,
                sessionId = handle,
                coreVersion = activeCoreVersion,
                lastError = diagnostic,
            )
        } else {
            resetAfterUnexpectedStop(handle, null)
        }
    }

    /**
     * Loads the native library ahead of the first [start], so the 36 MB load is not part of the first
     * Connect. A failure is only reported: [start] tries again and reports its own error.
     */
    @Synchronized
    fun preload(): Result<Unit> = runCatching {
        if (activeHandle == null) check(loadNative(runtimeProvider().getOrThrow()).apiVersion.isNotBlank())
    }

    @Synchronized
    fun start(
        configJson: String,
        assetsDirectory: Path? = null,
        amneziaWg: DesktopAmneziaWgBridge? = null,
        /** With it Core starts HEV TUN too, from one session call; without it the separate AmneziaWG and start calls run. */
        hev: DesktopCoreHevPlan? = null,
    ): Result<DesktopCoreState> = runCatching {
        require(configJson.isNotBlank()) { "SKIPI Core configuration must not be blank" }
        check(activeHandle == null) { "SKIPI Core is already running" }

        val runtime = runtimeProvider().getOrThrow()
        val core = loadNative(runtime)
        check(core.apiVersion == DesktopCoreApiVersion) {
            "Unsupported SKIPI Core desktop ABI ${core.apiVersion}; expected $DesktopCoreApiVersion"
        }
        core.initializeAssets(assetsDirectory?.toString() ?: runtime.directory.toString())
        val handle = if (hev != null) {
            startSession(core, configJson, amneziaWg, hev)
        } else {
            startSeparately(core, configJson, amneziaWg)
        }

        synchronized(stateLock) {
            activeHandle = handle
            sessionMode = hev != null
            activeCoreVersion = core.coreVersion
            diagnostic = ""
        }
        DesktopLogger.info(
            "CoreController",
            "Started in-process SKIPI Core ${activeCoreVersion.orEmpty()} (session $handle)",
        )
        state()
    }.onFailure { error ->
        synchronized(stateLock) { diagnostic = nativeDiagnostic("start SKIPI Core", error) }
        DesktopLogger.error("CoreController", "Failed to start in-process SKIPI Core", error)
    }

    /** The older start: the AmneziaWG runner first, then Core, each its own native call. */
    private fun startSeparately(core: DesktopCoreNative, configJson: String, amneziaWg: DesktopAmneziaWgBridge?): Long {
        // The runner has to listen before Xray starts, and must not outlive a failed start.
        if (amneziaWg != null) {
            amneziaWgLock.withLock {
                core.startAmneziaWg(amneziaWg.configJson, amneziaWg.socksPort)
                amneziaWgActive = true
            }
        }
        val handle = try {
            core.createController()
        } catch (error: Throwable) {
            releaseAmneziaWg(core)?.let(error::addSuppressed)
            throw error
        }
        // An older library has no connection log; that must never stop the tunnel.
        runCatching { core.setConnectionLogEnabled(handle, connectionLogEnabled) }
        try {
            core.start(handle, configJson)
        } catch (error: Throwable) {
            runCatching { core.destroyController(handle) }
                .onFailure(error::addSuppressed)
            releaseAmneziaWg(core)?.let(error::addSuppressed)
            throw error
        }
        return handle
    }

    /** One session call: Core starts AmneziaWG, Xray and HEV TUN itself and undoes them in reverse on a failure. */
    private fun startSession(
        core: DesktopCoreNative,
        configJson: String,
        amneziaWg: DesktopAmneziaWgBridge?,
        hev: DesktopCoreHevPlan,
    ): Long {
        val plan = sessionPlanJson(configJson, amneziaWg, connectionLogEnabled, hev)
        val handle = core.createController()
        startingHandle = handle
        // Core owns the runner in a session; the flag keeps latency probes away from it until the session ends.
        val locked = amneziaWg != null && amneziaWgLock.tryLock()
        try {
            check(amneziaWg == null || locked) { "The AmneziaWG runner is busy with another measurement" }
            if (amneziaWg != null) amneziaWgActive = true
            core.startSession(handle, plan, 0)
            // A force stop that came in while the start was hanging already released it; the late result is void.
            check(startingHandle == handle) { "The start was cancelled by an emergency stop" }
        } catch (error: Throwable) {
            if (amneziaWg != null && locked) amneziaWgActive = false
            // After an emergency stop the handle is already destroyed; a second destroy only adds a suppressed note.
            if (startingHandle == handle) runCatching { core.destroyController(handle) }.onFailure(error::addSuppressed)
            throw error
        } finally {
            startingHandle = null
            if (locked) amneziaWgLock.unlock()
        }
        return handle
    }

    @Synchronized
    fun stop(): Result<DesktopCoreState> = runCatching {
        // The session is over for the caller whatever the native side does next. The handle is taken under the state
        // lock in one step: state() polled from the UI may find the session dead at the same moment and release it,
        // and only one of the two may destroy it.
        val handle = synchronized(stateLock) {
            activeHandle?.also {
                activeHandle = null
                activeCoreVersion = null
            }
        } ?: return@runCatching inactiveState()
        val core = checkNotNull(native) { "SKIPI Core native library is unavailable" }
        val inSession = sessionMode
        sessionMode = false

        var failure: Throwable? = null
        runCatching {
            if (inSession) core.stopWithTimeout(handle, DesktopCoreSessionStopTimeoutMillis) else core.stop(handle)
        }.onFailure { failure = it }
        runCatching { core.destroyController(handle) }.onFailure { error ->
            val previousFailure = failure
            if (previousFailure == null) failure = error else previousFailure.addSuppressed(error)
        }
        releaseAmneziaWg(core)?.let { error ->
            val previousFailure = failure
            if (previousFailure == null) failure = error else previousFailure.addSuppressed(error)
        }
        failure?.let { throw it }

        synchronized(stateLock) { diagnostic = "" }
        DesktopLogger.info("CoreController", "Stopped in-process SKIPI Core session $handle")
        inactiveState()
    }.onFailure { error ->
        synchronized(stateLock) { diagnostic = nativeDiagnostic("stop SKIPI Core", error) }
        DesktopLogger.error("CoreController", "Failed to stop in-process SKIPI Core", error)
    }

    /** Whether Core's session is running (HEV TUN included); false when none is, or its state cannot be read. */
    fun sessionRunning(): Boolean {
        val handle = activeHandle ?: return false
        val core = native ?: return false
        return runCatching { core.sessionState(handle) == SessionRunningState }.getOrDefault(false)
    }

    /**
     * The emergency stop: never waits on a start or stop in flight (it takes no controller lock) and ends the session
     * whatever state it is in. Whatever Core had to give up on shows in [abandonedInstances].
     */
    fun forceStop(): Result<DesktopCoreState> = runCatching {
        val handle = synchronized(stateLock) {
            val taken = activeHandle ?: startingHandle
            activeHandle = null
            startingHandle = null
            activeCoreVersion = null
            sessionMode = false
            taken
        } ?: return@runCatching inactiveState()
        val core = checkNotNull(native) { "SKIPI Core native library is unavailable" }
        var failure: Throwable? = null
        runCatching { core.forceStop(handle) }.onFailure { failure = it }
        runCatching { core.destroyController(handle) }.onFailure { error ->
            val previous = failure
            if (previous == null) failure = error else previous.addSuppressed(error)
        }
        releaseAmneziaWg(core)?.let { error ->
            val previous = failure
            if (previous == null) failure = error else previous.addSuppressed(error)
        }
        failure?.let { throw it }
        synchronized(stateLock) { diagnostic = "" }
        DesktopLogger.info("CoreController", "Force stopped SKIPI Core session $handle")
        inactiveState()
    }.onFailure { error ->
        synchronized(stateLock) { diagnostic = nativeDiagnostic("force stop SKIPI Core", error) }
        DesktopLogger.error("CoreController", "Failed to force stop SKIPI Core", error)
    }

    /** How many Core instances were given up on and are still closing; non-zero means the app should restart. */
    fun abandonedInstances(): Result<Long> = runCatching {
        // A process-wide count, so it needs no session; it loads the library like a probe does and takes no lock.
        synchronized(this) { loadNative(runtimeProvider().getOrThrow()) }.abandonedInstances()
    }

    /** HEV TUN's counters of the running session. */
    @Synchronized
    fun queryTunnelStats(): Result<String> = withActiveCore("query tunnel statistics") { core, handle ->
        core.queryTunnelStats(handle)
    }

    /** The loaded Core library for TUN calls, which work before any session exists. */
    @Synchronized
    fun nativeForTun(): Result<DesktopCoreFfmNative> = runCatching {
        val core = loadNative(runtimeProvider().getOrThrow())
        core as? DesktopCoreFfmNative ?: error("This SKIPI Core library has no TUN support")
    }

    @Synchronized
    fun queryTrafficStats(): Result<String> = withActiveCore("query traffic statistics") { core, handle ->
        core.queryTrafficStats(handle)
    }

    /** Reads Core's raw counters through the same platform-neutral tunnel model used by Android. */
    @Synchronized
    fun readTunnelTraffic(): Result<TunnelTraffic> = withActiveCore("query tunnel traffic") { core, handle ->
        val snapshot = checkNotNull(parseCoreTrafficSnapshot(core.queryTrafficStats(handle))) {
            "SKIPI Core returned malformed traffic statistics"
        }
        snapshot.inbound.aggregateCoreInboundTraffic().toTunnelTraffic()
    }

    /** Remembered for the next session and applied at once to a running one. Never waits for a running probe. */
    fun setConnectionLogEnabled(enabled: Boolean) {
        connectionLogEnabled = enabled
        val handle = activeHandle ?: return
        runCatching { native?.setConnectionLogEnabled(handle, enabled) }
    }

    /**
     * Polled for the Home log, so a failure stays out of the diagnostic shown to the user. Not synchronized:
     * a delay probe holds the monitor for seconds, and the log must keep moving meanwhile.
     */
    fun readConnectionLog(afterSeq: Long): Result<String> = runCatching {
        val handle = checkNotNull(activeHandle) { "SKIPI Core is not running" }
        checkNotNull(native) { "SKIPI Core native library is unavailable" }.readConnectionLog(handle, afterSeq)
    }

    @Synchronized
    fun measureDelay(targetUrl: String): Result<Long> = withActiveCore("measure delay") { core, handle ->
        core.measureDelay(handle, targetUrl)
    }

    /**
     * Measures one outbound in an isolated Core instance, with no session needed. A probe of an
     * AmneziaWG server borrows the runner for the measurement only, and is refused while the
     * tunnel owns it. The measurement itself holds no controller lock, so Stop stays responsive.
     */
    fun measureOutboundDelay(
        configJson: String,
        targetUrl: String,
        amneziaWg: DesktopAmneziaWgBridge? = null,
    ): Result<Long> = runCatching {
        require(configJson.isNotBlank()) { "SKIPI Core probe configuration must not be blank" }
        val core = synchronized(this) { loadNative(runtimeProvider().getOrThrow()) }
        if (amneziaWg == null) return@runCatching core.measureOutboundDelay(configJson, targetUrl)
        check(amneziaWgLock.tryLock()) { "The AmneziaWG runner is busy with another measurement or a tunnel start" }
        try {
            check(!amneziaWgActive) { "AmneziaWG is in use by the running tunnel" }
            core.startAmneziaWg(amneziaWg.configJson, amneziaWg.socksPort)
            try {
                core.measureOutboundDelay(configJson, targetUrl)
            } finally {
                runCatching { core.stopAmneziaWg() }
            }
        } finally {
            amneziaWgLock.unlock()
        }
    }

    /**
     * Measures one TCP connect inside Core, so it follows Core's own sockets around the tunnel. Like
     * [measureOutboundDelay] it holds no controller lock and works whether or not the tunnel runs.
     */
    fun measureTcpDelay(host: String, port: Int, timeoutMillis: Int): Result<Long> = runCatching {
        val core = synchronized(this) { loadNative(runtimeProvider().getOrThrow()) }
        core.measureTcpDelay(host, port, timeoutMillis)
    }

    @Synchronized
    fun readMemoryStats(): Result<String> = runCatching {
        checkNotNull(native) { "SKIPI Core has not been loaded" }.readMemoryStats()
    }

    @Synchronized
    fun forceFreeMemory(): Result<Unit> = runCatching {
        checkNotNull(native) { "SKIPI Core has not been loaded" }.forceFreeMemory()
    }

    @Synchronized
    override fun close() {
        val stopFailure = stop().exceptionOrNull()
        val loaded = native
        native = null
        loadedLibrary = null
        if (loaded != null) {
            runCatching { loaded.close() }
                .onFailure { error ->
                    if (stopFailure != null) stopFailure.addSuppressed(error) else throw error
                }
        }
        stopFailure?.let { throw it }
    }

    private fun loadNative(runtime: DesktopCoreRuntime): DesktopCoreNative {
        val normalizedLibrary = runtime.library.toAbsolutePath().normalize()
        val loaded = native
        if (loaded != null && loadedLibrary == normalizedLibrary) return loaded
        check(activeHandle == null) { "Cannot replace SKIPI Core library while a session is active" }
        loaded?.close()
        return nativeLoader(normalizedLibrary).also { loadedNative ->
            native = loadedNative
            loadedLibrary = normalizedLibrary
        }
    }

    private fun <T> withActiveCore(
        operation: String,
        action: (DesktopCoreNative, Long) -> T,
    ): Result<T> = runCatching {
        val handle = checkNotNull(activeHandle) { "SKIPI Core is not running" }
        val core = checkNotNull(native) { "SKIPI Core native library is unavailable" }
        action(core, handle)
    }.onFailure { error ->
        synchronized(stateLock) { diagnostic = nativeDiagnostic(operation, error) }
    }

    private fun resetAfterUnexpectedStop(handle: Long, error: Throwable?): DesktopCoreState {
        activeHandle = null
        activeCoreVersion = null
        if (error != null) diagnostic = nativeDiagnostic("read Core state", error)
        native?.let { core ->
            runCatching { core.destroyController(handle) }
                .onFailure { destroyError ->
                    val detail = nativeDiagnostic("release stopped Core session", destroyError)
                    diagnostic = listOf(diagnostic, detail).filter(String::isNotBlank).joinToString("; ")
                }
            releaseAmneziaWg(core)?.let { releaseError ->
                val detail = nativeDiagnostic("stop AmneziaWG", releaseError)
                diagnostic = listOf(diagnostic, detail).filter(String::isNotBlank).joinToString("; ")
            }
        }
        return inactiveState()
    }

    /** Stops the AmneziaWG runner if this controller started it; returns the failure, if any. */
    private fun releaseAmneziaWg(core: DesktopCoreNative): Throwable? {
        // Only a tunnel sets the flag, and a probe never holds the lock while it is set, so this cannot wait on one.
        if (!amneziaWgActive) return null
        return amneziaWgLock.withLock {
            if (!amneziaWgActive) return@withLock null
            amneziaWgActive = false
            runCatching { core.stopAmneziaWg() }.exceptionOrNull()
        }
    }

    private companion object {
        const val SessionRunningState = 2
    }

    private fun inactiveState(): DesktopCoreState = DesktopCoreState(
        isRunning = false,
        lastError = diagnostic,
    )

    private fun nativeDiagnostic(operation: String, error: Throwable): String =
        "$operation: ${error.message ?: error::class.simpleName.orEmpty()}"
}
