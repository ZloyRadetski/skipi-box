// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import engine.vpn.SkipiCoreRuntime
import features.logs.AndroidAppLogger
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One immutable result of the only native traffic-counter read performed for a
 * running tunnel. UI, the notification and widgets share this value instead of
 * each parsing a SkipiCore snapshot independently.
 */
internal data class CoreTrafficStatsSample(
    val runtime: ProxyTrafficStatsRuntime,
    val snapshot: CoreTrafficStatsSnapshot,
    val inboundDelta: XrayTrafficBytes,
    val activeOutboundTag: String?,
    val sampledAtElapsedRealtime: Long,
)

/**
 * Process-wide, demand-driven source of tunnel counters.
 *
 * A lease represents a visible or otherwise meaningful consumer. No native
 * polling is kept alive merely because a VPN tunnel exists in the background.
 */
internal object CoreTrafficStatsSampler {
    private val lock = Any()
    private val samplingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeLeases = mutableSetOf<Long>()
    private val mutableSamples = MutableStateFlow<CoreTrafficStatsSample?>(null)

    val samples: StateFlow<CoreTrafficStatsSample?> = mutableSamples.asStateFlow()

    private var appContext: Context? = null
    private var runtime: ProxyTrafficStatsRuntime? = null
    private var pollingJob: Job? = null
    private var generation = 0L
    private var nextLeaseId = 0L

    /** Updates the tunnel being sampled without itself creating background work. */
    fun reconcile(
        context: Context,
        runtime: ProxyTrafficStatsRuntime?,
    ) {
        synchronized(lock) {
            appContext = context.applicationContext
            if (this.runtime != runtime) {
                this.runtime = runtime
                stopPollingLocked(clearSample = true)
            }
            startPollingIfNeededLocked()
        }
    }

    /**
     * Acquires one consumer lease. The caller must close it when its screen,
     * notification or widget is no longer active.
     */
    fun acquire(context: Context): Closeable {
        val applicationContext = context.applicationContext
        // Rehydrate the runtime after process recreation. Reading preferences
        // here is intentionally one-off; the sampler loop never polls them.
        val storedRuntime = ProxyTrafficStatsRuntimeStore.read(applicationContext)
        val leaseId = synchronized(lock) {
            appContext = applicationContext
            if (runtime != storedRuntime) {
                runtime = storedRuntime
                stopPollingLocked(clearSample = true)
            }
            val id = ++nextLeaseId
            activeLeases += id
            startPollingIfNeededLocked()
            id
        }
        return CoreTrafficStatsLease(leaseId)
    }

    private fun release(leaseId: Long) {
        synchronized(lock) {
            if (!activeLeases.remove(leaseId)) return
            if (activeLeases.isEmpty()) {
                stopPollingLocked(clearSample = true)
            }
        }
    }

    private fun startPollingIfNeededLocked() {
        val currentContext = appContext ?: return
        val currentRuntime = runtime
        if (activeLeases.isEmpty() || currentRuntime == null || currentRuntime.paused) {
            return
        }
        if (pollingJob?.isActive == true) return

        val token = ++generation
        pollingJob = samplingScope.launch {
            poll(
                context = currentContext,
                activeRuntime = currentRuntime,
                token = token,
            )
        }
    }

    private fun stopPollingLocked(clearSample: Boolean) {
        generation += 1L
        pollingJob?.cancel()
        pollingJob = null
        if (clearSample) {
            mutableSamples.value = null
        }
    }

    private suspend fun poll(
        context: Context,
        activeRuntime: ProxyTrafficStatsRuntime,
        token: Long,
    ) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        var previousInboundTotals = emptyMap<String, XrayTrafficBytes>()
        var previousOutboundTotals = emptyMap<String, XrayTrafficBytes>()
        var activeOutboundTag: String? = null
        var consecutiveFailures = 0

        while (currentCoroutineContext().isActive && isCurrent(token)) {
            var hasMeaningfulTraffic = false
            val snapshot = runCatching {
                requireNotNull(SkipiCoreRuntime.queryTrafficStats()) {
                    "SKIPI Core traffic counters are unavailable"
                }
            }.onFailure { error ->
                consecutiveFailures += 1
                if (consecutiveFailures == 1 || consecutiveFailures == MaxConsecutiveFailures) {
                    AndroidAppLogger.warn(
                        LogTag,
                        "Failed to query shared SKIPI Core traffic stats (attempt $consecutiveFailures)",
                        error,
                    )
                }
                if (consecutiveFailures >= MaxConsecutiveFailures && isCurrent(token)) {
                    // A stale route label is worse than an empty one, and the
                    // next low-frequency retry can restore it without waking a
                    // separate poller in every consumer.
                    mutableSamples.value = null
                }
            }.getOrNull()

            if (snapshot != null) {
                consecutiveFailures = 0
                val inboundTotals = snapshot.inbound.aggregateInboundTraffic()
                val previousInbound = previousInboundTotals.aggregateInboundTraffic()
                val inboundDelta = XrayTrafficBytes(
                    uplink = (inboundTotals.uplink - previousInbound.uplink).coerceAtLeast(0L),
                    downlink = (inboundTotals.downlink - previousInbound.downlink).coerceAtLeast(0L),
                )
                hasMeaningfulTraffic =
                    inboundDelta.uplink + inboundDelta.downlink >= MinimumMeaningfulTrafficDeltaBytes
                activeOutboundTag = snapshot.outbound.maxTrafficDeltaComparedTo(
                    previous = previousOutboundTotals,
                    currentActiveTag = activeOutboundTag,
                )
                previousInboundTotals = snapshot.inbound
                previousOutboundTotals = snapshot.outbound
                if (!isCurrent(token)) return
                mutableSamples.value = CoreTrafficStatsSample(
                    runtime = activeRuntime,
                    snapshot = snapshot,
                    inboundDelta = inboundDelta,
                    activeOutboundTag = activeOutboundTag,
                    sampledAtElapsedRealtime = SystemClock.elapsedRealtime(),
                )
            }

            delay(
                coreTrafficStatsPollIntervalMillis(
                    isScreenInteractive = powerManager?.isInteractive ?: true,
                    hasMeaningfulTraffic = hasMeaningfulTraffic,
                ),
            )
        }
    }

    private fun isCurrent(token: Long): Boolean = synchronized(lock) { generation == token }

    private class CoreTrafficStatsLease(
        private val leaseId: Long,
    ) : Closeable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) {
                CoreTrafficStatsSampler.release(leaseId)
            }
        }
    }

    private const val LogTag = "CoreTrafficStatsSampler"
    private const val MaxConsecutiveFailures = 5
}

/**
 * Keeps immediate feedback while traffic is flowing, but does not wake the CPU
 * at one-second cadence for an idle screen or for a pocketed device.
 */
internal fun coreTrafficStatsPollIntervalMillis(
    isScreenInteractive: Boolean,
    hasMeaningfulTraffic: Boolean,
): Long = when {
    !isScreenInteractive -> CoreTrafficStatsScreenOffPollIntervalMillis
    hasMeaningfulTraffic -> CoreTrafficStatsActivePollIntervalMillis
    else -> CoreTrafficStatsIdlePollIntervalMillis
}

internal const val CoreTrafficStatsActivePollIntervalMillis = 2_000L
internal const val CoreTrafficStatsIdlePollIntervalMillis = 5_000L
internal const val CoreTrafficStatsScreenOffPollIntervalMillis = 30_000L
