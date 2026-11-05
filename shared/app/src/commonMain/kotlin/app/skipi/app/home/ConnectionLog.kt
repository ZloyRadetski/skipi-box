// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import engine.xray.XrayTags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** What the Home log can be given: a connection or a line of the app's or Xray's own log. */
sealed interface ConnectionLogItem

/** One connection the running session accepted, as SKIPI Core reports it. */
data class ConnectionLogEntry(
    val seq: Long,
    val timeMillis: Long,
    val host: String,
    val port: Int,
    val network: String,
    val outbound: String,
) : ConnectionLogItem

enum class ConnectionLogLevel { Debug, Info, Warning, Error }

enum class ConnectionLogSource { App, Core }

/** A line of a log; [text] may run on into a stack trace. */
data class ConnectionLogMessage(
    val timeMillis: Long,
    val level: ConnectionLogLevel,
    val source: ConnectionLogSource,
    val text: String,
) : ConnectionLogItem

fun connectionLogLevel(name: String): ConnectionLogLevel = when (name.lowercase()) {
    "debug" -> ConnectionLogLevel.Debug
    "warning", "warn" -> ConnectionLogLevel.Warning
    "error" -> ConnectionLogLevel.Error
    else -> ConnectionLogLevel.Info
}

/** Connections newer than the cursor passed to Core, and the cursor for the next read. */
data class ConnectionLogSnapshot(
    val seq: Long,
    val entries: List<ConnectionLogEntry>,
)

fun parseConnectionLogSnapshot(json: String): ConnectionLogSnapshot {
    val snapshot = Json.parseToJsonElement(json).jsonObject
    return ConnectionLogSnapshot(
        seq = snapshot.getValue("seq").jsonPrimitive.long,
        entries = snapshot.getValue("entries").jsonArray.map { element ->
            val entry = element.jsonObject
            ConnectionLogEntry(
                seq = entry.getValue("seq").jsonPrimitive.long,
                timeMillis = entry.getValue("time").jsonPrimitive.long,
                host = entry.getValue("host").jsonPrimitive.content,
                port = entry.getValue("port").jsonPrimitive.int,
                network = entry.getValue("network").jsonPrimitive.content,
                outbound = entry.getValue("outbound").jsonPrimitive.content,
            )
        },
    )
}

enum class ConnectionLogRoute { Proxy, Direct, Block }

/** A visible line of the Home log. [id] belongs to the list: Core numbers every session from zero. */
sealed interface ConnectionLogLine {
    val id: Long
    val timeMillis: Long
    val count: Int
    val copyText: String
}

/** A connection; [count] repeats of one host and route share the row. */
data class ConnectionLogRow(
    override val id: Long,
    override val timeMillis: Long,
    val host: String,
    val isAddress: Boolean,
    val outbound: String,
    val route: ConnectionLogRoute,
    override val count: Int = 1,
) : ConnectionLogLine {
    override val copyText: String get() = host
}

/** A log line, shown by its [title]; [count] repeats of one text, level and source share the row. */
data class ConnectionLogMessageRow(
    override val id: Long,
    override val timeMillis: Long,
    val level: ConnectionLogLevel,
    val source: ConnectionLogSource,
    override val copyText: String,
    override val count: Int = 1,
) : ConnectionLogLine {
    val title: String get() = copyText.substringBefore('\n')
}

/**
 * What the Home log shows. While [held] the visible rows stay exactly as they are, so a line can be
 * read, clicked or scrolled to; what arrives meanwhile joins on [release].
 */
data class ConnectionLogState(
    val rows: List<ConnectionLogLine> = emptyList(),
    val held: Boolean = false,
    private val heldItems: List<ConnectionLogItem> = emptyList(),
    private val nextRowId: Long = 1,
) {
    val heldCount: Int get() = heldItems.size

    fun append(items: List<ConnectionLogItem>): ConnectionLogState = when {
        items.isEmpty() -> this
        held -> copy(heldItems = (heldItems + items).takeLast(Capacity))
        else -> merged(items)
    }

    fun hold(): ConnectionLogState = copy(held = true)

    fun release(): ConnectionLogState = merged(heldItems).copy(held = false, heldItems = emptyList())

    fun clear(): ConnectionLogState = copy(rows = emptyList(), heldItems = emptyList())

    private fun merged(items: List<ConnectionLogItem>): ConnectionLogState {
        val newestLast = ArrayDeque(rows.asReversed())
        var nextId = nextRowId
        items.forEach { item ->
            val repeated = newestLast.lastOrNull()?.repeatedBy(item)
            if (repeated != null) {
                newestLast[newestLast.lastIndex] = repeated
            } else {
                newestLast.addLast(item.toRow(nextId++))
            }
        }
        return copy(rows = newestLast.asReversed().take(Capacity), nextRowId = nextId)
    }

    companion object {
        const val Capacity = 500
    }
}

/** The newest row with [item] counted in, or null when [item] needs a row of its own. */
private fun ConnectionLogLine.repeatedBy(item: ConnectionLogItem): ConnectionLogLine? {
    val next = item.toRow(id)
    return when {
        this is ConnectionLogRow && next is ConnectionLogRow && host == next.host && outbound == next.outbound ->
            copy(timeMillis = next.timeMillis, count = count + 1)
        this is ConnectionLogMessageRow && next is ConnectionLogMessageRow &&
            copyText == next.copyText && level == next.level && source == next.source ->
            copy(timeMillis = next.timeMillis, count = count + 1)
        else -> null
    }
}

private fun ConnectionLogItem.toRow(id: Long): ConnectionLogLine = when (this) {
    is ConnectionLogEntry -> toRow(id)
    is ConnectionLogMessage -> ConnectionLogMessageRow(
        id = id,
        timeMillis = timeMillis,
        level = level,
        source = source,
        copyText = text,
    )
}

private fun ConnectionLogEntry.toRow(id: Long): ConnectionLogRow {
    val isIpv6 = ':' in host
    val isAddress = isIpv6 || host.all { it.isDigit() || it == '.' }
    return ConnectionLogRow(
        id = id,
        timeMillis = timeMillis,
        host = when {
            isIpv6 -> "[$host]:$port"
            isAddress -> "$host:$port"
            else -> host
        },
        isAddress = isAddress,
        outbound = outbound,
        route = when (outbound) {
            XrayTags.DIRECT -> ConnectionLogRoute.Direct
            XrayTags.BLOCK -> ConnectionLogRoute.Block
            else -> ConnectionLogRoute.Proxy
        },
    )
}
