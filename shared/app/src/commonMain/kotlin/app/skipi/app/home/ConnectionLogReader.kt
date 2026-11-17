// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/**
 * Reads the connections Core recorded since the last read. [read] asks Core for the entries after
 * a cursor and returns its JSON; the reader keeps the cursor and survives a Core restart.
 */
class ConnectionLogReader(private val read: (afterSeq: Long) -> Result<String>) {
    private var cursor = 0L

    @Synchronized
    fun next(): List<ConnectionLogEntry> {
        val snapshot = read(cursor).mapCatching(::parseConnectionLogSnapshot).getOrNull() ?: return emptyList()
        // Every Core session counts from zero, so a smaller cursor means a new session.
        if (snapshot.seq < cursor) {
            cursor = 0
            return emptyList()
        }
        cursor = snapshot.seq
        return snapshot.entries
    }

    @Synchronized
    fun reset() {
        cursor = 0
    }
}

/** A connection as a line of plain text: `12:03:41  tcp  youtube.com:443  → proxy`. */
fun ConnectionLogEntry.accessLogText(formatTime: (Long) -> String): String {
    val address = if (':' in host) "[$host]:$port" else "$host:$port"
    return "${formatTime(timeMillis)}  $network  $address  → $outbound"
}
