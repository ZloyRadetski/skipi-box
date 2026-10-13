// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

fun proxyServerSingleDeleteRequiresStop(
    serverId: Int,
    selectedServerId: Int?,
    serviceRunning: Boolean,
): Boolean = serviceRunning && selectedServerId == serverId

/**
 * Applies Android Home's one-row deletion order. This differs from bulk deletion: for the
 * selected running row, catalog removal is dispatched before the successful stop state is
 * published, and the dispatch itself remains non-suspending/fire-and-forget.
 */
suspend fun runSingleProxyServerDeleteWhenRequired(
    serverId: Int,
    selectedServerId: Int?,
    serviceRunning: Boolean,
    serviceOperationInProgress: Boolean,
    stopService: suspend () -> ProxyServerStopOutcome,
    dispatchDeleteIfPresent: () -> Boolean,
    applyStopState: () -> Unit,
    onDeleted: suspend () -> Unit,
    onMissingServer: suspend () -> Unit,
    onStopFailed: suspend (Throwable) -> Unit,
) {
    if (serviceOperationInProgress) return

    val mustStop = proxyServerSingleDeleteRequiresStop(
        serverId = serverId,
        selectedServerId = selectedServerId,
        serviceRunning = serviceRunning,
    )
    if (!mustStop) {
        if (dispatchDeleteIfPresent()) onDeleted()
        return
    }

    when (val result = stopService()) {
        ProxyServerStopOutcome.Stopped -> {
            if (dispatchDeleteIfPresent()) {
                applyStopState()
                onDeleted()
            }
        }

        ProxyServerStopOutcome.MissingServer -> onMissingServer()
        is ProxyServerStopOutcome.Failed -> onStopFailed(result.cause)
    }
}
