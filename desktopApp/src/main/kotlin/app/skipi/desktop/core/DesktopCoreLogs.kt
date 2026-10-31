// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.logs.CoreLogFile
import features.logs.CoreLogFileTailer
import features.logs.XrayFileLogRepository
import java.nio.file.Files
import java.nio.file.Path

/** Where SKIPI Core writes its error and access logs, mirroring Android's `xray/logs` folder. */
internal data class DesktopCoreLogPaths(private val root: Path) {
    private val directory: Path get() = root.resolve("xray").resolve("logs")
    val errorLogPath: String get() = directory.resolve("error.log").toString()
    val accessLogPath: String get() = directory.resolve("access.log").toString()

    companion object {
        fun default(): DesktopCoreLogPaths = DesktopCoreLogPaths(
            DesktopLogger.logDirectory().parent ?: DesktopLogger.logDirectory(),
        )
    }
}

/**
 * Desktop host of the shared core/access log viewers: restores the previous session, tails the
 * files while Core runs (access log only while it is enabled), applies retention and clears.
 */
internal class DesktopCoreLogs(
    private val paths: DesktopCoreLogPaths = DesktopCoreLogPaths.default(),
) {
    val core = XrayFileLogRepository("DesktopCoreLogRepository") { message, error ->
        DesktopLogger.warn("DesktopCoreLogRepository", message, error)
    }
    val access = XrayFileLogRepository("DesktopAccessLogRepository") { message, error ->
        DesktopLogger.warn("DesktopAccessLogRepository", message, error)
    }
    private var tailers: List<CoreLogFileTailer> = emptyList()

    private val errorFile get() = CoreLogFile(path = paths.errorLogPath, defaultLevel = "error")
    private val accessFile get() = CoreLogFile(path = paths.accessLogPath, defaultLevel = "info")

    /** Restores the previous session's lines and drops the ones past the retention period. */
    fun restore(retentionDays: Int) {
        core.initialize(errorFile, retentionDays)
        access.initialize(accessFile, retentionDays)
    }

    @Synchronized
    fun startTailers(settings: DesktopAppSettings) {
        stopTailers()
        tailers = buildList {
            add(
                CoreLogFileTailer(
                    logFiles = listOf(errorFile),
                    repository = core,
                    startAtEnd = false,
                    onFailure = { message, error -> DesktopLogger.warn("CoreLogFileTailer", message, error) },
                ),
            )
            if (settings.enableAccessLog) {
                add(
                    CoreLogFileTailer(
                        logFiles = listOf(accessFile),
                        repository = access,
                        startAtEnd = false,
                        onFailure = { message, error -> DesktopLogger.warn("CoreLogFileTailer", message, error) },
                    ),
                )
            }
        }.onEach(CoreLogFileTailer::start)
    }

    @Synchronized
    fun stopTailers() {
        tailers.forEach(CoreLogFileTailer::stop)
        tailers = emptyList()
    }

    fun pruneOlderThanDays(days: Int) {
        core.pruneOlderThanDays(days)
        access.pruneOlderThanDays(days)
    }

    /** Empties both viewers and truncates the files Core writes. */
    fun clear() {
        clearCore()
        clearAccess()
    }

    fun clearCore() {
        core.clear()
        truncate(paths.errorLogPath)
    }

    fun clearAccess() {
        access.clear()
        truncate(paths.accessLogPath)
    }

    private fun truncate(path: String) {
        runCatching {
            val file = Path.of(path)
            file.parent?.let(Files::createDirectories)
            Files.writeString(file, "")
        }.onFailure { error -> DesktopLogger.warn("DesktopCoreLogs", "Failed to clear Xray log file: $path", error) }
    }
}
