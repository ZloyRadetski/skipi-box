// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.isCompositeProxyServer
import features.subscription.SubscriptionServerCandidate
import features.subscription.reconcileSubscriptionServers

/** Portable input for reconciling one refreshed subscription group. */
data class SubscriptionServerCollectionUpdate(
    val groupId: Int,
    val servers: List<ProxyServer<*>>,
    val autoOverrideRules: Boolean?,
)

/**
 * Reconciles refreshed subscription rows and all references that can point to them.
 * Rows outside updated groups are retained, and composite models are always copied
 * before their references change so previously emitted state remains immutable.
 */
fun reconcileSubscriptionServerCollection(
    servers: List<ProxyServerRecord>,
    updates: List<SubscriptionServerCollectionUpdate>,
    nextServerId: Int,
    selectedServerId: Int,
    retainedServerIds: Set<Int> = emptySet(),
): ProxyServerCollectionResult {
    if (updates.isEmpty()) {
        return ProxyServerCollectionResult(servers, nextServerId, selectedServerId)
    }

    val updatesByGroupId = updates.associateBy(SubscriptionServerCollectionUpdate::groupId)
    val updatedGroupIds = updatesByGroupId.keys
    val existingDownloadedServersByGroup = servers
        .filter { row -> row.groupId in updatedGroupIds && !row.server.isCompositeProxyServer() }
        .groupBy(ProxyServerRecord::groupId)
    val oldIdToNewId = mutableMapOf<Int, Int>()
    var nextId = nextServerId

    val importedServers = updates.flatMap { update ->
        val candidates = existingDownloadedServersByGroup[update.groupId].orEmpty()
        val candidatesById = candidates.associateBy(ProxyServerRecord::id)
        val reconciliation = reconcileSubscriptionServers(
            previous = candidates.map { candidate ->
                SubscriptionServerCandidate(id = candidate.id, server = candidate.server)
            },
            incoming = update.servers,
            firstNewServerId = nextId,
            occupiedIds = retainedServerIds + servers.map(ProxyServerRecord::id),
        )
        nextId = reconciliation.nextServerId
        oldIdToNewId.putAll(reconciliation.oldIdToNewId)

        reconciliation.servers.map { reconciled ->
            val preserved = reconciled.matchedPreviousId?.let(candidatesById::get)
            val imported = reconciled.server
            val server = if (imported is Custom && update.autoOverrideRules != null) {
                imported.copy(overrideInboundAndDns = update.autoOverrideRules)
            } else {
                imported
            }
            ProxyServerRecord(
                id = reconciled.id,
                groupId = update.groupId,
                server = server,
                latency = preserved?.latency.orEmpty(),
            )
        }
    }

    val existingCompositeServers = servers.filter { row -> row.server.isCompositeProxyServer() }
    val otherServers = servers.filterNot { row ->
        row.groupId in updatedGroupIds || row.server.isCompositeProxyServer()
    }
    val validServerIds = (importedServers + otherServers + existingCompositeServers)
        .mapTo(mutableSetOf(), ProxyServerRecord::id)
        .apply { addAll(retainedServerIds) }

    val updatedCompositeServers = existingCompositeServers.map { row ->
        val updatedServer = when (val composite = row.server) {
            is StrategyGroup -> {
                val currentIds = composite.proxyServerIds
                val remappedIds = currentIds.map { id -> oldIdToNewId[id] ?: id }
                val filteredIds = remappedIds.filter { id -> id in validServerIds }
                val finalIds = when {
                    filteredIds.isNotEmpty() -> filteredIds
                    importedServers.isNotEmpty() -> importedServers.take(currentIds.size).map(ProxyServerRecord::id)
                    else -> currentIds
                }
                val selectedId = composite.selectedMemberId
                val remappedSelectedId = selectedId?.let { id -> oldIdToNewId[id] ?: id }
                val finalSelectedId = when {
                    remappedSelectedId == null -> null
                    remappedSelectedId in validServerIds -> remappedSelectedId
                    finalIds.isNotEmpty() -> finalIds.first()
                    else -> remappedSelectedId
                }
                if (finalIds == currentIds && finalSelectedId == selectedId) composite
                else composite.copy(proxyServerIds = finalIds, selectedMemberId = finalSelectedId)
            }
            is ChainProxy -> {
                val currentIds = composite.proxyServerIds
                val filteredIds = currentIds
                    .map { id -> oldIdToNewId[id] ?: id }
                    .filter { id -> id in validServerIds }
                if (filteredIds == currentIds) composite else composite.copy(proxyServerIds = filteredIds)
            }
            else -> composite
        }
        if (updatedServer === row.server) row else row.copy(server = updatedServer)
    }

    val nextServers = importedServers + otherServers + updatedCompositeServers
    val selectedId = when {
        nextServers.any { row -> row.id == selectedServerId } -> selectedServerId
        selectedServerId in retainedServerIds -> selectedServerId
        else -> servers.firstOrNull { row -> row.groupId !in updatedGroupIds }?.id
            ?: nextServers.firstOrNull()?.id
            ?: selectedServerId
    }
    return ProxyServerCollectionResult(
        servers = nextServers,
        nextServerId = maxOf(nextServerId, nextId),
        selectedServerId = selectedId,
    )
}
