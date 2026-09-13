// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CoreLogEntry(
    val id: Long,
    val time: String,
    val level: String,
    val message: String,
)

interface CoreLogRepository {
    val entries: StateFlow<List<CoreLogEntry>>

    fun append(level: String, message: String, time: String = currentLogTime())

    /** Commits a file-tail batch as one UI state update. */
    fun appendBatch(lines: List<ParsedCoreLogLine>) {
        lines.forEach { line ->
            append(
                level = line.level,
                message = line.message,
                time = line.time ?: currentLogTime(),
            )
        }
    }

    fun clear()

    fun pruneOlderThanDays(days: Int) = Unit

    suspend fun refresh() = Unit
}

open class InMemoryCoreLogRepository(
    private val maxEntries: Int = CoreLogMaxEntries,
    initialEntries: List<CoreLogEntry> = emptyList(),
) : CoreLogRepository {
    private val mutableEntries = MutableStateFlow(initialEntries.takeLast(maxEntries))

    override val entries: StateFlow<List<CoreLogEntry>> = mutableEntries.asStateFlow()

    override fun pruneOlderThanDays(days: Int) {
        if (days <= 0) return
        mutableEntries.update { entries ->
            entries.filter { !it.isOlderThanDays(days) }
        }
    }

    override fun append(level: String, message: String, time: String) {
        appendBatch(
            listOf(
                ParsedCoreLogLine(
                    time = time,
                    level = level,
                    message = message,
                ),
            ),
        )
    }

    override fun appendBatch(lines: List<ParsedCoreLogLine>) {
        val normalizedLines = lines.mapNotNull { line ->
            val normalizedMessage = line.message.trim()
            normalizedMessage.takeIf(String::isNotEmpty)?.let { message ->
                line.copy(
                    time = line.time ?: currentLogTime(),
                    level = line.level.normalizedLogLevel(),
                    message = message,
                )
            }
        }
        if (normalizedLines.isEmpty()) return
        mutableEntries.update { entries ->
            var nextId = (entries.lastOrNull()?.id ?: 0L) + 1L
            val additions = normalizedLines.map { line ->
                CoreLogEntry(
                    id = nextId++,
                    time = checkNotNull(line.time),
                    level = line.level,
                    message = line.message,
                )
            }
            (entries + additions).takeLast(maxEntries)
        }
    }

    override fun clear() {
        mutableEntries.value = emptyList()
    }

    protected fun replaceEntries(entries: List<CoreLogEntry>) {
        val normalizedEntries = entries
            .mapNotNull { entry ->
                val normalizedMessage = entry.message.trim()
                if (normalizedMessage.isEmpty()) {
                    null
                } else {
                    entry.copy(
                        level = entry.level.normalizedLogLevel(),
                        message = normalizedMessage,
                    )
                }
            }
            .takeLast(maxEntries)

        mutableEntries.value = normalizedEntries.mapIndexed { index, entry ->
            entry.copy(id = index + 1L)
        }
    }
}

private fun String.normalizedLogLevel(): String {
    return when (lowercase()) {
        "debug" -> "debug"
        "warning", "warn" -> "warning"
        "error" -> "error"
        else -> "info"
    }
}

fun currentLogTime(): String {
    val calendar = Calendar.getInstance()
    return buildString {
        append(calendar.get(Calendar.YEAR))
        append('-')
        append((calendar.get(Calendar.MONTH) + 1).twoDigits())
        append('-')
        append(calendar.get(Calendar.DAY_OF_MONTH).twoDigits())
        append(' ')
        append(calendar.get(Calendar.HOUR_OF_DAY).twoDigits())
        append(':')
        append(calendar.get(Calendar.MINUTE).twoDigits())
        append(':')
        append(calendar.get(Calendar.SECOND).twoDigits())
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
