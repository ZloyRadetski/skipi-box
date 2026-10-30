// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.toProxyServerListState
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import org.junit.Assert.assertEquals
import org.junit.Test
import platform.TunnelPhase
import platform.TunnelSnapshot

class ProxyServerListHomeGroupsTest {
    private val manualGroup = SubscriptionGroupState(
        id = DefaultSubscriptionGroupId,
        name = "Manual",
        url = "",
        userAgent = "",
        updateInterval = "",
        enabled = true,
        builtIn = true,
    )
    private val workGroup = SubscriptionGroupState(
        id = 2,
        name = "Work",
        url = "https://example.com/work",
        userAgent = "",
        updateInterval = "",
        enabled = true,
    )
    private val state = AppState(
        subscriptionGroups = listOf(manualGroup, workGroup),
        proxyServers = listOf(
            ProxyServerState(
                id = 10,
                server = VLESS(remarks = "Manual node", id = "m", server = "manual.example", port = "443"),
                groupId = DefaultSubscriptionGroupId,
            ),
            ProxyServerState(
                id = 11,
                server = VLESS(remarks = "Work node", id = "w", server = "work.example", port = "443"),
                groupId = workGroup.id,
            ),
        ),
        enableAllProxyGroup = true,
        selectedProxyServerId = 10,
        showServerSearch = true,
    )

    private fun homeInputFor(selectedGroupId: Int, searchValue: String) = state.toProxyHomeInput(
        groupState = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = selectedGroupId,
            searchValue = searchValue,
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        ),
        tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Disconnected),
        tunnelBusy = false,
        isTestingLatency = false,
    )

    /** Home filters and pages by itself, so swiping or typing must not change what the host feeds it. */
    @Test
    fun homeInputIgnoresSelectedGroupAndSearch() {
        val reference = homeInputFor(selectedGroupId = DefaultSubscriptionGroupId, searchValue = "")

        assertEquals(reference, homeInputFor(selectedGroupId = workGroup.id, searchValue = ""))
        assertEquals(reference, homeInputFor(selectedGroupId = AllProxyGroupId, searchValue = "work"))
        assertEquals(reference, homeInputFor(selectedGroupId = workGroup.id, searchValue = "nothing matches"))
    }

    @Test
    fun selectionIndependentGroupsFeedTheSameHomeInput() {
        val homeGroups = proxyServerListHomeGroups(
            state = state.toProxyServerListState(),
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )

        val input = state.toProxyHomeInput(
            groupState = homeGroups,
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Disconnected),
            tunnelBusy = false,
            isTestingLatency = false,
        )

        assertEquals(homeInputFor(workGroup.id, "work"), input)
    }
}
