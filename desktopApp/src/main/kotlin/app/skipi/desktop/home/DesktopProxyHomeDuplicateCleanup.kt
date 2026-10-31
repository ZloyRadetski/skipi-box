// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.usecase.ProxyServerGroupCleanup
import features.proxy.server.usecase.ProxyServerGroupCleanupRecord

/** Captures duplicate IDs from the catalog and selection visible when Home starts cleanup. */
internal fun captureDesktopDuplicateProxyServerIds(
    library: DesktopServerLibrary,
    currentGroupServerIds: Set<Int>,
): Set<Int> = ProxyServerGroupCleanup.duplicateServerIds(
    servers = library.servers.mapNotNull { stored ->
        stored.decode().getOrNull()?.let { server -> ProxyServerGroupCleanupRecord(stored.id, server) }
    },
    currentGroupServerIds = currentGroupServerIds,
    selectedServerId = library.selectedServerId ?: 0,
)
