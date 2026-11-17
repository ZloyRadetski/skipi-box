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

/**
 * Where a log list should scroll after it changed, or null to leave it. A log reads like a terminal: the newest
 * line is at the bottom, a log is first looked at from there, and a reader who stayed at the bottom keeps seeing
 * the newest lines as they arrive. [newestId] is used instead of the count, because a full log keeps its length.
 */
fun logScrollTarget(
    previousNewestId: Long?,
    newestId: Long?,
    count: Int,
    following: Boolean,
    listChanged: Boolean = false,
): Int? = when {
    count == 0 -> null
    listChanged || previousNewestId == null -> count - 1
    following && newestId != previousNewestId -> count - 1
    else -> null
}
