// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/**
 * The compact ping button measures only the selected server: the live connection while the tunnel
 * is up, otherwise the server on its own. It never pings the visible page or the server's group.
 * Null when there is nothing to measure.
 */
fun ProxyHomeUiState.compactPingAction(): ProxyHomeAction? {
    val server = selectedServer ?: return null
    return when {
        isRunning -> ProxyHomeAction.TestConnection(server.id)
        server.canTest -> ProxyHomeAction.TestServer(server.id)
        else -> null
    }
}
