// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import features.proxy.server.model.ProxyServer

/** Creates a server using the current catalog counter and prepends it to the ordered catalog. */
fun createProxyServerRecord(
    catalog: ProxyServerCatalog,
    server: ProxyServer<*>,
    sourceSubscriptionId: Int?,
): ProxyServerCatalog {
    require(catalog.nextServerId > 0) { "Next proxy server ID must be positive" }

    val serverId = maxOf(catalog.nextServerId, nextServerIdAfter(catalog.servers))
    check(serverId < Int.MAX_VALUE) { "No proxy server IDs remain" }

    val created = ProxyServerRecord(
        id = serverId,
        server = server,
        sourceSubscriptionId = sourceSubscriptionId,
    )
    val servers = listOf(created) + catalog.servers
    return catalog.copy(
        servers = servers,
        nextServerId = serverId + 1,
        selectedServerId = catalog.selectedServerId.takeIf { selectedId ->
            catalog.servers.any { it.id == selectedId }
        } ?: serverId,
    )
}

/** Replaces a server in place; a removed row is restored at the front with its captured identity. */
fun editProxyServerRecord(
    catalog: ProxyServerCatalog,
    serverId: Int,
    server: ProxyServer<*>,
    sourceSubscriptionId: Int? = null,
): ProxyServerCatalog {
    val existingIndex = catalog.servers.indexOfFirst { it.id == serverId }
    val servers = if (existingIndex >= 0) {
        catalog.servers.toMutableList().also { records ->
            records[existingIndex] = records[existingIndex].copy(server = server)
        }
    } else {
        listOf(
            ProxyServerRecord(
                id = serverId,
                server = server,
                sourceSubscriptionId = sourceSubscriptionId,
            ),
        ) + catalog.servers
    }

    return catalog.copy(
        servers = servers,
        nextServerId = maxOf(
            catalog.nextServerId,
            nextServerIdAfter(servers),
            positiveIncrementSaturated(serverId),
        ),
        selectedServerId = catalog.selectedServerId.takeIf { selectedId ->
            servers.any { it.id == selectedId }
        } ?: servers.firstOrNull()?.id ?: catalog.selectedServerId,
    )
}

private fun nextServerIdAfter(servers: List<ProxyServerRecord>): Int =
    servers.maxOfOrNull(ProxyServerRecord::id)
        ?.let(::positiveIncrementSaturated)
        ?: 1

private fun positiveIncrementSaturated(id: Int): Int = when {
    id >= Int.MAX_VALUE -> Int.MAX_VALUE
    id < 0 -> 1
    else -> id + 1
}
