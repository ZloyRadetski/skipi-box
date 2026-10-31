// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyServerSelectionRuntimeAction
import app.skipi.app.home.decideProxyServerSelectionRuntimeAction

/** Mirrors Android Home's refresh restart trigger: only a changed selected server can reconnect. */
internal fun shouldReconnectAfterSubscriptionRefresh(
    coreIsRunning: Boolean,
    previousSelectedServerId: Int?,
    currentSelectedServerId: Int?,
): Boolean {
    val selectedServerId = currentSelectedServerId ?: return false
    return decideProxyServerSelectionRuntimeAction(
        previousSelectedServerId = previousSelectedServerId,
        selectedServerId = selectedServerId,
        requestedServerId = selectedServerId,
        serverExists = true,
        runtimeRunning = coreIsRunning,
        // A refresh-driven selection transition leaves the previously selected server active
        // until the existing reconnect mechanism applies the new selection.
        activeServerId = previousSelectedServerId,
    ) == ProxyServerSelectionRuntimeAction.Restart
}
