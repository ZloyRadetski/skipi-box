// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/** Result of removing one server from the Home collection. */
data class ProxyHomeServerRemoval<Server, ServerId>(
    val servers: List<Server>,
    val selectedServerId: ServerId,
    val removed: Boolean,
)

/**
 * Removes a server and keeps selection valid when the selected server was removed.
 * If the collection becomes empty, the previous ID is retained for compatibility with hosts
 * that use a sentinel or allow selecting a server later.
 */
fun <Server, ServerId> removeProxyHomeServer(
    servers: List<Server>,
    selectedServerId: ServerId,
    serverId: ServerId,
    idOf: (Server) -> ServerId,
): ProxyHomeServerRemoval<Server, ServerId> {
    val nextServers = servers.filterNot { server -> idOf(server) == serverId }
    if (nextServers.size == servers.size) {
        return ProxyHomeServerRemoval(servers, selectedServerId, removed = false)
    }
    val nextSelectedId = if (selectedServerId == serverId) {
        nextServers.firstOrNull()?.let(idOf) ?: selectedServerId
    } else {
        selectedServerId
    }
    return ProxyHomeServerRemoval(nextServers, nextSelectedId, removed = true)
}

/** Result of removing a group and its servers from the Home collections. */
data class ProxyHomeGroupRemoval<Group, Server, ServerId>(
    val groups: List<Group>,
    val servers: List<Server>,
    val selectedServerId: ServerId,
    val removed: Boolean,
)

/**
 * Removes a deletable group and all servers belonging to it. Selection is retained if its
 * server survives, otherwise it moves to the first remaining server or the host's empty ID.
 */
fun <Group, Server, GroupId, ServerId> removeProxyHomeGroup(
    groups: List<Group>,
    servers: List<Server>,
    selectedServerId: ServerId,
    groupId: GroupId,
    emptySelectedServerId: ServerId,
    groupIdOf: (Group) -> GroupId,
    serverIdOf: (Server) -> ServerId,
    serverGroupIdOf: (Server) -> GroupId,
    isBuiltIn: (Group) -> Boolean,
): ProxyHomeGroupRemoval<Group, Server, ServerId> {
    val target = groups.firstOrNull { group -> groupIdOf(group) == groupId }
        ?: return ProxyHomeGroupRemoval(groups, servers, selectedServerId, removed = false)
    if (isBuiltIn(target)) {
        return ProxyHomeGroupRemoval(groups, servers, selectedServerId, removed = false)
    }

    val nextGroups = groups.filterNot { group -> groupIdOf(group) == groupId }
    val nextServers = servers.filterNot { server -> serverGroupIdOf(server) == groupId }
    val nextSelectedId = nextServers.firstOrNull { server -> serverIdOf(server) == selectedServerId }
        ?.let(serverIdOf)
        ?: nextServers.firstOrNull()?.let(serverIdOf)
        ?: emptySelectedServerId
    return ProxyHomeGroupRemoval(nextGroups, nextServers, nextSelectedId, removed = true)
}
/** Reorders one collection item by stable ID and leaves the input untouched when either ID is absent. */
fun <Item, ItemId> moveProxyHomeItem(
    items: List<Item>,
    fromId: ItemId,
    toId: ItemId,
    idOf: (Item) -> ItemId,
): List<Item> {
    val fromIndex = items.indexOfFirst { item -> idOf(item) == fromId }
    val toIndex = items.indexOfFirst { item -> idOf(item) == toId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return items

    return items.toMutableList().also { reordered ->
        reordered.add(toIndex, reordered.removeAt(fromIndex))
    }
}
