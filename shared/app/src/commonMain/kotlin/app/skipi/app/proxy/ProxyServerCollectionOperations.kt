// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import app.skipi.app.model.ProxyServerCatalog
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup

/** Platform-neutral proxy row used by collection transformations. */
data class ProxyServerRecord(
    val id: Int,
    val groupId: Int,
    val server: ProxyServer<*>,
    val latency: String = "",
)

data class ProxyServerCollectionResult(
    val servers: List<ProxyServerRecord>,
    val nextServerId: Int,
    val selectedServerId: Int,
    val proxyRunning: Boolean = false,
)

/** Prepends imported rows and advances IDs without disturbing the current selection. */
fun importProxyServerRecords(
    servers: List<ProxyServerRecord>,
    imported: List<ProxyServer<*>>,
    groupId: Int,
    nextServerId: Int,
    selectedServerId: Int,
): ProxyServerCollectionResult {
    if (imported.isEmpty()) return ProxyServerCollectionResult(servers, nextServerId, selectedServerId)
    var id = nextServerId
    val newServers = imported.map { server -> ProxyServerRecord(id = id++, groupId = groupId, server = server) }
    val result = newServers + servers
    return ProxyServerCollectionResult(
        servers = result,
        nextServerId = maxOf(nextServerId, id),
        selectedServerId = selectedServerId.takeIf { selected -> result.any { it.id == selected } }
            ?: result.first().id,
    )
}

data class ProxyServerRecordSaveResult(
    val collection: ProxyServerCollectionResult,
    val existingGroupId: Int?,
    val wasExisting: Boolean,
)

/** Replaces an existing row in place or prepends a new row when its group is known. */
fun saveProxyServerRecord(
    servers: List<ProxyServerRecord>,
    serverId: Int,
    server: ProxyServer<*>,
    groupId: Int?,
    nextServerId: Int,
    selectedServerId: Int,
): ProxyServerRecordSaveResult {
    val index = servers.indexOfFirst { it.id == serverId }
    val existing = index >= 0
    val actualGroupId = if (existing) servers[index].groupId else groupId
    val updated = when {
        existing -> servers.toMutableList().also { it[index] = it[index].copy(server = server) }
        groupId != null -> listOf(ProxyServerRecord(serverId, groupId, server)) + servers
        else -> servers
    }
    return ProxyServerRecordSaveResult(
        collection = ProxyServerCollectionResult(
            servers = updated,
            nextServerId = maxOf(nextServerId, serverId + 1),
            selectedServerId = selectedServerId.takeIf { selected -> updated.any { it.id == selected } }
                ?: updated.firstOrNull()?.id
                ?: selectedServerId,
        ),
        existingGroupId = actualGroupId,
        wasExisting = existing,
    )
}

/** Removes rows and prunes their references from strategy and chain servers. */
fun deleteProxyServerRecords(
    servers: List<ProxyServerRecord>,
    deletedServerIds: Set<Int>,
    nextServerId: Int,
    selectedServerId: Int,
    proxyRunning: Boolean,
): ProxyServerCollectionResult {
    if (deletedServerIds.isEmpty()) {
        return ProxyServerCollectionResult(servers, nextServerId, selectedServerId, proxyRunning)
    }
    val next = servers.filterNot { it.id in deletedServerIds }.map { record ->
        val updatedServer = record.server.withoutDeletedProxyReferences(deletedServerIds)
        if (updatedServer === record.server) record else record.copy(server = updatedServer)
    }
    return ProxyServerCollectionResult(
        servers = next,
        nextServerId = nextServerId,
        selectedServerId = selectedServerIdAfterDeletion(
            selectedServerId = selectedServerId,
            deletedServerIds = deletedServerIds,
            remainingServerIds = next.map(ProxyServerRecord::id),
        ),
        proxyRunning = proxyRunning && selectedServerId !in deletedServerIds,
    )
}

/** Removes rows and prunes references while keeping catalog-owned metadata intact. */
fun deleteProxyServerRecords(
    catalog: ProxyServerCatalog,
    deletedServerIds: Set<Int>,
): ProxyServerCatalog {
    if (deletedServerIds.isEmpty()) return catalog

    val remaining = catalog.servers.filterNot { it.id in deletedServerIds }.map { record ->
        val updatedServer = record.server.withoutDeletedProxyReferences(deletedServerIds)
        if (updatedServer === record.server) record else record.copy(server = updatedServer)
    }
    return catalog.copy(
        servers = remaining,
        selectedServerId = selectedServerIdAfterDeletion(
            selectedServerId = catalog.selectedServerId,
            deletedServerIds = deletedServerIds,
            remainingServerIds = remaining.map { it.id },
        ),
    )
}

private fun ProxyServer<*>.withoutDeletedProxyReferences(deletedServerIds: Set<Int>): ProxyServer<*> = when (this) {
    is StrategyGroup -> {
        val remainingIds = proxyServerIds.filterNot { it in deletedServerIds }
        val nextSelectedMemberId = if (selectedMemberId in deletedServerIds) {
            remainingIds.firstOrNull()
        } else {
            selectedMemberId
        }
        if (remainingIds == proxyServerIds && nextSelectedMemberId == selectedMemberId) this
        else copy(proxyServerIds = remainingIds, selectedMemberId = nextSelectedMemberId)
    }
    is ChainProxy -> {
        val remainingIds = proxyServerIds.filterNot { it in deletedServerIds }
        if (remainingIds == proxyServerIds) this else copy(proxyServerIds = remainingIds)
    }
    else -> this
}

private fun selectedServerIdAfterDeletion(
    selectedServerId: Int,
    deletedServerIds: Set<Int>,
    remainingServerIds: List<Int>,
): Int = if (selectedServerId in deletedServerIds) {
    remainingServerIds.firstOrNull() ?: selectedServerId
} else {
    selectedServerId
}

/** Reorders only real subscription groups while preserving fixed and built-in slots. */
fun <T> moveSubscriptionGroup(
    groups: List<T>,
    groupId: Int,
    offset: Int,
    idOf: (T) -> Int,
    isBuiltIn: (T) -> Boolean,
    fixedGroupId: Int,
): List<T> {
    if (offset == 0 || groupId == fixedGroupId) return groups
    val movable = groups.filter { idOf(it) != fixedGroupId && !isBuiltIn(it) }
    val sourceIndex = movable.indexOfFirst { idOf(it) == groupId }
    if (sourceIndex < 0) return groups
    val targetIndex = (sourceIndex + offset).coerceIn(0, movable.lastIndex)
    if (sourceIndex == targetIndex) return groups
    val reordered = movable.toMutableList().apply { add(targetIndex, removeAt(sourceIndex)) }
    val movableIds = reordered.mapTo(mutableSetOf(), idOf)
    var nextIndex = 0
    return groups.map { group -> if (idOf(group) in movableIds) reordered[nextIndex++] else group }
}
