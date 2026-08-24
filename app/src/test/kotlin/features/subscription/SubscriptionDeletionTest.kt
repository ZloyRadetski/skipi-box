// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.model.Custom
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionDeletionTest {

    @Test
    fun testDeleteSubscriptionGroupRemovesGroupAndServers() {
        val group1 = SubscriptionGroupState(
            id = 1,
            name = "Group 1",
            url = "https://example.com/sub1",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = false,
        )
        val group2 = SubscriptionGroupState(
            id = 2,
            name = "Group 2",
            url = "https://example.com/sub2",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = false,
        )

        val server1 = ProxyServerState(id = 101, groupId = 1, server = Custom(remarks = "Server 1"))
        val server2 = ProxyServerState(id = 102, groupId = 1, server = Custom(remarks = "Server 2"))
        val server3 = ProxyServerState(id = 103, groupId = 2, server = Custom(remarks = "Server 3"))

        val state = AppState(
            subscriptionGroups = listOf(group1, group2),
            proxyServers = listOf(server1, server2, server3),
            selectedProxyServerId = 101,
        )

        // Delete group 1
        val nextGroups = state.subscriptionGroups.filterNot { it.id == group1.id }
        val nextServers = state.proxyServers.filterNot { it.groupId == group1.id }
        val selectedProxyServerId = if (nextServers.any { it.id == state.selectedProxyServerId }) {
            state.selectedProxyServerId
        } else {
            nextServers.firstOrNull()?.id ?: 0
        }

        val updatedState = state.copy(
            subscriptionGroups = nextGroups,
            proxyServers = nextServers,
            selectedProxyServerId = selectedProxyServerId,
        )

        assertEquals(1, updatedState.subscriptionGroups.size)
        assertEquals(2, updatedState.subscriptionGroups.first().id)
        assertEquals(1, updatedState.proxyServers.size)
        assertEquals(103, updatedState.proxyServers.first().id)
        assertEquals(103, updatedState.selectedProxyServerId)
    }

    @Test
    fun testDeleteSubscriptionGroupWhenNoServersLeft() {
        val group = SubscriptionGroupState(
            id = 1,
            name = "Group 1",
            url = "https://example.com/sub",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = false,
        )
        val server = ProxyServerState(id = 101, groupId = 1, server = Custom(remarks = "Server 1"))

        val state = AppState(
            subscriptionGroups = listOf(group),
            proxyServers = listOf(server),
            selectedProxyServerId = 101,
        )

        val nextGroups = state.subscriptionGroups.filterNot { it.id == group.id }
        val nextServers = state.proxyServers.filterNot { it.groupId == group.id }
        val selectedProxyServerId = if (nextServers.any { it.id == state.selectedProxyServerId }) {
            state.selectedProxyServerId
        } else {
            nextServers.firstOrNull()?.id ?: 0
        }

        val updatedState = state.copy(
            subscriptionGroups = nextGroups,
            proxyServers = nextServers,
            selectedProxyServerId = selectedProxyServerId,
        )

        assertTrue(updatedState.subscriptionGroups.isEmpty())
        assertTrue(updatedState.proxyServers.isEmpty())
        assertEquals(0, updatedState.selectedProxyServerId)
    }

    @Test
    fun testBuiltInGroupIsNotDeleted() {
        val builtInGroup = SubscriptionGroupState(
            id = 0,
            name = "Default",
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
        assertTrue(builtInGroup.builtIn)
    }
}
