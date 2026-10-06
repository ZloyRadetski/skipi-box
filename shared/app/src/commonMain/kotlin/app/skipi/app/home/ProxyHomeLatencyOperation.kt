// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.Job

/**
 * Coordinates a Home latency run independently of server models and runtime APIs.
 * Hosts inject target lookup and execution while shared Home owns validation,
 * single-run policy, completion races, and cancellation state.
 */
class ProxyHomeLatencyOperation<Target, Mode>(
    private val getAllTargets: () -> List<Target>,
    private val targetId: (Target) -> String,
    private val getGroupMemberIds: (groupId: String) -> Set<String>?,
    private val isHostBusy: () -> Boolean,
    private val getModeAndDoneTemplate: () -> Pair<Mode, String>,
    private val launchTest: (
        targets: List<Target>,
        mode: Mode,
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
        if (isBusy) return Result.failure(IllegalStateException("Latency test is already in progress"))
        val requestedIds = serverIds.toHashSet()
        return startTest(getAllTargets().filter { target -> targetId(target) in requestedIds })
    }

    fun testGroup(groupId: String): Result<Unit> {
        if (isBusy) return Result.failure(IllegalStateException("Latency test is already in progress"))
        val memberIds = getGroupMemberIds(groupId)
            ?: return Result.failure(IllegalArgumentException("Unknown group ID: $groupId"))
        val memberIdSet = memberIds.toHashSet()
        val targets = getAllTargets().filter { target -> targetId(target) in memberIdSet }
        return startTest(targets)
    }

    private fun startTest(targets: List<Target>): Result<Unit> {
        val (mode, doneTemplate) = getModeAndDoneTemplate()
        val runId = ++nextRunId
        activeRunId = runId
        val job = launchTest(targets, mode, doneTemplate) {
            if (activeRunId == runId) {
                activeJob = null
                activeRunId = 0L
            }
        }
        // The callback may finish synchronously before launchTest returns.
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