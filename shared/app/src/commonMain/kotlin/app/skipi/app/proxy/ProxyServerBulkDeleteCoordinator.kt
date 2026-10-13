// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import app.skipi.app.store.SharedApplicationActionOutcome

/** Result of the host stop operation used by Android's bulk server deletion flow. */
sealed interface ProxyServerStopOutcome {
    data object Stopped : ProxyServerStopOutcome
    data object MissingServer : ProxyServerStopOutcome
    data class Failed(val cause: Throwable) : ProxyServerStopOutcome
}

/** True only when this delete would remove the selected server from a running service. */
fun proxyServerBulkDeleteRequiresStop(
    serverIds: Set<Int>,
    selectedServerId: Int?,
    serviceRunning: Boolean,
): Boolean = serverIds.isNotEmpty() &&
    serviceRunning &&
    selectedServerId != null &&
    selectedServerId in serverIds

/**
 * Applies Android's Home bulk-delete decision before delegating to the host runtime.
 * An operation already in progress blocks only a delete that must stop the selected running
 * server; stopped and unselected deletes still dispatch directly.
 */
suspend fun runProxyServerBulkDeleteWhenRequired(
    serverIds: Set<Int>,
    selectedServerId: Int?,
    serviceRunning: Boolean,
    serviceOperationInProgress: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    applyStopState: () -> Unit,
    dispatchDelete: suspend () -> SharedApplicationActionOutcome,
    onDeleted: suspend () -> Unit,
    onDeleteError: suspend (Throwable) -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    if (serverIds.isEmpty()) return

    val mustStop = proxyServerBulkDeleteRequiresStop(
        serverIds = serverIds,
        selectedServerId = selectedServerId,
        serviceRunning = serviceRunning,
    )
    if (!mustStop) {
        when (val outcome = dispatchDelete()) {
            SharedApplicationActionOutcome.Completed -> onDeleted()
            is SharedApplicationActionOutcome.Rejected ->
                onDeleteError(IllegalArgumentException(outcome.reason))
            is SharedApplicationActionOutcome.Failed ->
                onDeleteError(IllegalStateException(outcome.reason))
        }
        return
    }
    if (serviceOperationInProgress) return

    runBulkDeleteAfterStoppingProxyService(
        stopService = stopService,
        applyStopState = applyStopState,
        dispatchDelete = dispatchDelete,
        onDeleted = onDeleted,
        onDeleteError = onDeleteError,
        onStopFailed = onStopFailed,
    )
}

/**
 * Runs the bulk delete sequence used by Android: publish a successful stop state before
 * awaiting the catalog deletion, then report its outcome. A missing service skips only the
 * stop-state callback; a failed stop prevents deletion. Exceptions, including cancellation,
 * propagate to the caller.
 */
suspend fun runBulkDeleteAfterStoppingProxyService(
    stopService: suspend () -> ProxyServerStopOutcome,
    applyStopState: () -> Unit,
    dispatchDelete: suspend () -> SharedApplicationActionOutcome,
    onDeleted: suspend () -> Unit,
    onDeleteError: suspend (Throwable) -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    suspend fun dispatchDeleteAndReport() {
        when (val outcome = dispatchDelete()) {
            SharedApplicationActionOutcome.Completed -> onDeleted()
            is SharedApplicationActionOutcome.Rejected ->
                onDeleteError(IllegalArgumentException(outcome.reason))
            is SharedApplicationActionOutcome.Failed ->
                onDeleteError(IllegalStateException(outcome.reason))
        }
    }

    when (val stopOutcome = stopService()) {
        ProxyServerStopOutcome.Stopped -> {
            applyStopState()
            dispatchDeleteAndReport()
        }

        ProxyServerStopOutcome.MissingServer -> dispatchDeleteAndReport()
        is ProxyServerStopOutcome.Failed -> onStopFailed(stopOutcome.cause)
    }
}
