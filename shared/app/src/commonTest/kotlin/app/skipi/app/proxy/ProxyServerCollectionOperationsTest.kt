// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyServerCollectionOperationsTest {
    @Test
    fun importedServersArePrependedAndPreserveSelectionWhenPossible() {
        val existing = ProxyServerRecord(id = 5, groupId = 1, server = HTTP(port = "80"))

        val result = importProxyServerRecords(
            servers = listOf(existing),
            imported = listOf(HTTP(port = "81"), HTTP(port = "82")),
            groupId = 2,
            nextServerId = 10,
            selectedServerId = 5,
        )

        assertEquals(listOf(10, 11, 5), result.servers.map { it.id })
        assertEquals(listOf(2, 2, 1), result.servers.map { it.groupId })
        assertEquals(12, result.nextServerId)
        assertEquals(5, result.selectedServerId)
    }

    @Test
    fun saveKeepsExistingGroupAndLatencyAndSelectsFirstWhenCurrentSelectionDisappears() {
        val existing = ProxyServerRecord(id = 8, groupId = 3, server = HTTP(port = "80"), latency = "42 ms")

        val result = saveProxyServerRecord(
            servers = listOf(existing),
            serverId = 8,
            server = HTTP(port = "443"),
            groupId = 9,
            nextServerId = 10,
            selectedServerId = 99,
        )

        assertTrue(result.wasExisting)
        assertEquals(3, result.existingGroupId)
        assertEquals("42 ms", result.collection.servers.single().latency)
        assertEquals(3, result.collection.servers.single().groupId)
        assertEquals(8, result.collection.selectedServerId)
    }

    @Test
    fun deleteRemovesRowsPrunesCompositeReferencesAndStopsOnlyWhenSelectedRowWasDeleted() {
        val strategy = StrategyGroup(proxyServerIds = listOf(1, 2), selectedMemberId = 1)
        val chain = ChainProxy(proxyServerIds = listOf(1, 2))
        val result = deleteProxyServerRecords(
            servers = listOf(
                ProxyServerRecord(1, 1, HTTP(port = "80")),
                ProxyServerRecord(2, 1, HTTP(port = "443")),
                ProxyServerRecord(3, -2, strategy),
                ProxyServerRecord(4, -2, chain),
            ),
            deletedServerIds = setOf(1),
            nextServerId = 5,
            selectedServerId = 1,
            proxyRunning = true,
        )

        assertEquals(listOf(2, 3, 4), result.servers.map { it.id })
        assertEquals(listOf(1, -2, -2), result.servers.map { it.groupId })
        val updatedStrategy = result.servers.single { it.id == 3 }.server as StrategyGroup
        val updatedChain = result.servers.single { it.id == 4 }.server as ChainProxy
        assertEquals(listOf(2), updatedStrategy.proxyServerIds)
        assertEquals(2, updatedStrategy.selectedMemberId)
        assertEquals(listOf(2), updatedChain.proxyServerIds)
        // Collection updates are immutable: callers must use the returned records.
        assertEquals(listOf(1, 2), strategy.proxyServerIds)
        assertEquals(1, strategy.selectedMemberId)
        assertEquals(listOf(1, 2), chain.proxyServerIds)
        assertEquals(2, result.selectedServerId)
        assertFalse(result.proxyRunning)
    }

    @Test
    fun subscriptionMoveLeavesFixedAndBuiltInGroupsInTheirSlots() {
        data class Group(val id: Int, val builtIn: Boolean)
        val groups = listOf(Group(1, true), Group(10, false), Group(2, true), Group(11, false))

        val moved = moveSubscriptionGroup(
            groups = groups,
            groupId = 11,
            offset = -1,
            idOf = Group::id,
            isBuiltIn = Group::builtIn,
            fixedGroupId = 1,
        )

        assertEquals(listOf(1, 11, 2, 10), moved.map(Group::id))
    }
}
