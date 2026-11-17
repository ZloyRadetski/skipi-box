// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy

import app.ProxyServerLatencyTesting
import app.ProxyServerState
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The ping each server showed on Home, kept across restarts. The text is what the list displays (a number of
 * milliseconds or a failure marker), so after a restart the list, and a sort by ping, look as they did when the
 * app was closed instead of starting empty.
 */
object DisplayedLatencies {
    private val serializer = MapSerializer(Int.serializer(), String.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    /** Fills the servers that show no ping yet; a ping measured since the start is never replaced. */
    fun restore(servers: List<ProxyServerState>, saved: Map<Int, String>): List<ProxyServerState> {
        if (saved.isEmpty()) return servers
        return servers.map { server ->
            val text = saved[server.id]
            if (server.latency.isBlank() && !text.isNullOrBlank()) server.copy(latency = text) else server
        }
    }

    /** What is worth keeping: a shown ping, never the "testing" marker or a blank. */
    fun forPersistence(servers: List<ProxyServerState>): Map<Int, String> =
        servers.filter { it.latency.isNotBlank() && it.latency != ProxyServerLatencyTesting }
            .associate { it.id to it.latency }

    fun encode(latencies: Map<Int, String>): String = json.encodeToString(serializer, latencies)

    /** Nothing saved, or text that cannot be read, means no pings. */
    fun decode(payload: String?): Map<Int, String> {
        if (payload.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyMap())
    }
}
