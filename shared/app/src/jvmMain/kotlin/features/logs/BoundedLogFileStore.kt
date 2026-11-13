// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import java.io.File

class BoundedLogFileStore(
    private val file: () -> File?,
    private val logTag: String,
    private val maxLines: Int = CoreLogMaxEntries,
    /**
     * True when this store is the only writer of the file and keeps it to the last [maxLines] lines.
     * False for a file another process owns (Xray's log, followed by a tailer): [prune] then looks
     * at every line and rewrites the file only when the predicate drops one, because a rewrite
     * shrinks the file under the tailer and must not happen for the sake of a line window.
     */
    private val ownsFile: Boolean = true,
    private val trimExtraLines: Int = LogFileTrimExtraLines,
    private val onFailure: (message: String, error: Throwable) -> Unit,
) {
    private val lock = Any()
    private var lineCount = 0

    fun appendLine(line: String) = appendLines(listOf(line))

    /** Appends [lines] with one file open, trimming once afterwards if the bound was exceeded. */
    fun appendLines(lines: List<String>) {
        if (lines.isEmpty()) return
        val logFile = file() ?: return
        synchronized(lock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                logFile.appendText(lines.joinToString(separator = "\n", postfix = "\n"))
                lineCount += lines.size
                if (lineCount > maxLines + trimExtraLines) {
                    trimLocked(logFile)
                }
            }.onFailure { error ->
                reportFailure("append to", logFile, error)
            }
        }
    }

    fun clear() {
        val logFile = file() ?: return
        synchronized(lock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                logFile.writeText("")
                lineCount = 0
            }.onFailure { error ->
                reportFailure("clear", logFile, error)
            }
        }
    }

    fun prune(predicate: (String) -> Boolean) {
        val logFile = file() ?: return
        synchronized(lock) {
            runCatching {
                val lines = if (ownsFile) readLastLinesLocked(logFile) else readAllLinesLocked(logFile)
                val filtered = lines.filter(predicate)
                if (filtered.size != lines.size) {
                    logFile.writeText(
                        filtered.joinToString(separator = "\n", postfix = if (filtered.isEmpty()) "" else "\n")
                    )
                    lineCount = filtered.size
                }
            }.onFailure { error ->
                reportFailure("prune", logFile, error)
            }
        }
    }

    fun readLastLines(): List<String> {
        val logFile = file() ?: return emptyList()
        val lines = synchronized(lock) {
            readLastLinesLocked(logFile)
        }
        lineCount = lines.size
        return lines
    }

    private fun trimLocked(logFile: File) {
        val lines = readLastLinesLocked(logFile)
        logFile.writeText(
            lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n"),
        )
        lineCount = lines.size
    }

    private fun readAllLinesLocked(logFile: File): List<String> {
        if (!logFile.exists() || !logFile.isFile) return emptyList()
        return logFile.readLines(Charsets.UTF_8)
    }

    private fun readLastLinesLocked(logFile: File): List<String> {
        if (!logFile.exists() || !logFile.isFile || maxLines <= 0) {
            return emptyList()
        }

        return runCatching {
            val lines = ArrayDeque<String>(maxLines)
            logFile.forEachLine(Charsets.UTF_8) { line ->
                if (lines.size == maxLines) {
                    lines.removeFirst()
                }
                lines.addLast(line)
            }
            lines.toList()
        }.onFailure { error ->
            reportFailure("read", logFile, error)
        }.getOrDefault(emptyList())
    }

    private fun reportFailure(action: String, logFile: File, error: Throwable) {
        onFailure("Failed to $action $logTag file: ${logFile.absolutePath}", error)
    }
}
