// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import features.logs.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-memory ping cache with debounced persistence: every change is visible at once,
 * while the whole map is written at most once per [debounceMillis] or on [flush].
 */
internal class PingCacheCore(
    private val scope: CoroutineScope,
    private val debounceMillis: Long,
    private val nowMillis: () -> Long,
    private val load: () -> String?,
    private val save: (String) -> Unit,
) {
    @Serializable
    private data class CachedLatency(
        val latencyMillis: Long,
        val recordedAtMillis: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var memo: MutableMap<Int, CachedLatency>? = null
    private var dirty = false
    private var pendingPersist: Job? = null

    fun freshest(serverIds: Collection<Int>): Map<Int, Long> {
        if (serverIds.isEmpty()) return emptyMap()
        val now = nowMillis()
        synchronized(lock) {
            return entries()
                .filterKeys { serverId -> serverId in serverIds }
                .filterValues { entry -> entry.isFresh(now) }
                .mapValues { (_, entry) -> entry.latencyMillis }
        }
    }

    fun record(serverId: Int, latencyMillis: Long) {
        if (latencyMillis < 0) return
        synchronized(lock) {
            val entries = entries()
            entries[serverId] = CachedLatency(latencyMillis = latencyMillis, recordedAtMillis = nowMillis())
            prune(entries)
            markDirtyLocked()
        }
    }

    fun invalidate(serverId: Int) {
        synchronized(lock) {
            if (entries().remove(serverId) != null) markDirtyLocked()
        }
    }

    /** Writes pending changes now. Call when a probe run ends. */
    fun flush() {
        synchronized(lock) {
            pendingPersist?.cancel()
            pendingPersist = null
            persistLocked()
        }
    }

    private fun markDirtyLocked() {
        dirty = true
        if (pendingPersist?.isActive == true) return
        pendingPersist = scope.launch {
            delay(debounceMillis)
            synchronized(lock) {
                pendingPersist = null
                persistLocked()
            }
        }
    }

    private fun persistLocked() {
        if (!dirty) return
        val snapshot = memo ?: return
        dirty = false
        runCatching { save(json.encodeToString(snapshot)) }
            .onFailure { error ->
                dirty = true
                AppLog.warn(LogTag, "Failed to persist ping cache", error)
            }
    }

    private fun entries(): MutableMap<Int, CachedLatency> {
        memo?.let { return it }
        val persisted = load()
            ?.let { payload ->
                runCatching { json.decodeFromString<Map<Int, CachedLatency>>(payload) }
                    .onFailure { error -> AppLog.warn(LogTag, "Failed to decode persisted ping cache", error) }
                    .getOrNull()
            }
            .orEmpty()
        return persisted.toMutableMap().also { memo = it }
    }

    private fun prune(entries: MutableMap<Int, CachedLatency>) {
        if (entries.size <= MaxEntries) return
        entries.entries
            .sortedByDescending { it.value.recordedAtMillis }
            .drop(MaxEntries)
            .map { it.key }
            .forEach { serverId -> entries.remove(serverId) }
    }

    private fun CachedLatency.isFresh(now: Long): Boolean =
        latencyMillis >= 0 && now - recordedAtMillis <= FreshWindowMillis

    private companion object {
        /** Entries older than this are ignored as startup candidates. */
        const val FreshWindowMillis = 24L * 60L * 60L * 1000L

        /** Upper bound on tracked servers so the payload stays small. */
        const val MaxEntries = 2048
        const val LogTag = "ProxyPingResultCache"
    }
}
