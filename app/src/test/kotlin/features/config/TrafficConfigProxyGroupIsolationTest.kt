// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.toProxyServerListState
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.list.isVisibleOnProxyServerList
import features.proxy.server.list.proxyServerListGroups
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import engine.xray.buildXrayOutboundPlan
import features.proxy.server.model.StrategyGroupConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficConfigProxyGroupIsolationTest {

    @Test
    fun withConfigProxyGroupsReflected_only_reflects_active_config_groups() {
        val config1Text = """
            [Proxy Group]
            Config1Group = select, DIRECT, REJECT, skipi-display=active_config
        """.trimIndent()

        val config2Text = """
            [Proxy Group]
            Config2Group = url-test, DIRECT, REJECT, skipi-display=active_config
        """.trimIndent()

        val config1 = TrafficConfigState(id = 1, name = "Config 1", rawConfig = config1Text)
        val config2 = TrafficConfigState(id = 2, name = "Config 2", rawConfig = config2Text)

        // When no config is active (activeTrafficConfigId = 0)
        val stateNoActive = AppState(
            trafficConfigs = listOf(config1, config2),
            activeTrafficConfigId = 0,
        ).withConfigProxyGroupsReflected()

        val generatedNoActive = stateNoActive.proxyServers.filter {
            (it.server as? StrategyGroup)?.sourceTrafficConfigId != null
        }
        assertTrue("No proxy groups should be generated when activeTrafficConfigId is 0", generatedNoActive.isEmpty())

        // When config 1 is active
        val stateActive1 = AppState(
            trafficConfigs = listOf(config1, config2),
            activeTrafficConfigId = 1,
        ).withConfigProxyGroupsReflected()

        val generated1 = stateActive1.proxyServers.filter {
            (it.server as? StrategyGroup)?.sourceTrafficConfigId != null
        }
        assertEquals(1, generated1.size)
        assertEquals("Config1Group", (generated1.first().server as StrategyGroup).remarks)
        assertEquals(1, (generated1.first().server as StrategyGroup).sourceTrafficConfigId)

        // When config 2 is active
        val stateActive2 = AppState(
            trafficConfigs = listOf(config1, config2),
            activeTrafficConfigId = 2,
        ).withConfigProxyGroupsReflected()

        val generated2 = stateActive2.proxyServers.filter {
            (it.server as? StrategyGroup)?.sourceTrafficConfigId != null
        }
        assertEquals(1, generated2.size)
        assertEquals("Config2Group", (generated2.first().server as StrategyGroup).remarks)
        assertEquals(2, (generated2.first().server as StrategyGroup).sourceTrafficConfigId)
    }

    @Test
    fun isVisibleOnProxyServerList_hides_inactive_config_groups() {
        val activeGroupServer = ProxyServerState(
            id = 101,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "ActiveGroup",
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                sourceTrafficConfigId = 1,
            ),
        )

        val inactiveGroupServer = ProxyServerState(
            id = 102,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "InactiveGroup",
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                sourceTrafficConfigId = 2,
            ),
        )

        val manualUserStrategyGroup = ProxyServerState(
            id = 103,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "UserManualBalancer",
                sourceTrafficConfigId = null,
            ),
        )

        assertTrue(activeGroupServer.isVisibleOnProxyServerList(activeTrafficConfigId = 1))
        assertFalse(inactiveGroupServer.isVisibleOnProxyServerList(activeTrafficConfigId = 1))
        assertTrue(manualUserStrategyGroup.isVisibleOnProxyServerList(activeTrafficConfigId = 1))
        assertTrue(manualUserStrategyGroup.isVisibleOnProxyServerList(activeTrafficConfigId = 0))
    }

    @Test
    fun all_tab_excludes_config_internal_proxy_groups() {
        val regularServer = ProxyServerState(
            id = 10,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "Node 1", id = "u1", server = "s1", port = "443"),
        )

        val configGroupServer = ProxyServerState(
            id = 101,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "ActiveConfigGroup",
                sourceTrafficConfigId = 1,
            ),
        )

        val userManualStrategyGroup = ProxyServerState(
            id = 102,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "UserManualBalancer",
                sourceTrafficConfigId = null,
            ),
        )

        val appState = AppState(
            proxyServers = listOf(regularServer, configGroupServer, userManualStrategyGroup),
            subscriptionGroups = listOf(
                SubscriptionGroupState(
                    id = DefaultSubscriptionGroupId,
                    name = "Default",
                    url = "",
                    userAgent = "",
                    updateInterval = "",
                    enabled = true,
                ),
                SubscriptionGroupState(
                    id = 2,
                    name = "Subscription 1",
                    url = "https://example.com/sub",
                    userAgent = "",
                    updateInterval = "1d",
                    enabled = true,
                ),
            ),
            activeTrafficConfigId = 1,
            enableAllProxyGroup = true,
        )

        val groups = proxyServerListGroups(
            state = appState.toProxyServerListState(),
            selectedGroupId = 0, // AllProxyGroupId
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Default",
            autoBalancerGroupName = "Auto",
        )

        // In the "All" tab (selectedGroupId = 0), configGroupServer should be excluded, but regularServer and userManualStrategyGroup should remain
        val remarks = groups.currentGroupServers.map { it.server.getInfo().remarks }
        assertTrue("Node 1 should be in All tab", remarks.contains("Node 1"))
        assertTrue("UserManualBalancer should be in All tab", remarks.contains("UserManualBalancer"))
        assertFalse("ActiveConfigGroup should NOT be in All tab", remarks.contains("ActiveConfigGroup"))
    }

    @Test
    fun trafficConfigPage_global_proxy_groups_excludes_config_sourced_groups() {
        val configGroupServer = ProxyServerState(
            id = 101,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "ConfigGroup",
                sourceTrafficConfigId = 1,
            ),
        )

        val userManualStrategyGroup = ProxyServerState(
            id = 102,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "UserManualBalancer",
                sourceTrafficConfigId = null,
            ),
        )

        val appState = AppState(
            proxyServers = listOf(configGroupServer, userManualStrategyGroup),
        )

        val globalProxyGroups = appState.proxyServers.filter {
            val serverImpl = it.server
            it.groupId == AutoBalancerGroupId &&
                serverImpl is StrategyGroup &&
                serverImpl.sourceTrafficConfigId == null
        }

        assertEquals(1, globalProxyGroups.size)
        assertEquals("UserManualBalancer", (globalProxyGroups.first().server as StrategyGroup).remarks)
    }

    @Test
    fun withConfigProxyGroupsReflected_resolves_members_with_quotes_and_country_flags() {
        val node1 = ProxyServerState(
            id = 10,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "🇭🇰 HK Fast", id = "u1", server = "s1", port = "443"),
        )
        val node2 = ProxyServerState(
            id = 20,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "SG Clean", id = "u2", server = "s2", port = "443"),
        )

        val configText = """
            [Proxy Group]
            AutoProxy = select, "HK Fast", "🇸🇬 SG Clean", skipi-display=always
        """.trimIndent()

        val config = TrafficConfigState(id = 1, name = "Config 1", rawConfig = configText)

        val state = AppState(
            proxyServers = listOf(node1, node2),
            trafficConfigs = listOf(config),
            activeTrafficConfigId = 1,
        ).withConfigProxyGroupsReflected()

        val generatedGroup = state.proxyServers.firstOrNull {
            (it.server as? StrategyGroup)?.sourceTrafficConfigId == 1
        }

        assertTrue("Generated group must exist", generatedGroup != null)
        val strategy = generatedGroup!!.server as StrategyGroup
        assertEquals("AutoProxy", strategy.remarks)
        assertEquals(listOf(10, 20), strategy.proxyServerIds)
        assertEquals(10, strategy.selectedMemberId)
    }

    @Test
    fun hysteria2_toXrayOutbound_fallbacks_serverName_and_normalizes_bandwidth() {
        val hy2 = features.proxy.server.model.Hysteria2(
            remarks = "Hy2 Node",
            server = "hy2.example.com",
            port = "443",
            auth = "pass123",
            up = "100",
            down = "200",
            mport = "20000-30000",
            mportHopInt = "2", // should be coerced to >= 5
        )

        val outbound = hy2.toXrayOutbound("proxy")
        val streamSettings = outbound.streamSettings ?: error("missing streamSettings")
        val tlsSettings = streamSettings["tlsSettings"]?.let { it as kotlinx.serialization.json.JsonObject }
        assertEquals("hy2.example.com", tlsSettings?.get("serverName")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })

        val finalmask = streamSettings["finalmask"]?.let { it as kotlinx.serialization.json.JsonObject }
        val quicParams = finalmask?.get("quicParams")?.let { it as kotlinx.serialization.json.JsonObject }
        assertEquals("100 mbps", quicParams?.get("brutalUp")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
        assertEquals("200 mbps", quicParams?.get("brutalDown")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })

        val udpHop = quicParams?.get("udpHop")?.let { it as kotlinx.serialization.json.JsonObject }
        assertEquals(5, udpHop?.get("interval")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() })
    }

    @Test
    fun balancer_plan_picks_alive_node_as_fallback_ignoring_failed_nodes() {
        val deadNode1 = ProxyServerState(
            id = 1,
            groupId = DefaultSubscriptionGroupId,
            latency = "Failed (timeout)",
            server = VLESS(remarks = "Dead Node 1", id = "u1", server = "s1", port = "443"),
        )
        val deadNode2 = ProxyServerState(
            id = 2,
            groupId = DefaultSubscriptionGroupId,
            latency = "-1ms",
            server = VLESS(remarks = "Dead Node 2", id = "u2", server = "s2", port = "443"),
        )
        val aliveNode = ProxyServerState(
            id = 3,
            groupId = DefaultSubscriptionGroupId,
            latency = "120ms",
            server = VLESS(remarks = "Fast Alive Node", id = "u3", server = "s3", port = "443"),
        )

        val autoBalancer = ProxyServerState(
            id = 100,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "UrlTestGroup",
                strategy = features.proxy.server.model.StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = listOf(1, 2, 3),
            ),
        )

        val appState = AppState(
            proxyServers = listOf(deadNode1, deadNode2, aliveNode, autoBalancer),
            activeTrafficConfigId = 0,
        )

        val plan = appState.buildXrayOutboundPlan(autoBalancer)
        val balancer = plan.balancers.firstOrNull { it.tag == "proxy" }
        assertNotNull(balancer)
        assertEquals("proxy-policy-3", balancer!!.fallbackTag)
    }

    @Test
    fun withActiveTrafficConfig_switches_active_groups_seamlessly() {
        val config1Text = """
            [Proxy Group]
            Config1Group = select, DIRECT, REJECT, skipi-display=active_config
        """.trimIndent()

        val config2Text = """
            [Proxy Group]
            Config2Group = url-test, DIRECT, REJECT, skipi-display=active_config
        """.trimIndent()

        val config1 = TrafficConfigState(id = 1, name = "Config 1", rawConfig = config1Text)
        val config2 = TrafficConfigState(id = 2, name = "Config 2", rawConfig = config2Text)

        // Start with config 1 active
        var state = AppState(
            trafficConfigs = listOf(config1, config2),
            activeTrafficConfigId = 1,
        ).withConfigProxyGroupsReflected()

        assertEquals(1, state.proxyServers.filter { (it.server as? StrategyGroup)?.sourceTrafficConfigId != null }.size)
        val group1 = state.proxyServers.first { (it.server as? StrategyGroup)?.sourceTrafficConfigId != null }
        assertEquals("Config1Group", (group1.server as StrategyGroup).remarks)
        assertTrue(group1.isVisibleOnProxyServerList(state.activeTrafficConfigId))

        // Switch to config 2 via withActiveTrafficConfig
        state = state.withActiveTrafficConfig(2)

        assertEquals(2, state.activeTrafficConfigId)
        val generatedGroups2 = state.proxyServers.filter { (it.server as? StrategyGroup)?.sourceTrafficConfigId != null }
        assertEquals(1, generatedGroups2.size)
        val group2 = generatedGroups2.first()
        assertEquals("Config2Group", (group2.server as StrategyGroup).remarks)
        assertEquals(2, (group2.server as StrategyGroup).sourceTrafficConfigId)
        assertTrue(group2.isVisibleOnProxyServerList(state.activeTrafficConfigId))

        // Switch back to config 1
        state = state.withActiveTrafficConfig(1)

        assertEquals(1, state.activeTrafficConfigId)
        val generatedGroups1 = state.proxyServers.filter { (it.server as? StrategyGroup)?.sourceTrafficConfigId != null }
        assertEquals(1, generatedGroups1.size)
        assertEquals("Config1Group", (generatedGroups1.first().server as StrategyGroup).remarks)
    }
}
