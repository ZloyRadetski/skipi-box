// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.withActiveTrafficConfigApplied
import features.config.ShadowrocketPolicyGroup
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.toEditableStrategyGroup
import features.config.toShadowrocketLine
import features.config.withConfigProxyGroupsReflected
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayOutboundPlannerBalancerTest {

    private fun createServer(id: Int, remarks: String, groupId: Int = 1): ProxyServerState {
        return ProxyServerState(
            id = id,
            groupId = groupId,
            server = VLESS(
                remarks = remarks,
                id = "4219d973-8792-462f-8747-df766f70f137",
                server = "$remarks.example.com",
                port = "443",
            ),
        )
    }

    @Test
    fun testShadowrocketPolicyGroupRespectsCustomProxyServerIds() {
        val serverNL1 = createServer(10, "Нидерланды • Стабильный")
        val serverNL2 = createServer(11, "Нидерланды • Быстрый")
        val serverRU1 = createServer(20, "Россия • Быстрый")
        val serverRU2 = createServer(21, "Россия • Маскировка")

        val rawConfig = """
            [General]
            dns-server = 8.8.8.8
            
            [Proxy Group]
            LTE RUS TORVALDS = url-test, Россия • Быстрый, Россия • Маскировка, Нидерланды • Стабильный, Нидерланды • Быстрый, url=http://cp.cloudflare.com/generate_204, interval=300
            
            [Rule]
            DOMAIN-SET,geosite:category-ru,LTE RUS TORVALDS
            FINAL,DIRECT
        """.trimIndent() + "\n"

        val trafficConfig = TrafficConfigState(
            id = 1,
            name = "Test Config",
            rawConfig = rawConfig,
        )

        // The user edited the strategy group in the UI and selected ONLY the 2 NL servers (deselected RU servers)
        val userCustomizedBalancer = ProxyServerState(
            id = 100,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "LTE RUS TORVALDS",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = listOf(10, 11), // only NL1 and NL2
                sourceTrafficConfigId = 1,
                sourcePolicyGroupName = "LTE RUS TORVALDS",
            ),
        )

        val appState = AppState(
            proxyServers = listOf(serverNL1, serverNL2, serverRU1, serverRU2, userCustomizedBalancer),
            trafficConfigs = listOf(trafficConfig),
            activeTrafficConfigId = 1,
            selectedProxyServerId = 10,
        ).withActiveTrafficConfigApplied()

        val plan = appState.buildXrayOutboundPlan(serverNL1)

        val balancer = plan.balancers.firstOrNull { it.tag == "shadowrocket-group:LTE RUS TORVALDS" }
        org.junit.Assert.assertNotNull("Balancer should be created", balancer)

        // Check the outbound tags generated for this balancer
        val balancerMemberTags = plan.proxyOutbounds
            .map { it.tag }
            .filter { it.startsWith("shadowrocket-group:LTE RUS TORVALDS-policy-") }
            .toSet()

        assertEquals(
            setOf(
                "shadowrocket-group:LTE RUS TORVALDS-policy-10",
                "shadowrocket-group:LTE RUS TORVALDS-policy-11",
            ),
            balancerMemberTags,
        )
        assertFalse(balancerMemberTags.contains("shadowrocket-group:LTE RUS TORVALDS-policy-20"))
        assertFalse(balancerMemberTags.contains("shadowrocket-group:LTE RUS TORVALDS-policy-21"))
    }

    @Test
    fun testUnconfiguredManualStrategyGroupDoesNotIncludeAllServers() {
        val server1 = createServer(1, "Server 1")
        val server2 = createServer(2, "Server 2")

        val emptyStrategyGroup = StrategyGroup(
            remarks = "Empty Balancer",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = emptyList(),
            filter = "",
            subscriptionGroupId = null,
            sourceTrafficConfigId = null,
        )

        val appState = AppState(
            proxyServers = listOf(server1, server2),
        )

        val members = appState.strategyGroupMembers(emptyStrategyGroup)
        assertTrue("Unconfigured strategy group should have no members", members.isEmpty())
    }

    @Test
    fun testWithConfigProxyGroupsReflectedExpandsWildcard() {
        val server1 = createServer(1, "Server 1")
        val server2 = createServer(2, "Server 2")

        val rawConfig = """
            [General]
            dns-server = 8.8.8.8
            
            [Proxy Group]
            AutoAll = url-test, .*, url=http://cp.cloudflare.com/generate_204, interval=300, skipi-display=always
            
            [Rule]
            FINAL,DIRECT
        """.trimIndent() + "\n"

        val trafficConfig = TrafficConfigState(
            id = 1,
            name = "Test Config",
            rawConfig = rawConfig,
        )

        val appState = AppState(
            proxyServers = listOf(server1, server2),
            trafficConfigs = listOf(trafficConfig),
            activeTrafficConfigId = 1,
        ).withConfigProxyGroupsReflected()

        val autoBalancer = appState.proxyServers.firstOrNull { it.groupId == AutoBalancerGroupId }
        org.junit.Assert.assertNotNull("Auto balancer should be generated", autoBalancer)

        val strategy = autoBalancer!!.server as StrategyGroup
        assertEquals(listOf(1, 2), strategy.proxyServerIds)
    }

    @Test
    fun testProbeTimeoutParsedAndSerializedCorrectly() {
        val rawConfig = """
            [General]
            dns-server = 8.8.8.8
            
            [Proxy Group]
            AutoTimeout = url-test, Server 1, url=http://cp.cloudflare.com/generate_204, interval=15, timeout=12
            
            [Rule]
            FINAL,DIRECT
        """.trimIndent() + "\n"

        val analyzed = rawConfig.analyzeShadowrocketConfig()
        val group = analyzed.proxyGroups.firstOrNull { it.name == "AutoTimeout" }
        org.junit.Assert.assertNotNull("Proxy group should be parsed", group)
        assertEquals(12, group!!.timeoutSeconds)

        val serverChoice = features.config.ProxyGroupServerChoice(1, "Server 1")
        val editable = group.toEditableStrategyGroup(1, listOf(serverChoice))
        assertEquals("12s", editable.probeTimeout)

        val line = editable.toShadowrocketLine(listOf(serverChoice))
        assertTrue("Serialized line must include timeout=12", line.contains("timeout=12"))
    }

    @Test
    fun testObservatoryProbeTimeoutInXrayOutboundPlan() {
        val server1 = createServer(1, "Server 1")
        val strategyGroup = StrategyGroup(
            remarks = "Balancer",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(1),
            probeInterval = "10s",
            probeTimeout = "7s",
        )
        val balancerState = ProxyServerState(
            id = 100,
            groupId = 0,
            server = strategyGroup,
        )

        val appState = AppState(
            proxyServers = listOf(server1, balancerState),
            selectedProxyServerId = 100,
        )

        val plan = appState.buildXrayOutboundPlan(balancerState)
        assertEquals("7s", plan.observatoryProbeTimeout)
    }
}

