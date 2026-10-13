// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import kotlinx.coroutines.CancellationException

fun proxySubscriptionGroupDeleteRequiresStop(
    groupServerIds: Set<Int>,
    selectedServerId: Int?,
    serviceRunning: Boolean,
): Boolean = serviceRunning && selectedServerId != null && selectedServerId in groupServerIds

/**
 * Applies Android Home's subscription-group deletion decision and ordering. A running service
 * is stopped only when the selected server belongs to the group. Group removal is awaited;
 * after a successful removal, stop state and feedback are published in that order.
 */
suspend fun runSubscriptionGroupDeleteWhenRequired(
    groupServerIds: Set<Int>,
    selectedServerId: Int?,
    serviceRunning: Boolean,
    serviceOperationInProgress: Boolean,
    groupIsBuiltIn: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    deleteGroupIfPresent: suspend () -> Boolean,
    applyStopState: () -> Unit,
    onDeleted: suspend () -> Unit,
    onMissingServer: suspend () -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
    onDeleteError: suspend (Exception) -> Unit,
) {
    if (groupIsBuiltIn || serviceOperationInProgress) return

    val mustStop = proxySubscriptionGroupDeleteRequiresStop(
        groupServerIds = groupServerIds,
        selectedServerId = selectedServerId,
        serviceRunning = serviceRunning,
    )
    if (!mustStop) {
        deleteGroupAndReportIfPresent(
            deleteGroupIfPresent = deleteGroupIfPresent,
            onDeleted = onDeleted,
            onDeleteError = onDeleteError,
        )
        return
    }

    when (val result = stopService()) {
        ProxyServerStopOutcome.Stopped -> {
            if (deleteGroupAndReportIfPresent(
                    deleteGroupIfPresent = deleteGroupIfPresent,
                    onDeleted = {},
                    onDeleteError = onDeleteError,
                )
            ) {
                applyStopState()
                onDeleted()
            }
        }

        ProxyServerStopOutcome.MissingServer -> onMissingServer()
        is ProxyServerStopOutcome.Failed -> onStopFailed(result.cause)
    }
}

private suspend fun deleteGroupAndReportIfPresent(
    deleteGroupIfPresent: suspend () -> Boolean,
    onDeleted: suspend () -> Unit,
    onDeleteError: suspend (Exception) -> Unit,
): Boolean {
    val deleted = try {
        deleteGroupIfPresent()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onDeleteError(failure)
        return false
    }

    if (deleted) onDeleted()
    return deleted
}
