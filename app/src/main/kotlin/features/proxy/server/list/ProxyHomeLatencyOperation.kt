// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.modes.SubscriptionPingModeHttp
import app.ProxyServerState
import engine.proxy.latency.ProxyServerLatencyTestMode
import kotlinx.coroutines.Job

/** Resolves latency test mode according to configured subscription ping mode. */
internal fun resolveLatencyTestMode(subscriptionPingMode: Int): ProxyServerLatencyTestMode =
    if (subscriptionPingMode == SubscriptionPingModeHttp) {
        ProxyServerLatencyTestMode.RealConnection
    } else {
        ProxyServerLatencyTestMode.TcpConnect
    }

/** Resolves completion template according to test mode. */
internal fun resolveLatencyDoneTemplate(
    mode: ProxyServerLatencyTestMode,
    latencyDoneTemplate: String,
    realConnectionDoneTemplate: String,
): String = if (mode == ProxyServerLatencyTestMode.RealConnection) {
    realConnectionDoneTemplate
} else {
    latencyDoneTemplate
}

/** Resolves target servers for visible servers test. */
internal fun resolveVisibleServerTargets(
    allServers: List<ProxyServerState>,
    requestedIds: Collection<String>,
): List<ProxyServerState> {
    val idSet = requestedIds.mapNotNull(String::toIntOrNull).toSet()
    return allServers.filter { it.id in idSet }
}

/** Resolves target servers for a specific group, using only that group's members. */
internal fun resolveGroupServerTargets(
    allServers: List<ProxyServerState>,
    memberIds: Collection<String>,
): List<ProxyServerState> = resolveVisibleServerTargets(allServers, memberIds)

/**
 * Coordinates latency testing execution, ensuring configured ping modes,
 * single-dispatch busy prevention, and clean cancellation.
 */
internal class ProxyHomeLatencyCoordinator(
    private val getSubscriptionPingMode: () -> Int,
    private val getLatencyDoneTemplate: () -> String,
    private val getRealConnectionDoneTemplate: () -> String,
    private val getAllServers: () -> List<ProxyServerState>,
    private val getGroupMemberIds: (groupId: String) -> Set<String>?,
    private val isHostBusy: () -> Boolean,
    private val launchTest: (
        targets: List<ProxyServerState>,
        mode: ProxyServerLatencyTestMode,
        doneTemplate: String,
        onFinished: () -> Unit,
    ) -> Job,
    private val onCancel: () -> Unit,
) {
    private var activeJob: Job? = null
    private var nextRunId = 0L
    private var activeRunId = 0L

    val isBusy: Boolean
        get() = activeJob?.isActive == true || isHostBusy()

    fun testVisibleServers(serverIds: List<String>): Result<Unit> {
        if (isBusy) {
            return Result.failure(IllegalStateException("Latency test is already in progress"))
        }
        val targets = resolveVisibleServerTargets(getAllServers(), serverIds)
        return startTest(targets)
    }

    fun testGroup(groupIdString: String): Result<Unit> {
        val groupId = groupIdString.toIntOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid group ID: $groupIdString"))
        if (isBusy) {
            return Result.failure(IllegalStateException("Latency test is already in progress"))
        }
        val memberIds = getGroupMemberIds(groupId.toString())
            ?: return Result.failure(IllegalArgumentException("Unknown group ID: $groupIdString"))
        val targets = resolveGroupServerTargets(getAllServers(), memberIds)
        return startTest(targets)
    }

    private fun startTest(targets: List<ProxyServerState>): Result<Unit> {
        val mode = resolveLatencyTestMode(getSubscriptionPingMode())
        val template = resolveLatencyDoneTemplate(
            mode = mode,
            latencyDoneTemplate = getLatencyDoneTemplate(),
            realConnectionDoneTemplate = getRealConnectionDoneTemplate(),
        )
        val runId = ++nextRunId
        activeRunId = runId
        val job = launchTest(targets, mode, template) {
            if (activeRunId == runId) {
                activeJob = null
                activeRunId = 0L
            }
        }
        if (activeRunId == runId) activeJob = job
        return Result.success(Unit)
    }

    fun cancel(): Result<Unit> {
        val job = activeJob
        activeJob = null
        activeRunId = ++nextRunId
        job?.cancel()
        onCancel()
        return Result.success(Unit)
    }
}
