// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.StrategyGroupDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyServerGroupingTest {
    @Test
    fun ordersAutoBalancerSubscriptionsAndManualGroupsAndCountsBeforeSearch() {
        val result = groupProxyServersForHome(
            request(
                groups = listOf(manualGroup(), group(2, "Subscription", enabled = true), group(3, "Disabled", enabled = false)),
                servers = listOf(
                    server(1, groupId = 1, remarks = "Manual"),
                    server(2, groupId = 2, remarks = "One"),
                    server(3, groupId = 2, remarks = "Two"),
                    server(4, groupId = 3, remarks = "Hidden"),
                    server(5, groupId = -2, remarks = "Balancer", strategy = true),
                ),
                selectedGroupId = 0,
                searchQuery = " two ",
                enableAllGroup = true,
            ),
        )

        assertEquals(listOf(-2, 2, 1), result.visibleGroups.map { it.id })
        assertEquals(listOf(0, -2, 2, 1), result.groupTabs.map { it.id })
        assertEquals(listOf(4, 1, 2, 1), result.groupTabs.map { it.serverCount })
        assertEquals(0, result.selectedTabIndex)
        assertEquals(0, result.selectedTabId)
        assertEquals(-2, result.selectedGroupId)
        assertEquals(listOf(1, 2, 3, 5), result.visibleServerIds)
        assertEquals(listOf(1, 2, 3, 5), result.currentGroupServerIds)
        assertEquals(listOf(3), result.currentFilteredServerIds)
        assertFalse(result.visibleGroupIds.contains(3))
        assertEquals("Default group", result.groupNames[1])
    }

    @Test
    fun selectedAllTabKeepsAllIdAndResolvesBackingGroupToFirstVisibleGroup() {
        val request = request(
            groups = listOf(manualGroup(), group(2, "Subscription", enabled = true)),
            servers = listOf(server(1, groupId = 1, remarks = "Manual"), server(2, groupId = 2, remarks = "Sub")),
            selectedGroupId = 0,
            enableAllGroup = true,
        )

        val withAll = groupProxyServersForHome(request)
        val withoutAll = groupProxyServersForHome(request.copy(enableAllGroup = false))

        assertTrue(withAll.isAllGroupsSelected)
        assertEquals(0, withAll.selectedTabId)
        assertEquals(2, withAll.selectedGroupId)
        assertEquals(listOf(1, 2), withAll.currentGroupServerIds)
        assertFalse(withoutAll.isAllGroupsSelected)
        assertEquals(2, withoutAll.selectedTabId)
    }

    @Test
    fun allTabExcludesConfigOwnedStrategyServersButTabCountIncludesVisibleOnes() {
        val result = groupProxyServersForHome(
            request(
                groups = listOf(manualGroup(), group(2, "Subscription", enabled = true)),
                servers = listOf(
                    server(1, groupId = 1, remarks = "Manual"),
                    server(2, groupId = -2, remarks = "Profile balancer", strategy = true, sourceConfigId = 7, displayMode = StrategyGroupDisplayMode.ALWAYS),
                    server(3, groupId = -2, remarks = "User balancer", strategy = true, sourceConfigId = null, displayMode = StrategyGroupDisplayMode.NEVER),
                    server(4, groupId = -2, remarks = "Hidden profile", strategy = true, sourceConfigId = 8, displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG),
                ),
                activeConfigId = 7,
                selectedGroupId = 0,
                enableAllGroup = true,
            ),
        )

        assertEquals(3, result.groupTabs.first().serverCount)
        assertEquals(listOf(1, 3), result.currentGroupServerIds)
        assertEquals(listOf(1, 2, 3), result.visibleServerIds)
    }

    @Test
    fun missingSelectionFallsBackToFirstVisibleThenManualGroup() {
        val visible = groupProxyServersForHome(
            request(
                groups = listOf(manualGroup(), group(2, "Subscription", enabled = true)),
                servers = listOf(server(1, groupId = 1, remarks = "Manual")),
                selectedGroupId = 99,
            ),
        )
        val manualFallback = groupProxyServersForHome(
            request(groups = listOf(manualGroup()), servers = emptyList(), selectedGroupId = 99),
        )
        val noManualGroup = groupProxyServersForHome(
            request(groups = emptyList(), servers = emptyList(), selectedGroupId = 99),
        )

        assertEquals(2, visible.selectedGroupId)
        assertEquals(1, manualFallback.selectedGroupId)
        assertEquals(1, manualFallback.selectedTabId)
        assertNull(noManualGroup.selectedGroupId)
        assertEquals(1, noManualGroup.selectedTabId)
    }

    @Test
    fun preservesAndroidGroupOrderIncludesEnabledEmptyGroupsAndHidesDisabledMembership() {
        val request = request(
            groups = listOf(
                manualGroup(),
                group(2, "First subscription", enabled = true),
                group(3, "Disabled subscription", enabled = false),
                group(4, "Empty subscription", enabled = true),
            ),
            servers = listOf(
                server(10, groupId = 1, remarks = "Manual"),
                server(20, groupId = 2, remarks = "Subscription"),
                server(30, groupId = 3, remarks = "Disabled row"),
                server(40, groupId = -2, remarks = "User balancer", strategy = true),
                server(
                    50,
                    groupId = -2,
                    remarks = "Inactive config balancer",
                    strategy = true,
                    sourceConfigId = 8,
                    displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                ),
                server(
                    51,
                    groupId = -2,
                    remarks = "Active config balancer",
                    strategy = true,
                    sourceConfigId = 7,
                    displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                ),
            ),
            activeConfigId = 7,
            selectedGroupId = 3,
            enableAllGroup = true,
        )
        val result = groupProxyServersForHome(request)
        val allResult = groupProxyServersForHome(request.copy(selectedGroupId = 0))

        assertEquals(listOf(-2, 2, 4, 1), result.visibleGroups.map { it.id })
        assertEquals(listOf(0, -2, 2, 4, 1), result.groupTabs.map { it.id })
        // Raw All tab count includes every visible row, while Home's All membership omits config-owned strategies.
        assertEquals(listOf(4, 2, 1, 0, 1), result.groupTabs.map { it.serverCount })
        assertEquals(listOf(10, 20, 40, 51), result.visibleServerIds)
        assertEquals(listOf(40, 51), result.currentGroupServerIds)
        assertEquals(-2, result.selectedGroupId)
        assertEquals(-2, result.selectedTabId)
        assertEquals(1, result.selectedTabIndex)
        assertEquals(listOf(10, 20, 40), allResult.currentGroupServerIds)
    }

    @Test
    fun androidContractFixturePreservesConfigDisplayModesAndHomeGroupOrder() {
        val request = ProxyServerGroupingAndroidContractFixture.request
        val result = groupProxyServersForHome(request)

        assertEquals(listOf(-2, 8, 4, 2, 1), result.visibleGroups.map { it.id })
        assertEquals(listOf(0, -2, 8, 4, 2, 1), result.groupTabs.map { it.id })
        assertEquals(listOf(6, 3, 1, 0, 1, 1), result.groupTabs.map { it.serverCount })
        assertEquals(listOf(10, 20, 21, 40, 50, 52), result.visibleServerIds)
        assertEquals(listOf(10, 20, 21, 40), result.currentGroupServerIds)
        assertEquals(-2, result.selectedGroupId)
        assertEquals(0, result.selectedTabId)
        assertEquals(0, result.selectedTabIndex)

        val autoBalancers = groupProxyServersForHome(request.copy(selectedGroupId = -2))
        assertEquals(listOf(40, 50, 52), autoBalancers.currentGroupServerIds)
        assertEquals(-2, autoBalancers.selectedTabId)
        assertEquals(1, autoBalancers.selectedTabIndex)

        val invalidSelection = groupProxyServersForHome(request.copy(selectedGroupId = 3))
        assertEquals(-2, invalidSelection.selectedGroupId)
        assertEquals(-2, invalidSelection.selectedTabId)
    }

    @Test
    fun androidContractFixtureSearchFiltersRowsAfterGroupCountsAndAllMembership() {
        val request = ProxyServerGroupingAndroidContractFixture.request

        val searchedAll = groupProxyServersForHome(request.copy(searchQuery = " node "))
        assertEquals(listOf(6, 3, 1, 0, 1, 1), searchedAll.groupTabs.map { it.serverCount })
        assertEquals(listOf(10, 20, 21), searchedAll.currentFilteredServerIds)

        val searchedAuto = groupProxyServersForHome(
            request.copy(selectedGroupId = -2, searchQuery = " CONFIG "),
        )
        assertEquals(listOf(50, 52), searchedAuto.currentFilteredServerIds)
    }

    private fun request(
        groups: List<ProxyServerGroupInput>,
        servers: List<ProxyServerGroupServerInput>,
        activeConfigId: Int? = null,
        selectedGroupId: Int,
        searchQuery: String = "",
        enableAllGroup: Boolean = false,
    ) = ProxyServerGroupingRequest(
        groups = groups,
        servers = servers,
        activeTrafficConfigId = activeConfigId,
        selectedGroupId = selectedGroupId,
        searchQuery = searchQuery,
        enableAllGroup = enableAllGroup,
        allGroupName = "All",
        defaultGroupName = "Default group",
        autoBalancerGroupName = "Auto balancers",
        defaultGroupId = 1,
    )

    private fun manualGroup() = group(1, "Default", enabled = true, builtIn = true)

    private fun group(id: Int, name: String, enabled: Boolean, builtIn: Boolean = false) =
        ProxyServerGroupInput(id, name, enabled, builtIn)

    private fun server(
        id: Int,
        groupId: Int,
        remarks: String,
        strategy: Boolean = false,
        sourceConfigId: Int? = null,
        displayMode: String = StrategyGroupDisplayMode.ALWAYS,
    ) = ProxyServerGroupServerInput(
        id = id,
        groupId = groupId,
        remarks = remarks,
        isStrategyGroup = strategy,
        sourceTrafficConfigId = sourceConfigId,
        displayMode = displayMode,
    )
}
