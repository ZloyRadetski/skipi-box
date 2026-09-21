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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import platform.CoreTrafficBytes
import platform.CoreTrafficSnapshot
import platform.aggregateCoreInboundTraffic
import platform.coreTrafficStatsPollIntervalMillis

/**
 * One immutable result of the only native traffic-counter read performed for a
 * running tunnel. UI, the notification and widgets share this value instead of
 * each parsing a SkipiCore snapshot independently.
 */
internal data class CoreTrafficStatsSample(
    val runtime: ProxyTrafficStatsRuntime,
    val snapshot: CoreTrafficSnapshot,
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
    private val activeLeases = mutableMapOf<Long, Long?>()
    private val mutableSamples = MutableStateFlow<CoreTrafficStatsSample?>(null)
    private val mutablePollingPolicyVersion = MutableStateFlow(0L)

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
    fun acquire(
        context: Context,
        requestedRefreshIntervalMillis: Long? = null,
    ): CoreTrafficStatsLease {
        val applicationContext = context.applicationContext
        val normalizedRequestedInterval = requestedRefreshIntervalMillis
            ?.coerceAtLeast(MinimumRequestedRefreshIntervalMillis)
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
            activeLeases[id] = normalizedRequestedInterval
            signalPollingPolicyChangedLocked()
            startPollingIfNeededLocked()
            id
        }
        return CoreTrafficStatsLease(leaseId)
    }

    private fun release(leaseId: Long) {
        synchronized(lock) {
            if (!activeLeases.containsKey(leaseId)) return
            activeLeases.remove(leaseId)
            signalPollingPolicyChangedLocked()
            if (activeLeases.isEmpty()) {
                stopPollingLocked(clearSample = true)
            }
        }
    }

    private fun updateRequestedRefreshInterval(
        leaseId: Long,
        requestedRefreshIntervalMillis: Long?,
    ) {
        val normalizedRequestedInterval = requestedRefreshIntervalMillis
            ?.coerceAtLeast(MinimumRequestedRefreshIntervalMillis)
        synchronized(lock) {
            if (!activeLeases.containsKey(leaseId) || activeLeases[leaseId] == normalizedRequestedInterval) return
            activeLeases[leaseId] = normalizedRequestedInterval
            // Interrupt the current delay rather than leaving a newly selected
            // one-second cadence waiting behind an old ten-second interval.
            signalPollingPolicyChangedLocked()
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
                val inboundTotals = snapshot.inbound
                    .aggregateCoreInboundTraffic(xrayTrafficExcludedInboundTags())
                    .toXrayTrafficBytes()
                val previousInbound = previousInboundTotals.aggregateInboundTraffic()
                val inboundDelta = XrayTrafficBytes(
                    uplink = (inboundTotals.uplink - previousInbound.uplink).coerceAtLeast(0L),
                    downlink = (inboundTotals.downlink - previousInbound.downlink).coerceAtLeast(0L),
                )
                hasMeaningfulTraffic =
                    inboundDelta.uplink + inboundDelta.downlink >= MinimumMeaningfulTrafficDeltaBytes
                activeOutboundTag = snapshot.outbound
                    .toXrayTrafficBytes()
                    .maxTrafficDeltaComparedTo(
                        previous = previousOutboundTotals,
                        currentActiveTag = activeOutboundTag,
                    )
                previousInboundTotals = snapshot.inbound.toXrayTrafficBytes()
                previousOutboundTotals = snapshot.outbound.toXrayTrafficBytes()
                if (!isCurrent(token)) return
                mutableSamples.value = CoreTrafficStatsSample(
                    runtime = activeRuntime,
                    snapshot = snapshot,
                    inboundDelta = inboundDelta,
                    activeOutboundTag = activeOutboundTag,
                    sampledAtElapsedRealtime = SystemClock.elapsedRealtime(),
                )
            }

            val pollingPolicy = currentPollingPolicy()
            awaitNextPoll(
                delayMillis = coreTrafficStatsPollIntervalMillis(
                    isScreenInteractive = powerManager?.isInteractive ?: true,
                    hasMeaningfulTraffic = hasMeaningfulTraffic,
                    requestedRefreshIntervalMillis = pollingPolicy.requestedRefreshIntervalMillis,
                    hasDefaultFrequencyConsumer = pollingPolicy.hasDefaultFrequencyConsumer,
                ),
                observedPolicyVersion = pollingPolicy.version,
            )
        }
    }

    private fun currentPollingPolicy(): PollingPolicy = synchronized(lock) {
        PollingPolicy(
            version = mutablePollingPolicyVersion.value,
            requestedRefreshIntervalMillis = activeLeases.values.filterNotNull().minOrNull(),
            hasDefaultFrequencyConsumer = activeLeases.values.any { interval -> interval == null },
        )
    }

    private fun signalPollingPolicyChangedLocked() {
        mutablePollingPolicyVersion.value += 1L
    }

    private suspend fun awaitNextPoll(
        delayMillis: Long,
        observedPolicyVersion: Long,
    ) {
        withTimeoutOrNull(delayMillis) {
            mutablePollingPolicyVersion.first { version -> version != observedPolicyVersion }
        }
    }

    private fun isCurrent(token: Long): Boolean = synchronized(lock) { generation == token }

    internal class CoreTrafficStatsLease(
        private val leaseId: Long,
    ) : Closeable {
        private val released = AtomicBoolean(false)

        fun updateRequestedRefreshIntervalMillis(requestedRefreshIntervalMillis: Long?) {
            if (!released.get()) {
                CoreTrafficStatsSampler.updateRequestedRefreshInterval(
                    leaseId = leaseId,
                    requestedRefreshIntervalMillis = requestedRefreshIntervalMillis,
                )
            }
        }

        override fun close() {
            if (released.compareAndSet(false, true)) {
                CoreTrafficStatsSampler.release(leaseId)
            }
        }
    }

    private const val LogTag = "CoreTrafficStatsSampler"
    private const val MaxConsecutiveFailures = 5
    private const val MinimumRequestedRefreshIntervalMillis = 1_000L

    private data class PollingPolicy(
        val version: Long,
        val requestedRefreshIntervalMillis: Long?,
        val hasDefaultFrequencyConsumer: Boolean,
    )
}

private fun CoreTrafficBytes.toXrayTrafficBytes(): XrayTrafficBytes = XrayTrafficBytes(
    uplink = uplink,
    downlink = downlink,
)

private fun Map<String, CoreTrafficBytes>.toXrayTrafficBytes(): Map<String, XrayTrafficBytes> =
    mapValues { (_, bytes) -> bytes.toXrayTrafficBytes() }
