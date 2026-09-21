// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import platform.TunnelTraffic
import platform.aggregateCoreInboundTraffic
import platform.parseCoreTrafficSnapshot
import platform.toTunnelTraffic

data class DesktopCoreState(
    val isRunning: Boolean,
    val sessionId: Long? = null,
    val coreVersion: String? = null,
    val lastError: String = "",
)

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
    private var native: DesktopCoreNative? = null
    private var loadedLibrary: Path? = null
    private var activeHandle: Long? = null
    private var activeCoreVersion: String? = null
    private var diagnostic: String = ""

    @Synchronized
    fun state(): DesktopCoreState {
        val handle = activeHandle ?: return inactiveState()
        val core = native ?: return resetAfterUnexpectedStop(
            handle = handle,
            error = IllegalStateException("SKIPI Core native library was released while a session was active"),
        )
        val running = runCatching { core.isRunning(handle) }
            .onFailure { error -> diagnostic = nativeDiagnostic("read Core state", error) }
            .getOrDefault(false)
        return if (running) {
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

    @Synchronized
    fun start(configJson: String): Result<DesktopCoreState> = runCatching {
        require(configJson.isNotBlank()) { "SKIPI Core configuration must not be blank" }
        check(activeHandle == null) { "SKIPI Core is already running" }

        val runtime = runtimeProvider().getOrThrow()
        val core = loadNative(runtime)
        check(core.apiVersion == DesktopCoreApiVersion) {
            "Unsupported SKIPI Core desktop ABI ${core.apiVersion}; expected $DesktopCoreApiVersion"
        }
        core.initializeAssets(runtime.directory.toString())
        val handle = core.createController()
        try {
            core.start(handle, configJson)
        } catch (error: Throwable) {
            runCatching { core.destroyController(handle) }
                .onFailure(error::addSuppressed)
            throw error
        }

        activeHandle = handle
        activeCoreVersion = core.coreVersion
        diagnostic = ""
        DesktopLogger.info(
            "CoreController",
            "Started in-process SKIPI Core ${activeCoreVersion.orEmpty()} (session $handle)",
        )
        state()
    }.onFailure { error ->
        diagnostic = nativeDiagnostic("start SKIPI Core", error)
        DesktopLogger.error("CoreController", "Failed to start in-process SKIPI Core", error)
    }

    @Synchronized
    fun stop(): Result<DesktopCoreState> = runCatching {
        val handle = activeHandle ?: return@runCatching inactiveState()
        val core = checkNotNull(native) { "SKIPI Core native library is unavailable" }
        activeHandle = null
        activeCoreVersion = null

        var failure: Throwable? = null
        runCatching { core.stop(handle) }.onFailure { failure = it }
        runCatching { core.destroyController(handle) }.onFailure { error ->
            val previousFailure = failure
            if (previousFailure == null) failure = error else previousFailure.addSuppressed(error)
        }
        failure?.let { throw it }

        diagnostic = ""
        DesktopLogger.info("CoreController", "Stopped in-process SKIPI Core session $handle")
        inactiveState()
    }.onFailure { error ->
        diagnostic = nativeDiagnostic("stop SKIPI Core", error)
        DesktopLogger.error("CoreController", "Failed to stop in-process SKIPI Core", error)
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

    @Synchronized
    fun measureDelay(targetUrl: String): Result<Long> = withActiveCore("measure delay") { core, handle ->
        core.measureDelay(handle, targetUrl)
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
        diagnostic = nativeDiagnostic(operation, error)
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
        }
        return inactiveState()
    }

    private fun inactiveState(): DesktopCoreState = DesktopCoreState(
        isRunning = false,
        lastError = diagnostic,
    )

    private fun nativeDiagnostic(operation: String, error: Throwable): String =
        "$operation: ${error.message ?: error::class.simpleName.orEmpty()}"
}
