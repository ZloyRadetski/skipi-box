// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.skipi.app.server.ProxyServerEditResult
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

class TrafficConfigGlobalGroupEditResultAndroidTest {
    @Test
    fun existingStrategyGroupResultKeepsServerIdentityIdAndAutoBalancerReturnGroup() {
        val server = StrategyGroup(remarks = "Global group")

        val result = trafficConfigGlobalProxyGroupEditResult(server, serverId = 47)

        assertStrategyGroupResult(result, server, expectedServerId = 47)
    }

    @Test
    fun newStrategyGroupResultRetainsNullServerId() {
        val server = StrategyGroup(remarks = "New global group")

        val result = trafficConfigGlobalProxyGroupEditResult(server, serverId = null)

        assertStrategyGroupResult(result, server, expectedServerId = null)
    }

    @Test
    fun nonStrategyServerDoesNotProduceAnEditResult() {
        val server: ProxyServer<*> = VLESS(
            remarks = "Non-strategy auto-row",
            id = "node-71",
            server = "node.example",
            port = "443",
        )

        assertNull(trafficConfigGlobalProxyGroupEditResult(server, serverId = 71))
    }

    private fun assertStrategyGroupResult(
        result: ProxyServerEditResult?,
        server: StrategyGroup,
        expectedServerId: Int?,
    ) {
        val editResult = requireNotNull(result)
        assertSame(server, editResult.server)
        assertEquals(expectedServerId, editResult.serverId)
        assertEquals(-2, editResult.groupId)
        assertEquals(-2, editResult.returnGroupId)
        assertFalse(editResult.deleted)
    }
}
