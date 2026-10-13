// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import app.skipi.app.proxy.ProxyServerGroupInput
import app.skipi.app.proxy.ProxyServerGroupServerInput
import app.skipi.app.proxy.ProxyServerGroupingRequest
import app.skipi.app.proxy.groupProxyServersForHome
import features.proxy.server.model.StrategyGroupDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeGroupingProjectionTest {
    @Test
    fun homeMembershipUsesVisibleRowsButOmitsConfigOwnedStrategiesFromAll() {
        val request = ProxyServerGroupingRequest(
            groups = listOf(
                ProxyServerGroupInput(id = 1, name = "Manual", enabled = true, builtIn = true),
                ProxyServerGroupInput(id = 2, name = "Work", enabled = true),
                ProxyServerGroupInput(id = 3, name = "Disabled", enabled = false),
                ProxyServerGroupInput(id = 4, name = "Empty", enabled = true),
            ),
            servers = listOf(
                ProxyServerGroupServerInput(id = 10, groupId = 1, remarks = "Manual node"),
                ProxyServerGroupServerInput(id = 11, groupId = 2, remarks = "Work node"),
                ProxyServerGroupServerInput(id = 12, groupId = 3, remarks = "Disabled node"),
                ProxyServerGroupServerInput(id = 20, groupId = -2, remarks = "User strategy", isStrategyGroup = true),
                ProxyServerGroupServerInput(
                    id = 21,
                    groupId = -2,
                    remarks = "Config strategy",
                    isStrategyGroup = true,
                    sourceTrafficConfigId = 7,
                    displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                ),
            ),
            activeTrafficConfigId = 7,
            selectedGroupId = 0,
            searchQuery = "",
            enableAllGroup = true,
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
            defaultGroupId = 1,
        )
        val grouping = groupProxyServersForHome(request)
        val memberships = proxyHomeGroupMemberships(request, grouping)

        assertEquals(
            listOf(
                ProxyHomeGroupMembership(id = 0, title = "All", serverIds = listOf(10, 11, 20)),
                ProxyHomeGroupMembership(id = -2, title = "Auto balancers", serverIds = listOf(20, 21)),
                ProxyHomeGroupMembership(id = 2, title = "Work", serverIds = listOf(11)),
                ProxyHomeGroupMembership(id = 4, title = "Empty", serverIds = emptyList()),
                ProxyHomeGroupMembership(id = 1, title = "Manual", serverIds = listOf(10)),
            ),
            memberships,
        )
        assertEquals(listOf(3, 2, 1, 0, 1), memberships.map { it.serverCount })
    }

    @Test
    fun serverSearchTextIncludesGroupTitleAndSortUsesRawRemarksWithTitleFallback() {
        assertEquals(
            "Work node 🇺🇸 Work node work.example:443 VLESS Work",
            proxyHomeServerSearchText(
                title = "Work node",
                remarks = "🇺🇸 Work node",
                address = "work.example:443",
                protocol = "VLESS",
                groupName = "Work",
            ),
        )
        assertEquals("🇺🇸 Alpha", proxyHomeServerSortKey(remarks = "🇺🇸 Alpha", title = "Alpha"))
        assertEquals("VLESS", proxyHomeServerSortKey(remarks = "", title = "VLESS"))
    }
}
