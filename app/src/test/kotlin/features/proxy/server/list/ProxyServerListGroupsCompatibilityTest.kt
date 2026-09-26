// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.toProxyServerListState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyServerListGroupsCompatibilityTest {
    @Test
    fun androidAdapterPreservesGroupOrderSelectionFallbackFilteringAndOriginalModels() {
        val manualServer = ProxyServerState(
            id = 10,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "Manual node", id = "m", server = "manual.example", port = "443"),
        )
        val subscriptionServer = ProxyServerState(
            id = 20,
            groupId = 2,
            server = VLESS(remarks = "Subscription node", id = "s", server = "sub.example", port = "443"),
        )
        val activeConfigBalancer = ProxyServerState(
            id = 30,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "Config owned",
                sourceTrafficConfigId = 7,
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            ),
        )
        val inactiveConfigBalancer = ProxyServerState(
            id = 31,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(
                remarks = "Inactive config",
                sourceTrafficConfigId = 8,
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            ),
        )
        val disabledGroupServer = ProxyServerState(
            id = 40,
            groupId = 3,
            server = VLESS(remarks = "Disabled node", id = "d", server = "disabled.example", port = "443"),
        )
        val state = AppState(
            proxyServers = listOf(manualServer, subscriptionServer, activeConfigBalancer, inactiveConfigBalancer, disabledGroupServer),
            subscriptionGroups = listOf(
                SubscriptionGroupState(
                    id = DefaultSubscriptionGroupId,
                    name = "Manual",
                    url = "",
                    userAgent = "",
                    updateInterval = "",
                    enabled = true,
                    builtIn = true,
                ),
                SubscriptionGroupState(
                    id = 2,
                    name = "Subscription",
                    url = "https://example.com/sub",
                    userAgent = "",
                    updateInterval = "1d",
                    enabled = true,
                ),
                SubscriptionGroupState(
                    id = 3,
                    name = "Disabled",
                    url = "https://example.com/disabled",
                    userAgent = "",
                    updateInterval = "1d",
                    enabled = false,
                ),
            ),
            activeTrafficConfigId = 7,
            enableAllProxyGroup = true,
        )

        val result = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = 3,
            searchValue = " CONFIG ",
            allGroupName = "All",
            defaultGroupName = "Default group",
            autoBalancerGroupName = "Auto balancers",
        )

        assertEquals(listOf(AutoBalancerGroupId, 2, DefaultSubscriptionGroupId), result.visibleGroups.map { it.id })
        assertEquals(listOf(AllProxyGroupId, AutoBalancerGroupId, 2, DefaultSubscriptionGroupId), result.groupTabs.map { it.id })
        assertEquals(AutoBalancerGroupId, result.selectedGroup.id)
        assertEquals(AutoBalancerGroupId, result.selectedTabId)
        assertEquals(listOf(10, 20, 30), result.visibleServers.map { it.id })
        assertEquals(listOf(30), result.currentFilteredServers.map { it.id })
        assertEquals("Default group", result.groupNames[DefaultSubscriptionGroupId])
        assertEquals("Auto balancers", result.groupNames[AutoBalancerGroupId])
        assertTrue(result.visibleGroupIds.contains(2))
        assertEquals(1, result.groupTabs.first { it.id == AutoBalancerGroupId }.serverCount)
        assertSame(activeConfigBalancer, result.visibleServers.first { it.id == activeConfigBalancer.id })
    }
}
