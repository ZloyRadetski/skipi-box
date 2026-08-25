// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.content.Context
import java.io.Closeable

/**
 * Reuses a single [XrayStatsClient] gRPC channel across consecutive polls.
 *
 * Creating a channel per query is expensive (TCP + HTTP/2 handshake on every
 * sample). The session keeps one channel per tunnel runtime and recreates it
 * only when the tunnel restarts (runtime key change) or after repeated query
 * failures that may indicate a stale connection.
 */
internal class XrayStatsClientSession(private val context: Context) : Closeable {
    /** Runtime observed during the latest [withClient] call; null when the tunnel is not running. */
    var lastRuntime: ProxyTrafficStatsRuntime? = null
        private set

    private var client: XrayStatsClient? = null
    private var clientRuntimeKey: String? = null
    private var consecutiveFailures = 0

    /**
     * Runs [block] with a healthy client for the currently running tunnel,
     * or returns null immediately when the tunnel is not running. Failures
     * propagate to the caller and count towards automatic channel recreation.
     */
    suspend fun <T> withClient(block: (XrayStatsClient) -> T): T? {
        val runtime = ProxyTrafficStatsRuntimeStore.read(context)
        if (runtime == null) {
            closeChannel()
            lastRuntime = null
            return null
        }
        lastRuntime = runtime

        val key = "${runtime.listenAddress}:${runtime.port}:${runtime.startedAtElapsedRealtime}"
        val activeClient =
            if (client == null || key != clientRuntimeKey || consecutiveFailures >= RecreateAfterFailures) {
                closeChannel()
                XrayStatsClient(
                    listenAddress = runtime.listenAddress,
                    port = runtime.port,
                    apiTag = runtime.apiTag,
                ).also { created ->
                    client = created
                    clientRuntimeKey = key
                    consecutiveFailures = 0
                }
            } else {
                client!!
            }

        return try {
            block(activeClient).also { consecutiveFailures = 0 }
        } catch (error: Throwable) {
            consecutiveFailures++
            throw error
        }
    }

    override fun close() {
        closeChannel()
        lastRuntime = null
    }

    private fun closeChannel() {
        runCatching { client?.close() }
        client = null
        clientRuntimeKey = null
    }

    private companion object {
        const val RecreateAfterFailures = 3
    }
}
