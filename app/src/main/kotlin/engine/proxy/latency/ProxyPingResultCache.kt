// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import android.content.Context
import androidx.core.content.edit
import features.logs.AndroidAppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persistent cache of measured proxy latencies keyed by plain-server id.
 *
 * In-memory `ProxyServerState.latency` values die with the process, so right
 * after an app restart a balancer would previously pick its startup member by
 * racing blind short TCP probes. This cache keeps the last measured latency
 * for every tested server, letting the startup path go straight to a recently
 * verified member. Xray's Observatory still health-checks members after the
 * tunnel starts, so a stale entry can never trap traffic on a dead server.
 *
 * Only positive measurements are stored; a failed test drops the entry so a
 * dead server cannot be resurrected from stale data.
 */
internal object ProxyPingResultCache {

    @Serializable
    private data class CachedLatency(
        val latencyMillis: Long,
        val recordedAtMillis: Long,
    )

    private const val PreferencesName = "skipi_ping_cache"
    private const val KeyEntries = "entries"

    /** Entries older than this are ignored as startup candidates. */
    private const val FreshWindowMillis = 45L * 60L * 1000L

    /** Upper bound on tracked servers so the payload stays small. */
    private const val MaxEntries = 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val memoLock = Any()
    private var memo: MutableMap<Int, CachedLatency>? = null

    /** Fresh cached latencies restricted to [serverIds], best candidates first for callers to sort. */
    fun freshest(context: Context, serverIds: Collection<Int>): Map<Int, Long> {
        if (serverIds.isEmpty()) return emptyMap()
        val nowMillis = System.currentTimeMillis()
        synchronized(memoLock) {
            return mutableMemo(context)
                .filterKeys { serverId -> serverId in serverIds }
                .filterValues { entry -> entry.isFresh(nowMillis) }
                .mapValues { (_, entry) -> entry.latencyMillis }
        }
    }

    fun record(context: Context, serverId: Int, latencyMillis: Long) {
        if (latencyMillis < 0) return
        synchronized(memoLock) {
            val entries = mutableMemo(context)
            entries[serverId] = CachedLatency(
                latencyMillis = latencyMillis,
                recordedAtMillis = System.currentTimeMillis(),
            )
            pruneLocked(entries)
            persistLocked(context, entries)
        }
    }

    fun invalidate(context: Context, serverId: Int) {
        synchronized(memoLock) {
            val entries = mutableMemo(context)
            if (entries.remove(serverId) != null) {
                persistLocked(context, entries)
            }
        }
    }

    private fun mutableMemo(context: Context): MutableMap<Int, CachedLatency> {
        memo?.let { return it }
        val appContext = context.applicationContext
        val persisted = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(KeyEntries, null)
            ?.let { payload ->
                runCatching { json.decodeFromString<Map<Int, CachedLatency>>(payload) }
                    .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to decode persisted ping cache", error) }
                    .getOrNull()
            }
            .orEmpty()
        return mutableMapOf<Int, CachedLatency>().also { loaded ->
            loaded.putAll(persisted)
            memo = loaded
        }
    }

    private fun persistLocked(context: Context, entries: Map<Int, CachedLatency>) {
        val appContext = context.applicationContext
        runCatching {
            appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .edit { putString(KeyEntries, json.encodeToString(entries)) }
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to persist ping cache", error)
        }
    }

    private fun pruneLocked(entries: MutableMap<Int, CachedLatency>) {
        if (entries.size <= MaxEntries) return
        entries.entries
            .sortedByDescending { it.value.recordedAtMillis }
            .drop(MaxEntries)
            .mapNotNull { entry -> entry.key.takeIf { key -> entries.containsKey(key) } }
            .forEach { serverId -> entries.remove(serverId) }
    }

    private fun CachedLatency.isFresh(nowMillis: Long): Boolean {
        return latencyMillis >= 0 && nowMillis - recordedAtMillis <= FreshWindowMillis
    }

    private const val LogTag = "ProxyPingResultCache"
}
