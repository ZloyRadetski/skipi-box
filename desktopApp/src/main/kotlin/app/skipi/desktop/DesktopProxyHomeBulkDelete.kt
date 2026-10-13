// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.proxy.runSingleProxyServerDeleteWhenRequired
import app.skipi.app.proxy.runSubscriptionGroupDeleteWhenRequired
import app.skipi.app.proxy.ProxyServerStopOutcome
import app.skipi.app.proxy.runProxyServerBulkDeleteWhenRequired
import app.skipi.app.store.SharedApplicationActionOutcome
import features.proxy.server.usecase.ProxyServerGroupCleanup
import features.proxy.server.usecase.ProxyServerGroupCleanupRecord

/** Desktop host adapter for Android Home's distinct one-row deletion ordering. */
internal suspend fun runDesktopProxyHomeSingleDelete(
    serverId: Int,
    selectedServerId: Int?,
    tunnelRunning: Boolean,
    serviceOperationInProgress: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    dispatchDeleteIfPresent: () -> Boolean,
    applyStoppedState: () -> Unit,
    onDeleted: suspend () -> Unit,
    onMissingServer: suspend () -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    runSingleProxyServerDeleteWhenRequired(
        serverId = serverId,
        selectedServerId = selectedServerId,
        serviceRunning = tunnelRunning,
        serviceOperationInProgress = serviceOperationInProgress,
        stopService = stopService,
        dispatchDeleteIfPresent = dispatchDeleteIfPresent,
        applyStopState = applyStoppedState,
        onDeleted = onDeleted,
        onMissingServer = onMissingServer,
        onStopFailed = onStopFailed,
    )
}

/** Desktop host adapter for Android Home's stop-sensitive subscription-group deletion. */
internal suspend fun runDesktopProxyHomeSubscriptionGroupDelete(
    groupServerIds: Set<Int>,
    selectedServerId: Int?,
    tunnelRunning: Boolean,
    serviceOperationInProgress: Boolean,
    groupIsBuiltIn: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    deleteGroupIfPresent: suspend () -> Boolean,
    applyStoppedState: () -> Unit,
    onDeleted: suspend () -> Unit,
    onMissingServer: suspend () -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
    onDeleteError: suspend (Exception) -> Unit,
) {
    runSubscriptionGroupDeleteWhenRequired(
        groupServerIds = groupServerIds,
        selectedServerId = selectedServerId,
        serviceRunning = tunnelRunning,
        serviceOperationInProgress = serviceOperationInProgress,
        groupIsBuiltIn = groupIsBuiltIn,
        stopService = stopService,
        deleteGroupIfPresent = deleteGroupIfPresent,
        applyStopState = applyStoppedState,
        onDeleted = onDeleted,
        onMissingServer = onMissingServer,
        onStopFailed = onStopFailed,
        onDeleteError = onDeleteError,
    )
}

/** Desktop storage/runtime adapter for Android Home's stop-sensitive bulk-delete contract. */
internal suspend fun runDesktopProxyHomeBulkDelete(
    serverIds: Set<Int>,
    selectedServerId: Int?,
    tunnelRunning: Boolean,
    serviceOperationInProgress: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    applyStoppedState: () -> Unit,
    dispatchDelete: suspend () -> SharedApplicationActionOutcome,
    onDeleted: suspend () -> Unit,
    onDeleteError: suspend (Throwable) -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    runProxyServerBulkDeleteWhenRequired(
        serverIds = serverIds,
        selectedServerId = selectedServerId,
        serviceRunning = tunnelRunning,
        serviceOperationInProgress = serviceOperationInProgress,
        stopService = stopService,
        applyStopState = applyStoppedState,
        dispatchDelete = dispatchDelete,
        onDeleted = onDeleted,
        onDeleteError = onDeleteError,
        onStopFailed = onStopFailed,
    )
}

/**
 * Captures Android's current-group invalid IDs once, then removes only those IDs
 * through the host's latest-catalog action after any required runtime stop.
 */
internal suspend fun runDesktopProxyHomeDeleteInvalidServers(
    currentGroupServerIds: Set<Int>,
    servers: List<ProxyServerRecord>,
    selectedServerId: Int?,
    tunnelRunning: Boolean,
    serviceOperationInProgress: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    applyStoppedState: () -> Unit,
    dispatchDelete: suspend (Set<Int>) -> SharedApplicationActionOutcome,
    onDeleted: suspend (Int) -> Unit,
    onNoInvalidServers: suspend () -> Unit,
    onDeleteError: suspend (Throwable) -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    val invalidServerIds = ProxyServerGroupCleanup.invalidServerIds(
        servers = servers.map { server -> ProxyServerGroupCleanupRecord(server.id, server.server) },
        currentGroupServerIds = currentGroupServerIds,
    )
    if (invalidServerIds.isEmpty()) {
        onNoInvalidServers()
        return
    }

    runDesktopProxyHomeBulkDelete(
        serverIds = invalidServerIds,
        selectedServerId = selectedServerId,
        tunnelRunning = tunnelRunning,
        serviceOperationInProgress = serviceOperationInProgress,
        stopService = stopService,
        applyStoppedState = applyStoppedState,
        dispatchDelete = { dispatchDelete(invalidServerIds) },
        onDeleted = { onDeleted(invalidServerIds.size) },
        onDeleteError = onDeleteError,
        onStopFailed = onStopFailed,
    )
}
