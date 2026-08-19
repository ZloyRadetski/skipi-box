// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XrayConfigTest {

    @Test
    fun testBuildXrayOutboundPlanForSingleVlessServer() {
        val vless = VLESS(
            remarks = "Test VLESS",
            id = "4219d973-8792-462f-8747-df766f70f137",
            server = "vless.example.com",
            port = "443",
        )
        val serverState = ProxyServerState(
            id = 1,
            server = vless,
            groupId = 0,
        )
        val appState = AppState(
            proxyServers = listOf(serverState),
            selectedProxyServerId = 1,
        )

        val plan = appState.buildXrayOutboundPlan(serverState)

        assertNotNull(plan)
        assertEquals(1, plan.proxyOutbounds.size)
        assertEquals("proxy", plan.proxyOutbounds.first().tag)
    }

    @Test
    fun testBuildXrayOutboundPlanForLeastPingStrategyGroup() {
        val server1 = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "Node 1", id = "uuid-1", server = "s1.example.com", port = "443"),
            groupId = 0,
        )
        val server2 = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Node 2", id = "uuid-2", server = "s2.example.com", port = "443"),
            groupId = 0,
        )
        val group = StrategyGroup(
            remarks = "Auto Balancer",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            probeUrl = "https://www.gstatic.com/generate_204",
            probeInterval = "30s",
            proxyServerIds = listOf(10, 20),
        )
        val groupState = ProxyServerState(
            id = 100,
            server = group,
            groupId = 0,
        )
        val appState = AppState(
            proxyServers = listOf(server1, server2, groupState),
            selectedProxyServerId = 100,
        )

        val plan = appState.buildXrayOutboundPlan(groupState)

        assertNotNull(plan)
        assertEquals(2, plan.proxyOutbounds.size)
        assertTrue(plan.balancers.isNotEmpty())
        assertEquals("leastPing", plan.balancers.first().strategy)
        assertEquals(1, plan.observatorySelectors.size)
    }

    @Test
    fun testBuildXrayOutboundPlanForSelectStrategyGroup() {
        val server1 = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "Node 1", id = "uuid-1", server = "s1.example.com", port = "443"),
            groupId = 0,
        )
        val server2 = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Node 2", id = "uuid-2", server = "s2.example.com", port = "443"),
            groupId = 0,
        )
        val group = StrategyGroup(
            remarks = "Manual Select Group",
            strategy = StrategyGroupConstants.TYPE_SELECT,
            selectedMemberId = 20,
            proxyServerIds = listOf(10, 20),
        )
        val groupState = ProxyServerState(
            id = 100,
            server = group,
            groupId = 0,
        )
        val appState = AppState(
            proxyServers = listOf(server1, server2, groupState),
            selectedProxyServerId = 100,
        )

        val plan = appState.buildXrayOutboundPlan(groupState)

        assertNotNull(plan)
        assertEquals(1, plan.proxyOutbounds.size)
        assertEquals("proxy", plan.proxyOutbounds.first().tag)
    }
}
