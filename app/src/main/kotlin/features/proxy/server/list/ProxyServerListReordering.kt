// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import app.skipi.app.home.moveProxyHomeItem

internal fun List<ProxyServerState>.reorderVisibleServerById(
    fromServerId: Int,
    toServerId: Int,
): List<ProxyServerState> {
    val fromIndex = indexOfFirst { server -> server.id == fromServerId }
    val toIndex = indexOfFirst { server -> server.id == toServerId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return this

    return moveProxyHomeItem(items = this, fromId = fromServerId, toId = toServerId, idOf = { it.id })
}
