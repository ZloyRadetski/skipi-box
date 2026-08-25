// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import data.AndroidAppStateStore
import engine.stats.ProxyTrafficStatsRuntime
import engine.stats.XrayStatsClientSession
import engine.stats.XrayTrafficBytes
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps home screen widgets in sync while the app process is alive:
 * re-renders them on relevant state changes and polls live traffic speeds
 * from the Xray Stats API whenever the tunnel is running.
 *
 * Battery care: polling reuses a single stats channel instead of opening a
 * new one per tick, slows down when the screen is off, and does not push
 * RemoteViews updates while the screen is off (launcher stays asleep).
 */
internal class ProxyWidgetRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val stateStore by lazy { AndroidAppStateStore.get(context) }
    private val powerManager by lazy { context.getSystemService(PowerManager::class.java) }
    private var pollingJob: Job? = null

    @Volatile
    private var isScreenInteractive = true

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> isScreenInteractive = true
                Intent.ACTION_SCREEN_OFF -> isScreenInteractive = false
            }
        }
    }

    fun start() {
        isScreenInteractive = powerManager?.isInteractive ?: true
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                context,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        scope.launch {
            stateStore.state
                .map { state -> WidgetStateKey(state.proxyRunning, state.selectedProxyServerId, state.runMode) }
                .distinctUntilChanged()
                .collect { key ->
                    if (!key.running || !hasWidgets()) {
                        pollingJob?.cancel()
                        pollingJob = null
                        if (!key.running) WidgetSpeedStore.clear(context)
                    }
                    runCatching { SkipiWidgetRenderer.renderAll(context) }.onFailure { error ->
                        AndroidAppLogger.warn(LogTag, "Failed to render SKIPI home screen widgets", error)
                    }
                    if (key.running && hasWidgets()) startPolling()
                }
        }
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob =
            scope.launch(Dispatchers.IO) {
                // One reusable stats channel for the whole polling loop.
                XrayStatsClientSession(context).use { session ->
                    var previousRuntime: ProxyTrafficStatsRuntime? = null
                    var previousTotals = emptyMap<String, XrayTrafficBytes>()
                    var previousAtElapsedRealtime = 0L
                    var sessionTotals = XrayTrafficBytes()
                    while (isActive) {
                        val interactive = isScreenInteractive
                        val totals =
                            runCatching {
                                session.withClient { client -> client.queryOutboundTraffic(reset = false) }
                            }.onFailure { error ->
                                AndroidAppLogger.warn(LogTag, "Failed to query traffic for widgets", error)
                            }.getOrNull() ?: emptyMap<String, XrayTrafficBytes>()
                        val runtime = session.lastRuntime
                        if (runtime != null) {
                            if (runtime != previousRuntime) {
                                previousRuntime = runtime
                                previousTotals = emptyMap()
                                previousAtElapsedRealtime = 0L
                                sessionTotals = XrayTrafficBytes()
                            }
                            val now = SystemClock.elapsedRealtime()
                            if (previousAtElapsedRealtime > 0L && totals.isNotEmpty()) {
                                var uplinkDelta = 0L
                                var downlinkDelta = 0L
                                totals.forEach { (tag, bytes) ->
                                    val before = previousTotals[tag] ?: XrayTrafficBytes()
                                    uplinkDelta += (bytes.uplink - before.uplink).coerceAtLeast(0L)
                                    downlinkDelta += (bytes.downlink - before.downlink).coerceAtLeast(0L)
                                }
                                sessionTotals += XrayTrafficBytes(uplink = uplinkDelta, downlink = downlinkDelta)
                                val elapsedSeconds =
                                    ((now - previousAtElapsedRealtime).coerceAtLeast(1L)).toDouble() / 1000.0
                                val sample =
                                    WidgetSpeedSample(
                                        uplinkBytesPerSecond = (uplinkDelta / elapsedSeconds).toLong(),
                                        downlinkBytesPerSecond = (downlinkDelta / elapsedSeconds).toLong(),
                                        totalUplinkBytes = sessionTotals.uplink,
                                        totalDownlinkBytes = sessionTotals.downlink,
                                        updatedAtElapsedRealtime = now,
                                    )
                                WidgetSpeedStore.write(context, sample)
                                if (interactive) {
                                    runCatching { SkipiWidgetRenderer.updateTraffic(context, sample) }
                                }
                            }
                            previousTotals = totals
                            previousAtElapsedRealtime = now
                        } else {
                            // Tunnel stopped: reset the baseline until it starts again.
                            previousRuntime = null
                            previousTotals = emptyMap()
                            previousAtElapsedRealtime = 0L
                            sessionTotals = XrayTrafficBytes()
                        }
                        val pollIntervalMillis =
                            if (interactive) PollIntervalMillis else ScreenOffPollIntervalMillis
                        delay(pollIntervalMillis.milliseconds)
                    }
                }
            }
    }

    private fun hasWidgets(): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        val component = ComponentName(context, SkipiWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private data class WidgetStateKey(
        val running: Boolean,
        val selectedProxyServerId: Int,
        val runMode: Int,
    )

    private companion object {
        private const val LogTag = "ProxyWidgetRuntime"
        private const val PollIntervalMillis = 2_000L
        private const val ScreenOffPollIntervalMillis = 15_000L
    }
}
