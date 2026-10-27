// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

enum class ProxyServerSelectionRuntimeAction {
    None,
    Restart,
}

/** Applies the Android Home selection-to-runtime restart policy without host dependencies. */
fun decideProxyServerSelectionRuntimeAction(
    previousSelectedServerId: Int?,
    selectedServerId: Int?,
    requestedServerId: Int,
    serverExists: Boolean,
    runtimeRunning: Boolean,
    activeServerId: Int?,
): ProxyServerSelectionRuntimeAction {
    val selectionChanged = previousSelectedServerId != selectedServerId
    val requestMatchesLatestSelection = selectedServerId == requestedServerId
    val activeRuntimeUsesAnotherServer = activeServerId != selectedServerId

    return if (
        selectionChanged &&
        requestMatchesLatestSelection &&
        serverExists &&
        runtimeRunning &&
        activeRuntimeUsesAnotherServer
    ) {
        ProxyServerSelectionRuntimeAction.Restart
    } else {
        ProxyServerSelectionRuntimeAction.None
    }
}
