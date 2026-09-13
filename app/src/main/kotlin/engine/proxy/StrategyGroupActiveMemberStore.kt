// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import android.content.Context
import androidx.core.content.edit
import features.logs.AndroidAppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Remembers the concrete member that actually carried traffic for an automatic
 * strategy group.  Unlike the ping cache, this is never populated by a bare
 * TCP socket check, so a UDP-only transport cannot be promoted to startup
 * fallback merely because another service accepts TCP on the same port.
 */
internal object StrategyGroupActiveMemberStore {
    @Serializable
    private data class Entry(
        val memberId: Int,
        val recordedAtMillis: Long,
    )

    /** Returns a recent real-traffic member only while it still belongs to the group. */
    fun lastActiveMember(
        context: Context,
        strategyGroupId: Int,
        validMemberIds: Collection<Int>,
    ): Int? {
        if (strategyGroupId < 0 || validMemberIds.isEmpty()) return null
        val nowMillis = System.currentTimeMillis()
        synchronized(lock) {
            val entries = mutableEntries(context)
            val entry = entries[strategyGroupId]
            val isValid = entry != null &&
                entry.memberId in validMemberIds &&
                nowMillis - entry.recordedAtMillis in 0..FreshWindowMillis
            if (!isValid) {
                if (entries.remove(strategyGroupId) != null) {
                    persist(context, entries)
                }
                return null
            }
            return entry.memberId
        }
    }

    /** Records a member only after Xray's outbound counters observed real traffic through it. */
    fun record(
        context: Context,
        strategyGroupId: Int,
        memberId: Int,
    ) {
        if (strategyGroupId < 0 || memberId < 0) return
        synchronized(lock) {
            val entries = mutableEntries(context)
            val nowMillis = System.currentTimeMillis()
            entries[strategyGroupId]
                ?.takeIf { entry ->
                    entry.memberId == memberId &&
                        nowMillis - entry.recordedAtMillis in 0 until RefreshIntervalMillis
                }
                ?.let { return }
            entries[strategyGroupId] = Entry(
                memberId = memberId,
                recordedAtMillis = nowMillis,
            )
            prune(entries)
            persist(context, entries)
        }
    }

    private fun mutableEntries(context: Context): MutableMap<Int, Entry> {
        memo?.let { return it }
        val appContext = context.applicationContext
        val persisted = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(KeyEntries, null)
            ?.let { payload ->
                runCatching { json.decodeFromString<Map<Int, Entry>>(payload) }
                    .onFailure { error -> AndroidAppLogger.warn(LogTag, "Failed to decode active balancer member cache", error) }
                    .getOrNull()
            }
            .orEmpty()
        return persisted.toMutableMap().also { loaded -> memo = loaded }
    }

    private fun persist(context: Context, entries: Map<Int, Entry>) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .edit { putString(KeyEntries, json.encodeToString(entries)) }
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to persist active balancer member cache", error)
        }
    }

    private fun prune(entries: MutableMap<Int, Entry>) {
        if (entries.size <= MaxEntries) return
        entries.entries
            .sortedByDescending { (_, entry) -> entry.recordedAtMillis }
            .drop(MaxEntries)
            .map { (groupId, _) -> groupId }
            .forEach(entries::remove)
    }

    private const val PreferencesName = "strategy_group_active_members"
    private const val KeyEntries = "entries"
    private const val FreshWindowMillis = 24L * 60L * 60L * 1_000L
    private const val RefreshIntervalMillis = 5L * 60L * 1_000L
    private const val MaxEntries = 512
    private const val LogTag = "StrategyGroupActiveMemberStore"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var memo: MutableMap<Int, Entry>? = null
}
