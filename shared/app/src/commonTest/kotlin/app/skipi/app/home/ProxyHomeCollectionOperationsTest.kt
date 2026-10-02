// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyHomeCollectionOperationsTest {
    @Test
    fun deletingSelectedServerSelectsNextAndMissingServerIsNoOp() {
        val servers = listOf(Server(1, 10), Server(2, 10), Server(3, 20))

        val result = removeProxyHomeServer(
            servers = servers,
            selectedServerId = 1,
            serverId = 1,
            idOf = Server::id,
        )

        assertEquals(listOf(servers[1], servers[2]), result.servers)
        assertEquals(2, result.selectedServerId)
        assertTrue(result.removed)

        val missing = removeProxyHomeServer(
            servers = result.servers,
            selectedServerId = result.selectedServerId,
            serverId = 99,
            idOf = Server::id,
        )
        assertEquals(result.servers, missing.servers)
        assertEquals(2, missing.selectedServerId)
        assertFalse(missing.removed)
    }

    @Test
    fun deletingGroupRemovesItsServersAndKeepsSelectionWhenPossible() {
        val groups = listOf(Group(10), Group(20))
        val servers = listOf(Server(1, 10), Server(2, 10), Server(3, 20))

        val result = removeProxyHomeGroup(
            groups = groups,
            servers = servers,
            selectedServerId = 3,
            groupId = 10,
            emptySelectedServerId = 0,
            groupIdOf = Group::id,
            serverIdOf = Server::id,
            serverGroupIdOf = Server::groupId,
            isBuiltIn = Group::builtIn,
        )

        assertEquals(listOf(groups[1]), result.groups)
        assertEquals(listOf(servers[2]), result.servers)
        assertEquals(3, result.selectedServerId)
        assertTrue(result.removed)
    }

    @Test
    fun deletingSelectedGroupChoosesFirstRemainingServerAndEmptySentinel() {
        val result = removeProxyHomeGroup(
            groups = listOf(Group(10), Group(20)),
            servers = listOf(Server(1, 10), Server(2, 20)),
            selectedServerId = 1,
            groupId = 10,
            emptySelectedServerId = 0,
            groupIdOf = Group::id,
            serverIdOf = Server::id,
            serverGroupIdOf = Server::groupId,
            isBuiltIn = Group::builtIn,
        )
        assertEquals(2, result.selectedServerId)

        val empty = removeProxyHomeGroup(
            groups = listOf(Group(10)),
            servers = listOf(Server(1, 10)),
            selectedServerId = 1,
            groupId = 10,
            emptySelectedServerId = 0,
            groupIdOf = Group::id,
            serverIdOf = Server::id,
            serverGroupIdOf = Server::groupId,
            isBuiltIn = Group::builtIn,
        )
        assertEquals(0, empty.selectedServerId)
        assertTrue(empty.servers.isEmpty())
    }

    @Test
    fun builtInAndMissingGroupsCannotChangeCollections() {
        val groups = listOf(Group(10, builtIn = true))
        val servers = listOf(Server(1, 10))

        listOf(10, 99).forEach { groupId ->
            val result = removeProxyHomeGroup(
                groups = groups,
                servers = servers,
                selectedServerId = 1,
                groupId = groupId,
                emptySelectedServerId = 0,
                groupIdOf = Group::id,
                serverIdOf = Server::id,
                serverGroupIdOf = Server::groupId,
                isBuiltIn = Group::builtIn,
            )
            assertEquals(groups, result.groups)
            assertEquals(servers, result.servers)
            assertEquals(1, result.selectedServerId)
            assertFalse(result.removed)
        }
    }

    private data class Group(val id: Int, val builtIn: Boolean = false)

    private data class Server(val id: Int, val groupId: Int)
}
