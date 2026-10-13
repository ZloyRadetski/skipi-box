// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.rawInitialProxyHomeGroupId

internal fun initialDesktopProxyHomeGroupId(serverLibrary: DesktopServerLibrary): String {
    val selectedId = serverLibrary.selectedServerId
    val selectedRecord = selectedId?.let { id -> serverLibrary.servers.firstOrNull { server -> server.id == id } }
    val decodedSelectedGroup = selectedRecord
        ?.decode()
        ?.getOrNull()
        ?.let { selectedRecord.id to selectedRecord.subscriptionId.toDesktopHomeGroupId() }

    return rawInitialProxyHomeGroupId(
        selectedServerId = selectedId,
        decodedServerGroupIds = listOfNotNull(decodedSelectedGroup),
        defaultGroupId = DesktopProxyGroupIds.Manual,
    )
}

private fun Int?.toDesktopHomeGroupId(): String = when (this) {
    null -> DesktopProxyGroupIds.Manual
    -2 -> DesktopProxyGroupIds.AutoBalancers
    else -> if (this > 0) DesktopProxyGroupIds.subscription(this) else toString()
}
