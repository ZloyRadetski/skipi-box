// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.modes.SubscriptionPingModeHttp
import app.ProxyServerState
import app.skipi.app.home.ProxyHomeLatencyOperation as SharedProxyHomeLatencyOperation
import engine.proxy.latency.ProxyServerLatencyTestMode
import kotlinx.coroutines.Job

/** Resolves the Android latency mode from the persisted subscription-ping preference. */
internal fun resolveLatencyTestMode(subscriptionPingMode: Int): ProxyServerLatencyTestMode =
    if (subscriptionPingMode == SubscriptionPingModeHttp) {
        ProxyServerLatencyTestMode.RealConnection
    } else {
        ProxyServerLatencyTestMode.TcpConnect
    }

/** Selects the completion text that matches the Android latency runtime mode. */
internal fun resolveLatencyDoneTemplate(
    mode: ProxyServerLatencyTestMode,
    latencyDoneTemplate: String,
    realConnectionDoneTemplate: String,
): String = if (mode == ProxyServerLatencyTestMode.RealConnection) {
    realConnectionDoneTemplate
} else {
    latencyDoneTemplate
}

/** Android model/runtime adapter for shared latency run coordination. */
internal class ProxyHomeLatencyCoordinator(
    getSubscriptionPingMode: () -> Int,
    getLatencyDoneTemplate: () -> String,
    getRealConnectionDoneTemplate: () -> String,
    getAllServers: () -> List<ProxyServerState>,
    getGroupMemberIds: (groupId: String) -> Set<String>?,
    isHostBusy: () -> Boolean,
    launchTest: (
        targets: List<ProxyServerState>,
        mode: ProxyServerLatencyTestMode,
        doneTemplate: String,
        onFinished: () -> Unit,
    ) -> Job,
    onCancel: () -> Unit,
) {
    private val operation = SharedProxyHomeLatencyOperation(
        getAllTargets = getAllServers,
        targetId = { it.id.toString() },
        getGroupMemberIds = getGroupMemberIds,
        isHostBusy = isHostBusy,
        getModeAndDoneTemplate = {
            val mode = resolveLatencyTestMode(getSubscriptionPingMode())
            mode to resolveLatencyDoneTemplate(
                mode = mode,
                latencyDoneTemplate = getLatencyDoneTemplate(),
                realConnectionDoneTemplate = getRealConnectionDoneTemplate(),
            )
        },
        launchTest = launchTest,
        onCancel = onCancel,
    )

    val isBusy: Boolean get() = operation.isBusy

    fun testVisibleServers(serverIds: List<String>): Result<Unit> = operation.testVisibleServers(
        serverIds.mapNotNull(String::toIntOrNull).map(Int::toString),
    )

    fun testGroup(groupId: String): Result<Unit> {
        if (groupId.toIntOrNull() == null) {
            return Result.failure(IllegalArgumentException("Invalid group ID: $groupId"))
        }
        return operation.testGroup(groupId.toInt().toString())
    }

    fun cancel(): Result<Unit> = operation.cancel()
}
