// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.content.Context
import java.io.Closeable

/**
 * Compatibility accessor for consumers that still need the current counters.
 * It never queries SkipiCore itself: all native reads belong to
 * [CoreTrafficStatsSampler].
 */
internal class CoreTrafficStatsSession(context: Context) : Closeable {
    private val appContext = context.applicationContext

    /** Runtime observed during the latest [querySnapshot] call; null when stopped. */
    var lastRuntime: ProxyTrafficStatsRuntime? = null
        private set

    fun querySnapshot(): CoreTrafficStatsSnapshot? {
        val sample = CoreTrafficStatsSampler.samples.value
            ?.takeIf { current -> current.runtime == ProxyTrafficStatsRuntimeStore.read(appContext) }
        lastRuntime = sample?.runtime
        return sample?.snapshot
    }

    override fun close() {
        lastRuntime = null
    }
}
