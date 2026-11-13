// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Persistent cache of measured proxy latencies keyed by plain-server id.
 *
 * In-memory `ProxyServerState.latency` values die with the process, so right
 * after an app restart a balancer would previously pick its startup member by
 * racing blind short TCP probes. This cache keeps the last measured latency
 * for TCP-compatible servers only. UDP/QUIC/WebRTC members are restored from
 * the separate real-traffic cache instead of being mistaken for TCP endpoints.
 *
 * Only positive measurements are stored; a failed test drops the entry so a
 * dead server cannot be resurrected from stale data.
 */
internal object ProxyPingResultCache {
    private const val PreferencesName = "skipi_ping_cache"
    private const val KeyEntries = "entries"
    private const val PersistDebounceMillis = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var core: PingCacheCore? = null

    /** Fresh cached latencies restricted to [serverIds], best candidates first for callers to sort. */
    fun freshest(context: Context, serverIds: Collection<Int>): Map<Int, Long> =
        core(context).freshest(serverIds)

    /** Updates the in-memory cache at once; the preferences write is debounced, see [flush]. */
    fun record(context: Context, serverId: Int, latencyMillis: Long) {
        core(context).record(serverId, latencyMillis)
    }

    fun invalidate(context: Context, serverId: Int) {
        core(context).invalidate(serverId)
    }

    /** Writes pending changes now; call when a latency run ends. */
    fun flush(context: Context) {
        core(context).flush()
    }

    private fun core(context: Context): PingCacheCore {
        core?.let { return it }
        return synchronized(this) {
            core ?: createCore(context.applicationContext).also { core = it }
        }
    }

    private fun createCore(appContext: Context): PingCacheCore {
        val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        return PingCacheCore(
            scope = scope,
            debounceMillis = PersistDebounceMillis,
            nowMillis = System::currentTimeMillis,
            load = { preferences.getString(KeyEntries, null) },
            save = { payload -> preferences.edit { putString(KeyEntries, payload) } },
        )
    }
}
