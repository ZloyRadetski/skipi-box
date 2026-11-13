// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    private val lock = Any()

    // Entries live in an append-only array window. A published snapshot is a view of [start, end) of
    // one array, and a slot is only written after every snapshot that could see it (index >= end),
    // so snapshots stay immutable without copying the list on every append. The array is rebuilt
    // (one O(maxEntries) copy) only after maxEntries further appends, which amortises to O(1).
    private var buffer = arrayOfNulls<CoreLogEntry>(bufferCapacity())
    private var start = 0
    private var end = 0
    private var changes = 0L
    private val mutableEntries = MutableStateFlow<List<CoreLogEntry>>(emptyList())

    init {
        synchronized(lock) { resetLocked(initialEntries.takeLast(maxEntries)) }
    }

    override val entries: StateFlow<List<CoreLogEntry>> = mutableEntries.asStateFlow()

    override fun pruneOlderThanDays(days: Int) {
        if (days <= 0) return
        synchronized(lock) {
            val kept = snapshotLocked().filter { !it.isOlderThanDays(days) }
            if (kept.size != end - start) {
                changes += 1
                resetLocked(kept)
            }
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
        synchronized(lock) {
            changes += 1
            var nextId = (if (end > start) buffer[end - 1]?.id ?: 0L else 0L) + 1L
            val additions = normalizedLines.takeLast(maxEntries).map { line ->
                CoreLogEntry(
                    id = nextId++,
                    time = checkNotNull(line.time),
                    level = line.level,
                    message = line.message,
                )
            }
            if (end + additions.size > buffer.size) {
                val keep = (end - start).coerceAtMost(maxEntries - additions.size)
                val rebuilt = arrayOfNulls<CoreLogEntry>(buffer.size)
                buffer.copyInto(rebuilt, 0, end - keep, end)
                buffer = rebuilt
                start = 0
                end = keep
            }
            additions.forEachIndexed { index, entry -> buffer[end + index] = entry }
            end += additions.size
            if (end - start > maxEntries) start = end - maxEntries
            mutableEntries.value = EntriesWindow(buffer, start, end)
            onAppended(normalizedLines)
        }
    }

    override fun clear() {
        synchronized(lock) {
            changes += 1
            resetLocked(emptyList())
        }
    }

    /** Called with each committed batch (trimmed, level-normalised, timed), in commit order. */
    protected open fun onAppended(lines: List<ParsedCoreLogLine>) = Unit

    /** Number of mutations so far; lets a reader detect that entries changed while it worked. */
    protected fun changeCount(): Long = synchronized(lock) { changes }

    protected fun replaceEntries(entries: List<CoreLogEntry>) {
        synchronized(lock) { replaceEntriesLocked(entries) }
    }

    /** Replaces the entries only if nothing was appended or removed since [expectedChangeCount]. */
    protected fun replaceEntriesIfUnchanged(expectedChangeCount: Long, entries: List<CoreLogEntry>): Boolean {
        synchronized(lock) {
            if (changes != expectedChangeCount) return false
            replaceEntriesLocked(entries)
            return true
        }
    }

    private fun replaceEntriesLocked(entries: List<CoreLogEntry>) {
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

        resetLocked(normalizedEntries.mapIndexed { index, entry -> entry.copy(id = index + 1L) })
    }

    private fun snapshotLocked(): List<CoreLogEntry> = EntriesWindow(buffer, start, end).toList()

    private fun resetLocked(entries: List<CoreLogEntry>) {
        buffer = arrayOfNulls(bufferCapacity())
        entries.forEachIndexed { index, entry -> buffer[index] = entry }
        start = 0
        end = entries.size
        mutableEntries.value = EntriesWindow(buffer, start, end)
    }

    private fun bufferCapacity(): Int = maxEntries.coerceAtLeast(1) * 2
}

/** Immutable view of one slice of an append-only array; see [InMemoryCoreLogRepository]. */
private class EntriesWindow(
    private val array: Array<CoreLogEntry?>,
    private val from: Int,
    private val to: Int,
) : AbstractList<CoreLogEntry>(), RandomAccess {
    override val size: Int get() = to - from

    override fun get(index: Int): CoreLogEntry {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("index: $index, size: $size")
        return checkNotNull(array[from + index])
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
