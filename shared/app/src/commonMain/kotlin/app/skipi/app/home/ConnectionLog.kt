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

/** One connection the running session accepted, as SKIPI Core reports it. */
data class ConnectionLogEntry(
    val seq: Long,
    val timeMillis: Long,
    val host: String,
    val port: Int,
    val network: String,
    val outbound: String,
)

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

/** A visible line of the Home connection log; [count] repeats of one host and route share it. */
data class ConnectionLogRow(
    val id: Long,
    val timeMillis: Long,
    val host: String,
    val isAddress: Boolean,
    val outbound: String,
    val route: ConnectionLogRoute,
    val count: Int = 1,
)

/**
 * What the Home connection log shows. While [held] the visible rows stay exactly as they are, so a
 * line can be read, clicked or scrolled to; what arrives meanwhile joins on [release].
 */
data class ConnectionLogState(
    val rows: List<ConnectionLogRow> = emptyList(),
    val held: Boolean = false,
    private val heldEntries: List<ConnectionLogEntry> = emptyList(),
) {
    val heldCount: Int get() = heldEntries.size

    fun append(entries: List<ConnectionLogEntry>): ConnectionLogState = when {
        entries.isEmpty() -> this
        held -> copy(heldEntries = (heldEntries + entries).takeLast(Capacity))
        else -> copy(rows = merged(rows, entries))
    }

    fun hold(): ConnectionLogState = copy(held = true)

    fun release(): ConnectionLogState = copy(rows = merged(rows, heldEntries), held = false, heldEntries = emptyList())

    fun clear(): ConnectionLogState = copy(rows = emptyList(), heldEntries = emptyList())

    companion object {
        const val Capacity = 500
    }
}

private fun merged(rows: List<ConnectionLogRow>, entries: List<ConnectionLogEntry>): List<ConnectionLogRow> {
    val newestLast = ArrayDeque(rows.asReversed())
    entries.forEach { entry ->
        val row = entry.toRow()
        val newest = newestLast.lastOrNull()
        if (newest != null && newest.host == row.host && newest.outbound == row.outbound) {
            newestLast[newestLast.lastIndex] = newest.copy(timeMillis = row.timeMillis, count = newest.count + 1)
        } else {
            newestLast.addLast(row)
        }
    }
    return newestLast.asReversed().take(ConnectionLogState.Capacity)
}

private fun ConnectionLogEntry.toRow(): ConnectionLogRow {
    val isIpv6 = ':' in host
    val isAddress = isIpv6 || host.all { it.isDigit() || it == '.' }
    return ConnectionLogRow(
        id = seq,
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
