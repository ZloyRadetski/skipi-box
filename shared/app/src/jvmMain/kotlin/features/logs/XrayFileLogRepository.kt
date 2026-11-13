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
        // Xray owns this file and a tailer follows it: prune must not rewrite it for a line window.
        ownsFile = false,
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
        // Lines the tailer publishes while the file is read are not in that read; replacing then
        // would drop them. The in-memory list is already current in that case, so keep it.
        val changesBefore = changeCount()
        val restoredLines = withContext(Dispatchers.IO) {
            readRestoredLines(file)
        }
        afterRefreshRead()
        replaceRestoredLines(restoredLines, onlyIfUnchangedSince = changesBefore)
    }

    /** Test seam: runs between reading the file and replacing the entries. */
    internal var afterRefreshRead: () -> Unit = {}

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
        // Lines without a timestamp continue the line before them and sort with its time, so they
        // keep file order instead of floating to the front under an empty key.
        var inheritedTime = ""
        return fileStore
            .readLastLines()
            .mapNotNull { line ->
                parseCoreLogLine(line, file.defaultLevel)?.let { parsedLine ->
                    parsedLine.time?.let { inheritedTime = it }
                    RestoredCoreLogLine(
                        order = order++,
                        sortTime = inheritedTime,
                        parsedLine = parsedLine,
                    )
                }
            }
            .sortedWith(
                compareBy<RestoredCoreLogLine> { it.sortTime }
                    .thenBy { it.order },
            )
            .takeLast(MaxEntries)
    }

    private fun replaceRestoredLines(
        restoredLines: List<RestoredCoreLogLine>,
        onlyIfUnchangedSince: Long? = null,
    ) {
        val restored =
            restoredLines.mapIndexed { index, restoredLine ->
                val parsedLine = restoredLine.parsedLine
                CoreLogEntry(
                    id = index + 1L,
                    time = parsedLine.time ?: currentLogTime(),
                    level = parsedLine.level,
                    message = parsedLine.message,
                )
            }
        if (onlyIfUnchangedSince == null) replaceEntries(restored)
        else replaceEntriesIfUnchanged(onlyIfUnchangedSince, restored)
    }
}

private data class RestoredCoreLogLine(
    val order: Long,
    val sortTime: String,
    val parsedLine: ParsedCoreLogLine,
)

private const val MaxEntries = CoreLogMaxEntries
