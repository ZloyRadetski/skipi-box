// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Packets and bytes through the TUN, as Core's hev reports them. */
@Serializable
internal data class TunnelStats(
    val txPackets: Long = 0,
    val txBytes: Long = 0,
    val rxPackets: Long = 0,
    val rxBytes: Long = 0,
) {
    /** The layout of the old TProxyGetStats array: tx packets, tx bytes, rx packets, rx bytes. */
    fun toLongArray(): LongArray = longArrayOf(txPackets, txBytes, rxPackets, rxBytes)
}

private val tunnelStatsJson = Json { ignoreUnknownKeys = true }

/** Parses CoreController.queryTunnelStats(); null for blank or unreadable JSON. */
internal fun parseTunnelStats(json: String): TunnelStats? {
    if (json.isBlank()) return null
    return runCatching { tunnelStatsJson.decodeFromString(TunnelStats.serializer(), json) }.getOrNull()
}
