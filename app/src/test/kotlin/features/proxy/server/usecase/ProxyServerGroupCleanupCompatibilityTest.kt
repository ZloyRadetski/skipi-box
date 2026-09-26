// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.ProxyServerState
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.ProxyServer
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class ProxyServerGroupCleanupCompatibilityTest {
    @Test
    fun duplicateAdapterKeepsSelectedRecordAndItsLatencyAndLeavesOtherGroupsUntouched() {
        val url = "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node"
        val first = state(1, 4, ProxyServer.parse(url), "12 ms")
        val selected = state(2, 4, ProxyServer.parse(url), "30 ms")
        val otherGroup = state(3, 5, ProxyServer.parse(url), "50 ms")

        val result = listOf(first, selected, otherGroup).deleteDuplicateServersInGroup(
            currentGroupServerIds = setOf(first.id, selected.id),
            selectedProxyServerId = selected.id,
        )

        assertEquals(1, result.removedCount)
        assertEquals(listOf(selected, otherGroup), result.servers)
        assertSame(selected, result.servers.first())
        assertEquals("30 ms", result.servers.first().latency)
        assertSame(otherGroup, result.servers.last())
    }

    @Test
    fun invalidAdapterRetainsValidCompositeReferencesAndReportsRemovedIds() {
        val invalid = state(1, 4, HTTP(port = ""), "")
        val chain = state(2, 4, ChainProxy(remarks = "Valid chain", proxyServerIds = listOf(3, 4)), "")
        val outsideGroup = state(3, 5, HTTP(port = ""), "")

        val result = listOf(invalid, chain, outsideGroup).deleteInvalidServersInGroup(setOf(1, 2))

        assertEquals(1, result.removedCount)
        assertEquals(setOf(1), result.removedServerIds)
        assertEquals(listOf(chain, outsideGroup), result.servers)
        assertSame(chain, result.servers.first())
    }

    private fun state(id: Int, groupId: Int, server: ProxyServer<*>, latency: String) =
        ProxyServerState(id = id, groupId = groupId, server = server, latency = latency)
}
