// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.logs.AndroidAccessLogRepository
import features.logs.AppLog
import features.logs.AndroidCoreLogRepository
import features.logs.CoreLogFile
import features.logs.CoreLogFileTailer
import java.io.File

internal fun XrayCoreLogPaths.startCoreLogTailers(): List<CoreLogFileTailer> {
    // The Access logs page is fed by Core's connection log, not by tailing access.log.
    return buildList {
        add(
            CoreLogFileTailer(
                logFiles = listOf(errorLogFile()),
                repository = AndroidCoreLogRepository,
                startAtEnd = false,
                onFailure = { message, error -> AppLog.warn("CoreLogFileTailer", message, error) },
            ),
        )
    }.onEach { tailer -> tailer.start() }
}

internal fun List<CoreLogFileTailer>.stopCoreLogTailers() {
    forEach { tailer -> tailer.stop() }
}

internal fun XrayCoreLogPaths.clearCoreLogs(logTag: String) {
    AndroidCoreLogRepository.clear()
    AndroidAccessLogRepository.clear()
    clearCoreLogFilesAsApp(
        logPaths = logFilePaths(),
        logTag = logTag,
    )
}

internal fun XrayCoreLogPaths.logFilePaths(): List<String> {
    return listOf(accessLogPath, errorLogPath).filter(String::isNotBlank)
}

internal fun clearCoreLogFilesAsApp(logPaths: List<String>, logTag: String) {
    logPaths
        .filter(String::isNotBlank)
        .forEach { logPath ->
            runCatching {
                File(logPath).apply {
                    parentFile?.mkdirs()
                    writeText("")
                }
            }.onFailure { error ->
                AppLog.warn(logTag, "Failed to clear Xray log file: $logPath", error)
            }
        }
}

private fun XrayCoreLogPaths.errorLogFile(): CoreLogFile {
    return CoreLogFile(path = errorLogPath, defaultLevel = "error")
}
