// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

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
    suspend fun dispatchDeleteAndReport() {
        when (val outcome = dispatchDelete()) {
            SharedApplicationActionOutcome.Completed -> {
                onDeleted()
            }

            is SharedApplicationActionOutcome.Rejected -> {
                onDeleteError(IllegalArgumentException(outcome.reason))
            }

            is SharedApplicationActionOutcome.Failed -> {
                onDeleteError(IllegalStateException(outcome.reason))
            }
        }
    }

    when (val stopResult = stopService()) {
        is ProxyServiceResult.Success -> {
            applyStopState(stopResult)
            dispatchDeleteAndReport()
        }

        ProxyServiceResult.MissingServer -> dispatchDeleteAndReport()
        is ProxyServiceResult.Failed -> onStopFailed(stopResult.error)
    }
}
