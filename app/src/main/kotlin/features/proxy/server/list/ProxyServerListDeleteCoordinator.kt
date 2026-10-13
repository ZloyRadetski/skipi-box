// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.skipi.app.proxy.ProxyServerStopOutcome
import app.skipi.app.proxy.runProxyServerBulkDeleteWhenRequired
import app.skipi.app.proxy.runBulkDeleteAfterStoppingProxyService
import app.skipi.app.store.SharedApplicationActionOutcome
import features.proxy.server.usecase.ProxyServiceResult

/** Stops the selected server before removing it from the shared catalog. */
internal suspend fun deleteProxyServersAfterStoppingService(
    stopService: suspend () -> ProxyServiceResult,
    dispatchDelete: suspend () -> SharedApplicationActionOutcome,
    applyStopState: (ProxyServiceResult.Success) -> Unit,
    onDeleted: suspend () -> Unit,
    onDeleteError: suspend (Throwable) -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    var successfulStop: ProxyServiceResult.Success? = null

    runBulkDeleteAfterStoppingProxyService(
        stopService = {
            when (val result = stopService()) {
                is ProxyServiceResult.Success -> {
                    successfulStop = result
                    ProxyServerStopOutcome.Stopped
                }

                ProxyServiceResult.MissingServer -> ProxyServerStopOutcome.MissingServer
                is ProxyServiceResult.Failed -> ProxyServerStopOutcome.Failed(result.error)
            }
        },
        applyStopState = { applyStopState(checkNotNull(successfulStop)) },
        dispatchDelete = dispatchDelete,
        onDeleted = onDeleted,
        onDeleteError = onDeleteError,
        onStopFailed = onStopFailed,
    )
}

/** Applies Android Home's busy/selection decision before entering the existing runtime adapter. */
internal suspend fun deleteProxyServersWhenRequired(
    serverIds: Set<Int>,
    selectedServerId: Int?,
    serviceRunning: Boolean,
    serviceOperationInProgress: Boolean,
    stopService: suspend () -> ProxyServiceResult,
    dispatchDelete: suspend () -> SharedApplicationActionOutcome,
    applyStopState: (ProxyServiceResult.Success) -> Unit,
    onDeleted: suspend () -> Unit,
    onDeleteError: suspend (Throwable) -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    var successfulStop: ProxyServiceResult.Success? = null

    runProxyServerBulkDeleteWhenRequired(
        serverIds = serverIds,
        selectedServerId = selectedServerId,
        serviceRunning = serviceRunning,
        serviceOperationInProgress = serviceOperationInProgress,
        stopService = {
            when (val result = stopService()) {
                is ProxyServiceResult.Success -> {
                    successfulStop = result
                    ProxyServerStopOutcome.Stopped
                }

                ProxyServiceResult.MissingServer -> ProxyServerStopOutcome.MissingServer
                is ProxyServiceResult.Failed -> ProxyServerStopOutcome.Failed(result.error)
            }
        },
        applyStopState = { applyStopState(checkNotNull(successfulStop)) },
        dispatchDelete = dispatchDelete,
        onDeleted = onDeleted,
        onDeleteError = onDeleteError,
        onStopFailed = onStopFailed,
    )
}
