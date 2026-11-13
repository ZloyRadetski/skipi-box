// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class CoreLogFile(
    val path: String,
    val defaultLevel: String,
)

class CoreLogFileTailer(
    private val logFiles: List<CoreLogFile>,
    private val repository: CoreLogRepository,
    private val startAtEnd: Boolean = true,
    private val onFailure: (message: String, error: Throwable) -> Unit = { _, _ -> },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        logFiles
            .filter { logFile -> logFile.path.isNotBlank() }
            .forEach { logFile ->
                scope.launch {
                    tail(logFile)
                }
            }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun tail(logFile: CoreLogFile) {
        val file = File(logFile.path)
        file.parentFile?.mkdirs()
        val poller = CoreLogFilePoller(file, startAtEnd)
        var failureLogged = false

        while (scope.isActive) {
            runCatching {
                repository.appendParsedCoreLogLines(poller.poll(), logFile.defaultLevel)
            }.onSuccess {
                failureLogged = false
            }.onFailure { error ->
                if (!failureLogged) {
                    onFailure("Failed to tail Xray log file: ${file.absolutePath}", error)
                    failureLogged = true
                }
            }

            delay(CoreLogTailerPollingPolicy.intervalMillis())
        }
    }
}

/**
 * Reads the complete lines appended to [file] since the previous [poll].
 *
 * - A last line without its trailing `
` is not returned; the read position stays in front of it
 *   until the newline arrives, so a line that Xray is still writing is never published in halves.
 * - The poller remembers the last line it returned. When the file got shorter or no longer holds
 *   that line at the remembered position, it was rewritten: if the rewrite kept the remembered
 *   line (retention pruning drops only old lines), reading resumes right after it and nothing
 *   already published is returned again; if it is gone, the file was truncated or rotated and
 *   reading restarts at the beginning.
 */
internal class CoreLogFilePoller(private val file: File, startAtEnd: Boolean) {
    private var position: Long = if (startAtEnd) runCatching { file.length() }.getOrDefault(0L) else 0L
    private var anchor: ByteArray? = null

    fun poll(): List<String> {
        if (!file.exists()) return emptyList()
        RandomAccessFile(file, "r").use { reader ->
            if (reader.length() < position || !anchorIntact(reader)) {
                position = positionAfterRewrite(reader)
            }
            val available = reader.length() - position
            if (available <= 0L) return emptyList()

            val chunk = ByteArray(minOf(available, MaxBytesPerPoll.toLong()).toInt())
            reader.seek(position)
            reader.readFully(chunk)
            var end = chunk.lastIndexOf(NewLine)
            if (end < 0) {
                if (chunk.size < MaxBytesPerPoll) return emptyList()
                end = chunk.size // an oversized line: emit it rather than stall forever
            }
            val consumed = if (end < chunk.size && chunk[end] == NewLine) end + 1 else end

            val lines = ArrayList<String>()
            var lineStart = 0
            var lastRawLine: ByteArray? = null
            fun emit(to: Int) {
                val line = chunk.decodeLine(lineStart, to)
                lines += line
                if (line.isNotBlank()) lastRawLine = chunk.copyOfRange(lineStart, minOf(to + 1, consumed))
                lineStart = to + 1
            }
            for (index in 0 until consumed) {
                if (chunk[index] == NewLine) emit(index)
            }
            if (lineStart < consumed) emit(consumed)
            position += consumed
            lastRawLine?.let { anchor = it }
            return lines
        }
    }

    private fun ByteArray.decodeLine(from: Int, to: Int): String {
        val last = if (to > from && this[to - 1] == CarriageReturn) to - 1 else to
        return String(this, from, last - from, Charsets.UTF_8)
    }

    /** True while the bytes in front of [position] are still the line published last. */
    private fun anchorIntact(reader: RandomAccessFile): Boolean {
        val line = anchor ?: return true
        val start = position - line.size
        if (start < 0 || position > reader.length()) return false
        val bytes = ByteArray(line.size)
        reader.seek(start)
        reader.readFully(bytes)
        return bytes.contentEquals(line)
    }

    /** Position after the last line of the rewritten file that equals the anchor, else 0. */
    private fun positionAfterRewrite(reader: RandomAccessFile): Long {
        val line = anchor ?: return 0L
        val bytes = ByteArray(reader.length().toInt())
        reader.seek(0)
        reader.readFully(bytes)
        var end = bytes.size - line.size
        while (end >= 0) {
            val startsLine = end == 0 || bytes[end - 1] == NewLine
            if (startsLine && bytes.regionEquals(end, line)) return (end + line.size).toLong()
            end -= 1
        }
        return 0L
    }

