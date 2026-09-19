// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

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
    private const val MaxLogSizeBytes = 2L * 1024L * 1024L // 2 MB

    fun logDirectory(): Path {
        val base = System.getenv("APPDATA")
            ?.takeIf(String::isNotBlank)
            ?: System.getProperty("user.home")
        return Path.of(base, "SKIPI", "logs")
    }

    fun logFile(): Path = logDirectory().resolve("skipi.log")

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
        val all = Files.readAllLines(file, StandardCharsets.UTF_8)
        all.takeLast(limit)
    }.getOrDefault(emptyList())

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val stackTrace = throwable?.let { error ->
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            "\n" + sw.toString().trimEnd()
        }.orEmpty()

        val line = "[$timestamp] [$level] [$tag] $message$stackTrace\n"
        println(line.trimEnd())

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
