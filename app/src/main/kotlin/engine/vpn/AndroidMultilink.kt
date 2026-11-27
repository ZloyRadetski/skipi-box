// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import app.skipi.core.skipicore.Skipicore
import engine.network.AndroidLinkMonitor
import features.logs.AppLog
import features.multilink.MultilinkSettings
import features.multilink.MultilinkSync
import features.multilink.toCoreLinksJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SKIPI Core's link merging on Android, the way the application reaches it. Core and the tunnel live in one process. */
internal object AndroidMultilinkCore {
    /** What the settings page reads; the tunnel service, not the page, applies the settings (see [AndroidMultilink]). */
    val reader = MultilinkSync(
        apply = { Result.success(Unit) },
        readStatus = { runCatching { Skipicore.multilinkStatus() } },
    )

    fun newSync(): MultilinkSync = MultilinkSync(
        apply = { json -> runCatching { Skipicore.multilinkApply(json) } },
        readStatus = { runCatching { Skipicore.multilinkStatus() } },
    )

    fun setLinks(json: String): Result<Unit> = runCatching { Skipicore.multilinkSetLinks(json) }
}

/**
 * Runs link merging for one tunnel: while the tunnel runs it follows the saved settings. The networks are watched
 * (and kept connected) only while merging is on, and Core is told to stop merging when the tunnel stops.
 */
internal class AndroidMultilink(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: Flow<MultilinkSettings>,
) {
    private val sync = AndroidMultilinkCore.newSync()
    private var monitor: AndroidLinkMonitor? = null
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job != null) return
        job = scope.launch {
            settings.distinctUntilChanged().collect { current -> apply(current) }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        stopMonitor()
        // Core stops with the tunnel and its merging goes with it; this also makes the next start apply afresh.
        runCatching { sync.sync(MultilinkSettings(), tunnelRunning = false) }
    }

    private suspend fun apply(current: MultilinkSettings) {
        if (current.enabled) startMonitor()
        withContext(Dispatchers.IO) {
            sync.sync(current, tunnelRunning = true)
                .onFailure { error -> AppLog.warn(LogTag, "Link merging settings were not applied: ${error.message}") }
        }
        if (!current.enabled) stopMonitor()
    }

    @Synchronized
    private fun startMonitor() {
        if (monitor != null) return
        monitor = AndroidLinkMonitor(context) { links ->
            AndroidMultilinkCore.setLinks(links.toCoreLinksJson())
                .onFailure { error -> AppLog.warn(LogTag, "Core refused the links: ${error.message}") }
        }.also(AndroidLinkMonitor::start)
    }

    @Synchronized
    private fun stopMonitor() {
        monitor?.stop()
        monitor = null
    }

    private companion object {
        const val LogTag = "Multilink"
    }
}
