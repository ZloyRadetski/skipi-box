// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core (Xray) log viewer backed by a bounded log file: restores the previous session sorted by
 * time, applies the retention period and re-reads the file on refresh. Hosts supply the file
 * location through [initialize].
 */
open class XrayFileLogRepository(
    private val logTag: String,
    private val onFailure: (message: String, error: Throwable) -> Unit,
) : InMemoryCoreLogRepository() {
    private val restoredPreviousLogs = AtomicBoolean(false)
    private var logFile: CoreLogFile? = null
    private val fileStore = BoundedLogFileStore(
        file = { logFile?.let { File(it.path) } },
        logTag = logTag,
        onFailure = onFailure,
    )

    fun initialize(logFile: CoreLogFile, retentionDays: Int = 0) {
        this.logFile = logFile
        restorePreviousLogs(retentionDays)
    }

    override fun pruneOlderThanDays(days: Int) {
        super.pruneOlderThanDays(days)
        val file = logFile ?: return
        if (days <= 0) return
        fileStore.prune { line ->
            val parsed = parseCoreLogLine(line, file.defaultLevel) ?: return@prune true
            !parsed.isOlderThanDays(days)
        }
    }

    override suspend fun refresh() {
        val file = logFile ?: return
        val restoredLines = withContext(Dispatchers.IO) {
            readRestoredLines(file)
        }
        replaceRestoredLines(restoredLines)
    }

    private fun restorePreviousLogs(retentionDays: Int = 0) {
        val file = logFile ?: return
        if (!restoredPreviousLogs.compareAndSet(false, true) || entries.value.isNotEmpty()) {
            if (retentionDays > 0) pruneOlderThanDays(retentionDays)
            return
        }

        if (retentionDays > 0) {
            fileStore.prune { line ->
                val parsed = parseCoreLogLine(line, file.defaultLevel) ?: return@prune true
                !parsed.isOlderThanDays(retentionDays)
            }
        }
        replaceRestoredLines(readRestoredLines(file))
    }

    private fun readRestoredLines(file: CoreLogFile): List<RestoredCoreLogLine> {
        var order = 0L
        return fileStore
            .readLastLines()
            .mapNotNull { line ->
                parseCoreLogLine(line, file.defaultLevel)?.let { parsedLine ->
                    RestoredCoreLogLine(
                        order = order++,
                        parsedLine = parsedLine,
                    )
                }
            }
            .sortedWith(
                compareBy<RestoredCoreLogLine> { it.parsedLine.time.orEmpty() }
                    .thenBy { it.order },
            )
            .takeLast(MaxEntries)
    }

    private fun replaceRestoredLines(restoredLines: List<RestoredCoreLogLine>) {
        replaceEntries(
            restoredLines.mapIndexed { index, restoredLine ->
                val parsedLine = restoredLine.parsedLine
                CoreLogEntry(
                    id = index + 1L,
                    time = parsedLine.time ?: currentLogTime(),
                    level = parsedLine.level,
                    message = parsedLine.message,
                )
            }
        )
    }
}

private data class RestoredCoreLogLine(
    val order: Long,
    val parsedLine: ParsedCoreLogLine,
)

private const val MaxEntries = CoreLogMaxEntries
