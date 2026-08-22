// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import data.AndroidAppStateStore
import engine.stats.ProxyTrafficStatsRuntimeStore
import engine.stats.XrayStatsClient
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
 */
internal class ProxyWidgetRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val stateStore by lazy { AndroidAppStateStore.get(context) }
    private var pollingJob: Job? = null

    fun start() {
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
                var runtimeKey: String? = null
                var previousTotals = emptyMap<String, XrayTrafficBytes>()
                var previousAtElapsedRealtime = 0L
                var sessionTotals = XrayTrafficBytes()
                while (isActive) {
                    val runtime = ProxyTrafficStatsRuntimeStore.read(context)
                    if (runtime != null) {
                        val key = "${runtime.listenAddress}:${runtime.port}:${runtime.startedAtElapsedRealtime}"
                        if (key != runtimeKey) {
                            runtimeKey = key
                            previousTotals = emptyMap()
                            previousAtElapsedRealtime = 0L
                            sessionTotals = XrayTrafficBytes()
                        }
                        val now = SystemClock.elapsedRealtime()
                        val totals =
                            runCatching {
                                XrayStatsClient(
                                    listenAddress = runtime.listenAddress,
                                    port = runtime.port,
                                    apiTag = runtime.apiTag,
                                ).use { client -> client.queryOutboundTraffic(reset = false) }
                            }.onFailure { error ->
                                AndroidAppLogger.warn(LogTag, "Failed to query traffic for widgets", error)
                            }.getOrDefault(emptyMap())
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
                            runCatching { SkipiWidgetRenderer.updateTraffic(context, sample) }
                        }
                        previousTotals = totals
                        previousAtElapsedRealtime = now
                    }
                    delay(PollIntervalMillis.milliseconds)
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
    }
}
