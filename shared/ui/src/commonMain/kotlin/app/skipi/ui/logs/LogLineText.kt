// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import features.logs.CoreLogEntry

/** How loud a level is drawn: errors and warnings get a colour, everything else stays quiet. */
enum class LogTone { Error, Warning, Neutral }

fun logTone(level: String): LogTone = when (level.trim().lowercase()) {
    "error", "fatal", "panic" -> LogTone.Error
    "warn", "warning" -> LogTone.Warning
    else -> LogTone.Neutral
}

/** One log row as plain text: the parts are coloured apart, [plain] is what copy and export write. */
data class LogLine(val time: String?, val level: String?, val message: String) {
    val plain: String
        get() = if (time == null || level == null) message else "$time  $level  $message"
}

fun CoreLogEntry.logLine(showMetadata: Boolean): LogLine =
    if (showMetadata) LogLine(time, level.uppercase(), message) else LogLine(null, null, message)

/** The shown rows as one block of text, for the copy action. */
fun logBlockText(entries: List<CoreLogEntry>, showMetadata: Boolean): String =
    entries.joinToString("\n") { it.exportText(showMetadata) }
