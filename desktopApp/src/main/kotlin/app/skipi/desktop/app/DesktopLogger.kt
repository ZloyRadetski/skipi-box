// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ConnectionLogMessage
import app.skipi.app.home.ConnectionLogSource
import app.skipi.app.home.connectionLogLevel
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DesktopLogger {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private const val TailBytes = 64 * 1024
    private const val MaxLogSizeBytes = 2L * 1024L * 1024L // 2 MB

    fun logDirectory(): Path {
        val base = System.getenv("APPDATA")
            ?.takeIf(String::isNotBlank)
            ?: System.getProperty("user.home")
        return Path.of(base, "SKIPI", "logs")
    }

    fun logFile(): Path = logDirectory().resolve("skipi.log")

    @Synchronized
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log("DEBUG", tag, message, throwable)
    }

    @Synchronized
    fun info(tag: String, message: String) {
        log("INFO", tag, message, null)
    }

    @Synchronized
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log("WARN", tag, message, throwable)
    }

    @Synchronized
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
    }

    @Synchronized
    fun recentEntries(limit: Int = 80): List<String> = runCatching {
        val file = logFile()
        if (!Files.exists(file)) return emptyList()
        tailLines(file, limit)
    }.getOrDefault(emptyList())

    /** The last [limit] lines, read from the last [TailBytes] of the file only (a log can be megabytes). */
    internal fun tailLines(file: Path, limit: Int): List<String> {
        java.io.RandomAccessFile(file.toFile(), "r").use { input ->
            val length = input.length()
            val start = (length - TailBytes).coerceAtLeast(0)
            val bytes = ByteArray((length - start).toInt())
            input.seek(start)
            input.readFully(bytes)
            var text = String(bytes, StandardCharsets.UTF_8)
            // A cut in the middle of a line (or of a character) leaves a partial first line: drop it.
            if (start > 0) text = text.substringAfter(Char(10), "")
            return text.lines().dropLastWhile(String::isEmpty).takeLast(limit)
        }
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val stackTrace = throwable?.let { error ->
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            "\n" + sw.toString().trimEnd()
        }.orEmpty()

        val line = "[$timestamp] [$level] [$tag] $message$stackTrace\n"
        println(line.trimEnd())
        val cause = throwable?.let { error -> ": ${error.message ?: error}" }.orEmpty()
        // The Home log filters these by the log level chosen in settings.
        DesktopAppLogFeed.add(
            ConnectionLogMessage(
                timeMillis = System.currentTimeMillis(),
                level = connectionLogLevel(level),
                source = ConnectionLogSource.App,
                text = "$tag: $message$cause$stackTrace",
            ),
        )

        try {
            val dir = logDirectory()
            Files.createDirectories(dir)
            val file = logFile()
            if (Files.exists(file) && Files.size(file) > MaxLogSizeBytes) {
                val oldFile = dir.resolve("skipi.log.old")
                Files.move(file, oldFile, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.writeString(
                file,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
        } catch (_: Throwable) {
            // Logging should never crash caller
        }
    }
}

/** Routes the shared [features.logs.AppLog] into the Desktop log file. */
object DesktopAppLogSink : features.logs.AppLogSink {
    override fun log(
        level: features.logs.AppLogLevel,
        tag: String,
        message: String,
        error: Throwable?,
        recordInApp: Boolean,
    ) {
        if (!recordInApp) return
        when (level) {
            features.logs.AppLogLevel.Debug -> DesktopLogger.debug(tag, message, error)
            features.logs.AppLogLevel.Info -> DesktopLogger.info(tag, message)
            features.logs.AppLogLevel.Warning -> DesktopLogger.warn(tag, message, error)
            features.logs.AppLogLevel.Error -> DesktopLogger.error(tag, message, error)
        }
    }
}
