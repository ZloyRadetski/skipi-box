// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.config.ShadowrocketPolicyGroup
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.withConfigProxyGroupsReflected
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.list.isVisibleOnProxyServerList
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.proxy.server.usecase.withUpdatedSubscriptionServers
import engine.stats.maxTrafficDeltaComparedTo
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertEquals(0, plan.burstObservatorySelectors.size)
        assertEquals("30s", plan.observatoryProbeInterval)
    }

    @Test
    fun testBuildXrayOutboundPlanForLeastPingPrefersLowestLatencyFallback() {
        val server1 = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "Dead Node", id = "uuid-1", server = "s1.example.com", port = "443"),
            latency = "9999ms",
            groupId = 0,
        )
        val server2 = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Fast Node", id = "uuid-2", server = "s2.example.com", port = "443"),
            latency = "45ms",
            groupId = 0,
        )
        val group = StrategyGroup(
            remarks = "Fast Balancer",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
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
        assertEquals(1, plan.balancers.size)
        val balancer = plan.balancers.first()
        assertEquals("proxy-policy-20", balancer.fallbackTag)

        val jsonBalancers = buildXrayBalancers(plan.balancers)
        assertEquals(1, jsonBalancers.size)
        val strategySettings = jsonBalancers.first()["strategy"]?.let { it as? kotlinx.serialization.json.JsonObject }?.get("settings") as? kotlinx.serialization.json.JsonObject
        // For default leastPing, observerTag must be null to use standard observatory
        assertNull(strategySettings?.get("observerTag"))
    }

    @Test
    fun testBuildXrayOutboundPlanForBurstProbeStrategyGroup() {
        val server1 = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "Node 1", id = "uuid-1", server = "s1.example.com", port = "443"),
            groupId = 0,
        )
        val group = StrategyGroup(
            remarks = "Burst Balancer",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            enableBurstProbe = true,
            probeInterval = "45s",
            proxyServerIds = listOf(10),
        )
        val groupState = ProxyServerState(
            id = 100,
            server = group,
            groupId = 0,
        )
        val appState = AppState(
            proxyServers = listOf(server1, groupState),
            selectedProxyServerId = 100,
        )

        val plan = appState.buildXrayOutboundPlan(groupState)

        assertNotNull(plan)
        assertEquals(1, plan.burstObservatorySelectors.size)
        assertEquals(0, plan.observatorySelectors.size)
        assertEquals("burstObservatory", plan.balancers.first().observerTag)
        assertEquals("45s", plan.observatoryProbeInterval)

        val jsonBalancers = buildXrayBalancers(plan.balancers)
        val strategySettings = jsonBalancers.first()["strategy"]?.let { it as? kotlinx.serialization.json.JsonObject }?.get("settings") as? kotlinx.serialization.json.JsonObject
        assertEquals("burstObservatory", (strategySettings?.get("observerTag") as? kotlinx.serialization.json.JsonPrimitive)?.content)
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

    @Test
    fun testShadowrocketProxyGroupDisplayModes() {
        val raw = """
            [Proxy Group]
            AlwaysGroup = select, Node 1, Node 2, skipi-display=always
            ActiveConfigGroup = url-test, Node 1, Node 2, interval=300, skipi-display=active_config
            NeverGroup = fallback, Node 1, Node 2, skipi-display=never
        """.trimIndent()

        val parsed = raw.analyzeShadowrocketConfig()
        assertEquals(3, parsed.proxyGroups.size)

        val alwaysGroup = parsed.proxyGroups[0]
        val activeConfigGroup = parsed.proxyGroups[1]
        val neverGroup = parsed.proxyGroups[2]

        assertEquals(StrategyGroupDisplayMode.ALWAYS, alwaysGroup.displayMode)
        assertEquals(StrategyGroupDisplayMode.ACTIVE_CONFIG, activeConfigGroup.displayMode)
        assertEquals(StrategyGroupDisplayMode.NEVER, neverGroup.displayMode)

        val server1 = ProxyServerState(id = 10, server = VLESS(remarks = "Node 1", id = "u1", server = "s1", port = "443"), groupId = 0)
        val server2 = ProxyServerState(id = 20, server = VLESS(remarks = "Node 2", id = "u2", server = "s2", port = "443"), groupId = 0)

        val trafficConfig = TrafficConfigState(id = 1, name = "Test Config", rawConfig = raw)
        val appState = AppState(
            proxyServers = listOf(server1, server2),
            trafficConfigs = listOf(trafficConfig),
            activeTrafficConfigId = 1,
        ).withConfigProxyGroupsReflected()

        // 2 non-never groups should be materialized
        val configGroups = appState.proxyServers.filter { it.groupId == AutoBalancerGroupId }
        assertEquals(2, configGroups.size)

        val alwaysServer = configGroups.first { (it.server as StrategyGroup).remarks == "AlwaysGroup" }
        val activeConfigServer = configGroups.first { (it.server as StrategyGroup).remarks == "ActiveConfigGroup" }

        // When active config matches (1)
        assertTrue(alwaysServer.isVisibleOnProxyServerList(activeTrafficConfigId = 1))
        assertTrue(activeConfigServer.isVisibleOnProxyServerList(activeTrafficConfigId = 1))

        // When another config is active (2)
        assertTrue(alwaysServer.isVisibleOnProxyServerList(activeTrafficConfigId = 2))
        assertFalse(activeConfigServer.isVisibleOnProxyServerList(activeTrafficConfigId = 2))
    }

    @Test
    fun testStrategyGroupPreservedDuringSubscriptionUpdate() {
        val node1 = ProxyServerState(id = 10, server = VLESS(remarks = "Sub Node 1", id = "u1", server = "s1.com", port = "443"), groupId = 1)
        val node2 = ProxyServerState(id = 20, server = VLESS(remarks = "Sub Node 2", id = "u2", server = "s2.com", port = "443"), groupId = 1)
        val balancer = ProxyServerState(
            id = 100,
            server = StrategyGroup(
                remarks = "My Balancer",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = listOf(10, 20),
            ),
            groupId = AutoBalancerGroupId,
        )

        val initialAppState = AppState(
            proxyServers = listOf(node1, node2, balancer),
            subscriptionGroups = listOf(
                app.SubscriptionGroupState(
                    id = 1,
                    name = "Test Subscription",
                    url = "https://sub.example.com",
                    userAgent = "",
                    updateInterval = "",
                    enabled = true,
                )
            ),
        )

        // Updated subscription with new server instances (same fingerprint)
        val updatedNode1 = VLESS(remarks = "Sub Node 1 [Updated]", id = "u1", server = "s1.com", port = "443")
        val updatedNode2 = VLESS(remarks = "Sub Node 2 [Updated]", id = "u2", server = "s2.com", port = "443")

        val subUpdate = features.proxy.server.usecase.ProxyServerListSubscriptionUpdate(
            groupId = 1,
            sourceIdentity = features.proxy.server.usecase.SubscriptionGroupFetchIdentity(
                url = "https://sub.example.com",
                userAgent = "",
                updateInterval = "",
                ageSecretKey = "",
                updateViaProxy = false,
                enabled = true,
            ),
            urlCount = 2,
            servers = listOf(updatedNode1, updatedNode2),
        )

        val updatedState = initialAppState.withUpdatedSubscriptionServers(
            updates = listOf(subUpdate),
            updatedAtMillis = System.currentTimeMillis(),
        )

        val updatedBalancer = updatedState.proxyServers.first { it.id == 100 }
        val strategyGroup = updatedBalancer.server as StrategyGroup

        // Balancer must retain its member IDs
        assertEquals(listOf(10, 20), strategyGroup.proxyServerIds)
    }

    @Test
    fun testMaxTrafficDeltaIgnoresSmallProbeNoiseAndMaintainsStability() {
        val previousTotals = mapOf(
            "proxy-policy-1" to engine.stats.XrayTrafficBytes(uplink = 1000, downlink = 5000),
            "proxy-policy-2" to engine.stats.XrayTrafficBytes(uplink = 2000, downlink = 8000),
        )
        // Background observatory probe on node 2: ~400 bytes delta (less than 2KB)
        val probeTotals = mapOf(
            "proxy-policy-1" to engine.stats.XrayTrafficBytes(uplink = 1000, downlink = 5000),
            "proxy-policy-2" to engine.stats.XrayTrafficBytes(uplink = 2100, downlink = 8300),
        )

        // When currently active on node 1, small probe on node 2 should NOT override node 1
        val result = probeTotals.maxTrafficDeltaComparedTo(
            previous = previousTotals,
            currentActiveTag = "proxy-policy-1",
        )
        assertEquals("proxy-policy-1", result)

        // Substantial user payload traffic on node 2 (e.g. 50 KB) SHOULD switch active tag
        val userTrafficTotals = mapOf(
            "proxy-policy-1" to engine.stats.XrayTrafficBytes(uplink = 1000, downlink = 5000),
            "proxy-policy-2" to engine.stats.XrayTrafficBytes(uplink = 12000, downlink = 48000),
        )
        val switchedResult = userTrafficTotals.maxTrafficDeltaComparedTo(
            previous = previousTotals,
            currentActiveTag = "proxy-policy-1",
        )
        assertEquals("proxy-policy-2", switchedResult)
    }
}