    private fun ByteArray.regionEquals(from: Int, other: ByteArray): Boolean {
        for (offset in other.indices) if (this[from + offset] != other[offset]) return false
        return true
    }

    private companion object {
        const val MaxBytesPerPoll = 8 * 1024 * 1024
        val NewLine: Byte = 10
        val CarriageReturn: Byte = 13
    }
}

data class ParsedCoreLogLine(
    val time: String?,
    val level: String,
    val message: String,
)

private val XrayLogLineRegex = Regex("""^(\d{4}/\d{2}/\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?)\s+\[([A-Za-z]+)]\s*(.*)$""")
private val XrayLogLineWithoutLevelRegex = Regex("""^(\d{4}/\d{2}/\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?)\s+(.*)$""")
private val XrayLogTimeWhitespaceRegex = Regex("\\s+")
private const val XrayLogTimeFormat = "yyyy-MM-dd HH:mm:ss"

// Xray-core (Go) timestamps are UTC on Android: Go's runtime stubs the local
// timezone to UTC and ignores $TZ (src/time/zoneinfo_android.go initLocal), and
// skipicore embeds no tzdata. Parse the captured stamp as UTC, render in the
// device's local timezone so the viewer shows wall-clock time.
private val utcXrayLogTimeParser = ThreadLocal.withInitial {
    SimpleDateFormat(XrayLogTimeFormat, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
}
private val localXrayLogTimeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat(XrayLogTimeFormat, Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
}

private fun toLocalXrayLogTime(rawXrayTime: String): String? {
    val normalized = rawXrayTime.replace('/', '-').replace(XrayLogTimeWhitespaceRegex, " ")
    // Xray-core may include sub-second precision (e.g. 11:48:12.901279) which
    // SimpleDateFormat "yyyy-MM-dd HH:mm:ss" cannot parse. Strip the fraction.
    val withoutFraction = normalized.substringBeforeLast('.')
    val parser = utcXrayLogTimeParser.get() ?: return null
    val instant = runCatching { parser.parse(withoutFraction) }.getOrNull() ?: return null
    val formatter = localXrayLogTimeFormatter.get() ?: return null
    formatter.timeZone = TimeZone.getDefault()
    return runCatching { formatter.format(instant) }.getOrNull()
}

fun parseCoreLogTimeMillis(time: String): Long? {
    val formatter = localXrayLogTimeFormatter.get() ?: return null
    formatter.timeZone = TimeZone.getDefault()
    return runCatching { formatter.parse(time.trim())?.time }.getOrNull()
}

fun ParsedCoreLogLine.isOlderThanDays(days: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (days <= 0 || time == null) return false
    val millis = parseCoreLogTimeMillis(time) ?: return false
    return millis < (nowMillis - days * 24L * 60L * 60L * 1000L)
}

fun CoreLogEntry.isOlderThanDays(days: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (days <= 0) return false
    val millis = parseCoreLogTimeMillis(time) ?: return false
    return millis < (nowMillis - days * 24L * 60L * 60L * 1000L)
}

fun CoreLogRepository.appendParsedCoreLogLine(line: String, defaultLevel: String) {
    val parsedLine = parseCoreLogLine(line, defaultLevel) ?: return
    if (parsedLine.time == null) {
        append(level = parsedLine.level, message = parsedLine.message)
    } else {
        append(level = parsedLine.level, message = parsedLine.message, time = parsedLine.time)
    }
}

fun CoreLogRepository.appendParsedCoreLogLines(lines: List<String>, defaultLevel: String) {
    appendBatch(lines.mapNotNull { line -> parseCoreLogLine(line, defaultLevel) })
}

fun parseCoreLogLine(line: String, defaultLevel: String): ParsedCoreLogLine? {
    val trimmedLine = line.trim()
    if (trimmedLine.isEmpty()) {
        return null
    }

    XrayLogLineRegex.matchEntire(trimmedLine)?.let { match ->
        val (rawTime, level, message) = match.destructured
        val localTime = toLocalXrayLogTime(rawTime) ?: rawTime.replace('/', '-')
        return ParsedCoreLogLine(
            time = localTime,
            level = level,
            message = "$localTime [$level] $message",
        )
    }

    XrayLogLineWithoutLevelRegex.matchEntire(trimmedLine)?.let { match ->
        val (rawTime, message) = match.destructured
        val localTime = toLocalXrayLogTime(rawTime) ?: rawTime.replace('/', '-')
        return ParsedCoreLogLine(
            time = localTime,
            level = defaultLevel,
            message = "$localTime $message",
        )
    }

    return ParsedCoreLogLine(
        time = null,
        level = defaultLevel,
        message = trimmedLine,
    )
}
