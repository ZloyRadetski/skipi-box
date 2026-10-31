// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeLatencyOperation
import kotlinx.coroutines.Job

/** Keeps Home latency selection and single-run state in the shared operation. */
internal class DesktopProxyHomeLatencyCoordinator<Target>(
    getAllTargets: () -> List<Target>,
    targetId: (Target) -> String,
    getGroupMemberIds: (groupId: String) -> Set<String>?,
    isHostBusy: () -> Boolean,
    launchTest: (targets: List<Target>, onFinished: () -> Unit) -> Job,
    onCancel: () -> Unit,
) {
    private val operation = ProxyHomeLatencyOperation(
        getAllTargets = getAllTargets,
        targetId = targetId,
        getGroupMemberIds = getGroupMemberIds,
        isHostBusy = isHostBusy,
        getModeAndDoneTemplate = { Unit to "" },
        launchTest = { targets, _, _, onFinished -> launchTest(targets, onFinished) },
        onCancel = onCancel,
    )

    val isBusy: Boolean get() = operation.isBusy

    fun testVisibleServers(serverIds: List<String>): Result<Unit> = operation.testVisibleServers(serverIds)

    fun testGroup(groupId: String): Result<Unit> = operation.testGroup(groupId)

    fun cancel(): Result<Unit> = operation.cancel()
}
