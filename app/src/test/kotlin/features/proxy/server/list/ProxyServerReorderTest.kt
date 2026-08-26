// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import features.proxy.server.model.VLESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProxyServerReorderTest {

    private fun createServer(id: Int, remarks: String, groupId: Int = AutoBalancerGroupId): ProxyServerState {
        return ProxyServerState(
            id = id,
            groupId = groupId,
            server = VLESS(
                remarks = remarks,
                id = "4219d973-8792-462f-8747-df766f70f137",
                server = "example.com",
                port = "443",
            ),
        )
    }

    @Test
    fun testReorderVisibleServerByIdMovesForward() {
        val server1 = createServer(101, "Balancer 1")
        val server2 = createServer(102, "Balancer 2")
        val server3 = createServer(103, "Balancer 3")
        val list = listOf(server1, server2, server3)

        val reordered = list.reorderVisibleServerById(fromServerId = 101, toServerId = 103)
        assertEquals(listOf(102, 103, 101), reordered.map { it.id })
    }

    @Test
    fun testReorderVisibleServerByIdMovesBackward() {
        val server1 = createServer(101, "Balancer 1")
        val server2 = createServer(102, "Balancer 2")
        val server3 = createServer(103, "Balancer 3")
        val list = listOf(server1, server2, server3)

        val reordered = list.reorderVisibleServerById(fromServerId = 103, toServerId = 101)
        assertEquals(listOf(103, 101, 102), reordered.map { it.id })
    }

    @Test
    fun testReorderVisibleServerByIdWithUnknownOrSameIdReturnsSameList() {
        val server1 = createServer(101, "Balancer 1")
        val server2 = createServer(102, "Balancer 2")
        val list = listOf(server1, server2)

        assertSame(list, list.reorderVisibleServerById(fromServerId = 101, toServerId = 101))
        assertSame(list, list.reorderVisibleServerById(fromServerId = 999, toServerId = 101))
        assertSame(list, list.reorderVisibleServerById(fromServerId = 101, toServerId = 999))
    }

    @Test
    fun testReorderVisibleServerByIdInMixedGroupList() {
        val regular1 = createServer(1, "Server 1", groupId = 0)
        val balancer1 = createServer(10, "AutoBalancer 1", groupId = AutoBalancerGroupId)
        val balancer2 = createServer(20, "AutoBalancer 2", groupId = AutoBalancerGroupId)
        val balancer3 = createServer(30, "AutoBalancer 3", groupId = AutoBalancerGroupId)
        val regular2 = createServer(2, "Server 2", groupId = 0)
        val list = listOf(regular1, balancer1, balancer2, balancer3, regular2)

        val reordered = list.reorderVisibleServerById(fromServerId = 10, toServerId = 30)
        assertEquals(listOf(1, 20, 30, 10, 2), reordered.map { it.id })
    }
}
