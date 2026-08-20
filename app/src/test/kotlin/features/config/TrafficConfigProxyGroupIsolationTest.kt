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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            it.groupId == AutoBalancerGroupId &&
                it.server is StrategyGroup &&
                it.server.sourceTrafficConfigId == null
        }

        assertEquals(1, globalProxyGroups.size)
        assertEquals("UserManualBalancer", (globalProxyGroups.first().server as StrategyGroup).remarks)
    }
}
