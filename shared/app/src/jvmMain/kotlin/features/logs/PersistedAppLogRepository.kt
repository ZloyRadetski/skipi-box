// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    private var logFile: File? = null
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
        fileStore.prune { line ->
            val entry = decodeLogLine(0L, line) ?: return@prune true
            !entry.isOlderThanDays(days)
        }
    }

    override fun append(level: String, message: String, time: String) {
        super.append(level, message, time)
        appendPersistedLine(time = time, level = level, message = message)
    }

    override fun clear() {
        super.clear()
        fileStore.clear()
    }

    override suspend fun refresh() {
        val restoredEntries = withContext(Dispatchers.IO) {
            readPersistedEntries()
        }
        replaceEntries(restoredEntries)
    }

    private fun restorePreviousLogs(retentionDays: Int = 0) {
        if (!restoredPreviousLogs.compareAndSet(false, true)) {
            if (retentionDays > 0) pruneOlderThanDays(retentionDays)
            return
        }
        if (retentionDays > 0) {
            fileStore.prune { line ->
                val entry = decodeLogLine(0L, line) ?: return@prune true
                !entry.isOlderThanDays(retentionDays)
            }
        }
        val pendingEntries = entries.value
        replaceEntries(readPersistedEntries() + pendingEntries)
        pendingEntries.forEach { entry ->
            appendPersistedLine(time = entry.time, level = entry.level, message = entry.message)
        }
    }

    private fun appendPersistedLine(time: String, level: String, message: String) {
        fileStore.appendLine(encodeLogLine(time, level, message))
    }

    private fun readPersistedEntries(): List<CoreLogEntry> {
        return fileStore.readLastLines()
            .mapIndexedNotNull { index, line ->
                decodeLogLine(id = index + 1L, line = line)
            }
    }
}

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
