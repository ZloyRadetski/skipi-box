// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import utils.decodeBase64StringOrNull
import utils.encodeBase64

/**
 * Application log viewer persisted as one tab-separated line per entry (message Base64-encoded
 * so multi-line text stays on a single line). Hosts supply the file through [initialize].
 */
open class PersistedAppLogRepository(
    private val logTag: String,
    private val onFailure: (message: String, error: Throwable) -> Unit,
) : InMemoryCoreLogRepository() {

    private val restoredPreviousLogs = AtomicBoolean(false)

    // Disk writes happen on one background thread, in order: onAppended runs inside the in-memory log's lock on
    // whatever thread logged, and a file append (or the trim that rewrites the whole file) must not run there.
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "skipi-log-writer").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    /** Appends are written only once the first restore has merged what was logged before the file was known. */
    @Volatile
    private var persisting = false
    private val fileStore = BoundedLogFileStore(
        file = { logFile },
        logTag = logTag,
        onFailure = onFailure,
    )

    fun initialize(file: File, retentionDays: Int = 0) {
        logFile = file
        restorePreviousLogs(retentionDays)
    }

    override fun pruneOlderThanDays(days: Int) {
        super.pruneOlderThanDays(days)
        if (days <= 0) return
        flushPendingWrites()
        fileStore.prune { line ->
            val entry = decodeLogLine(0L, line) ?: return@prune true
            !entry.isOlderThanDays(days)
        }
    }

    // Every committed append, single or batch, is written with one file open per batch.
    override fun onAppended(lines: List<ParsedCoreLogLine>) {
        // Before initialize() there is no file; those entries are merged and written by restorePreviousLogs().
        if (!persisting) return
        val encoded = lines.map { line -> encodeLogLine(checkNotNull(line.time), line.level, line.message) }
        writer.execute { fileStore.appendLines(encoded) }
    }

    override fun clear() {
        super.clear()
        writer.execute { fileStore.clear() }
    }

    /** Waits until every append queued so far is on disk (before reading the file, and for tests). */
    fun flushPendingWrites() {
        runCatching { writer.submit {}.get(FlushTimeoutSeconds, TimeUnit.SECONDS) }
    }

    override suspend fun refresh() {
        // Lines appended while the file is read must not be dropped by the replacement: retry on a fresh read, and
        // if appends keep arriving keep the in-memory entries, which already hold every one of them.
        repeat(RefreshAttempts) {
            val seen = changeCount()
            val restoredEntries = withContext(Dispatchers.IO) {
                flushPendingWrites()
                readPersistedEntries()
            }
            if (replaceEntriesIfUnchanged(seen, restoredEntries)) return
        }
    }

    private fun restorePreviousLogs(retentionDays: Int = 0) {
        if (!restoredPreviousLogs.compareAndSet(false, true)) {
            if (retentionDays > 0) pruneOlderThanDays(retentionDays)
            return
        }
        flushPendingWrites()
        if (retentionDays > 0) {
            fileStore.prune { line ->
                val entry = decodeLogLine(0L, line) ?: return@prune true
                !entry.isOlderThanDays(retentionDays)
            }
        }
        // Appends keep landing in memory meanwhile and are not written (persisting is off) until the merge commits,
        // atomically with it: otherwise a line logged during the restore would be on disk and also re-appended below.
        repeat(RefreshAttempts) {
            val seen = changeCount()
            val pendingEntries = entries.value
            val merged = readPersistedEntries() + pendingEntries
            val committed = replaceEntriesIfUnchanged(seen, merged) {
                persisting = true
                val encoded = pendingEntries.map { entry -> encodeLogLine(entry.time, entry.level, entry.message) }
                writer.execute { fileStore.appendLines(encoded) }
            }
            if (committed) return
        }
        val pendingEntries = entries.value
        replaceEntries(readPersistedEntries() + pendingEntries)
        persisting = true
        fileStore.appendLines(pendingEntries.map { entry -> encodeLogLine(entry.time, entry.level, entry.message) })
    }

    private fun readPersistedEntries(): List<CoreLogEntry> {
        return fileStore.readLastLines()
            .mapIndexedNotNull { index, line ->
                decodeLogLine(id = index + 1L, line = line)
            }
    }
}

private const val FlushTimeoutSeconds = 10L
private const val RefreshAttempts = 5

private const val LogcatFieldSeparator = '\t'

private fun encodeLogLine(time: String, level: String, message: String): String {
    return listOf(time, level, message.encodeBase64()).joinToString(LogcatFieldSeparator.toString())
}

private fun decodeLogLine(id: Long, line: String): CoreLogEntry? {
    val fields = line.split(LogcatFieldSeparator, limit = 3)
    if (fields.size != 3) {
        return null
    }
    val message = fields[2].decodeBase64StringOrNull() ?: return null
    return CoreLogEntry(
        id = id,
        time = fields[0],
        level = fields[1],
        message = message,
    )
}
