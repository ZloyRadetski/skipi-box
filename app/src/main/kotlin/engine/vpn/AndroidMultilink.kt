// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import app.skipi.core.skipicore.Skipicore
import engine.network.AndroidLinkMonitor
import features.logs.AppLog
import features.multilink.MultilinkController
import features.multilink.MultilinkLinksReporter
import features.multilink.MultilinkSettings
import features.multilink.MultilinkSync
import features.multilink.toCoreLinksJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
 * Runs link merging for one tunnel: while the tunnel runs it follows the saved settings ([MultilinkController]). The
 * networks are watched (and kept connected) only after Core accepted merging being on, and Core is told to stop
 * merging when the tunnel stops.
 */
internal class AndroidMultilink(
    private val context: Context,
    scope: CoroutineScope,
    settings: Flow<MultilinkSettings>,
) {
    private val sync = AndroidMultilinkCore.newSync()
    private val controller = MultilinkController(
        scope = scope,
        sync = sync,
        settings = settings,
        tunnelRunning = flowOf(true),
        ioDispatcher = Dispatchers.IO,
        onResult = { current, running, result -> onApplied(current, running, result) },
    )
    private var monitor: AndroidLinkMonitor? = null
    private var reporter: MultilinkLinksReporter? = null
    private var stopped = false

    fun start() = controller.start()

    fun stop() {
        synchronized(this) {
            stopped = true
            stopMonitor()
        }
        // Core stops with the tunnel and its merging goes with it; this also makes the next start apply afresh. It stays
        // synchronous so Core is certain to be told before the tunnel goes; a failure makes the next sync apply again.
        controller.stop().onFailure { error -> AppLog.warn(LogTag, "Link merging was not stopped: ${error.message}") }
    }

    private fun onApplied(current: MultilinkSettings, running: Boolean, result: Result<Unit>) {
        result.onFailure { error -> AppLog.warn(LogTag, "Link merging settings were not applied: ${error.message}") }
        if (result.isSuccess && !sync.unsupported && current.enabled && running) startMonitor() else stopMonitor()
    }

    @Synchronized
    private fun startMonitor() {
        if (stopped || monitor != null) return
        val newReporter = MultilinkLinksReporter { links ->
            AndroidMultilinkCore.setLinks(links.toCoreLinksJson())
                .onFailure { error -> AppLog.warn(LogTag, "Core refused the links: ${error.message}") }
        }
        val newMonitor = AndroidLinkMonitor(context, newReporter::report)
        if (newMonitor.start()) {
            reporter = newReporter
            monitor = newMonitor
        }
    }

    @Synchronized
    private fun stopMonitor() {
        reporter?.close()
        reporter = null
        monitor?.stop()
        monitor = null
    }

    private companion object {
        const val LogTag = "Multilink"
    }
}
