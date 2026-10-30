// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyServerSummary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProxyHomeAvailableActionsTest {
    private val server = ProxyServerSummary(id = "1", title = "One", address = "a:1", protocol = "vless")
    private val otherServer = server.copy(id = "2", title = "Two")
    private val subscription = DesktopStoredSubscription(id = 7, url = "https://example.test/sub")

    private fun actions(
        servers: List<ProxyServerSummary> = emptyList(),
        subscriptions: List<DesktopStoredSubscription> = emptyList(),
        canToggleTunnel: Boolean = false,
        sortMode: ProxyHomeSortMode = ProxyHomeSortMode.Default,
        updatingSubscription: Boolean = false,
        pingingSubscriptionIds: Set<Int> = emptySet(),
    ): Set<ProxyHomeActionId> = desktopProxyHomeAvailableActions(
        capabilityActions = emptySet(),
        servers = servers,
        hasStrategyGroup = false,
        subscriptions = subscriptions,
        groupCatalog = DesktopProxyGroups.create(
            DesktopServerLibrary(),
            DesktopSubscriptionLibrary(subscriptions = subscriptions),
            DesktopProxyGroupOptions(activeTrafficConfigId = null, enableAllProxyGroup = false),
        ),
        canToggleTunnel = canToggleTunnel,
        hasCancelableLatencyTests = false,
        pingingSubscriptionIds = pingingSubscriptionIds,
        updatingSubscription = updatingSubscription,
        sortMode = sortMode,
    )

    @Test
    fun emptyHomeOffersOnlyCreationAndGroupEditing() {
        val result = actions()
        assertTrue(ProxyHomeActionId.SelectServer in result)
        assertTrue(ProxyHomeActionId.AddServer in result)
        assertTrue(ProxyHomeActionId.AddSubscription in result)
        assertTrue(ProxyHomeActionId.ImportServers in result)
        assertTrue(ProxyHomeActionId.SetSort in result)
        assertTrue(ProxyHomeActionId.EditGroup in result)
        assertFalse(ProxyHomeActionId.EditServer in result)
        assertFalse(ProxyHomeActionId.TestServer in result)
        assertFalse(ProxyHomeActionId.DeleteGroup in result)
        assertFalse(ProxyHomeActionId.ToggleTunnel in result)
    }

    @Test
    fun toggleTunnelFollowsCapability() {
        assertTrue(ProxyHomeActionId.ToggleTunnel in actions(canToggleTunnel = true))
    }

    @Test
    fun serversEnableEditDeleteCopyAndTesting() {
        val result = actions(servers = listOf(server))
        assertTrue(ProxyHomeActionId.EditServer in result)
        assertTrue(ProxyHomeActionId.DeleteServer in result)
        assertTrue(ProxyHomeActionId.CopyServer in result)
        assertTrue(ProxyHomeActionId.TestServer in result)
        assertTrue(ProxyHomeActionId.TestVisibleServers in result)
        assertTrue(ProxyHomeActionId.TestGroup in result)
    }

    @Test
    fun reorderingNeedsDefaultSortAndMoreThanOneServer() {
        val two = listOf(server, otherServer)
        assertTrue(ProxyHomeActionId.ReorderServer in actions(servers = two))
        assertTrue(ProxyHomeActionId.MoveServer in actions(servers = two))
        assertFalse(ProxyHomeActionId.ReorderServer in actions(servers = listOf(server)))
        assertFalse(ProxyHomeActionId.MoveServer in actions(servers = two, sortMode = ProxyHomeSortMode.Name))
    }

    @Test
    fun subscriptionRefreshNeedsAUrlAndNoRunningUpdate() {
        assertTrue(ProxyHomeActionId.RefreshSubscription in actions(subscriptions = listOf(subscription)))
        assertFalse(
            ProxyHomeActionId.RefreshSubscription in
                actions(subscriptions = listOf(subscription), updatingSubscription = true),
        )
        assertFalse(
            ProxyHomeActionId.RefreshSubscription in
                actions(subscriptions = listOf(DesktopStoredSubscription(id = 8, url = ""))),
        )
    }

    @Test
    fun subscriptionsEnableGroupManagement() {
        val one = actions(subscriptions = listOf(subscription))
        assertTrue(ProxyHomeActionId.ToggleSubscriptionEnabled in one)
        assertTrue(ProxyHomeActionId.EditSubscription in one)
        assertTrue(ProxyHomeActionId.DeleteGroup in one)
        assertFalse(ProxyHomeActionId.MoveGroup in one)
        val two = actions(subscriptions = listOf(subscription, DesktopStoredSubscription(id = 8, url = "")))
        assertTrue(ProxyHomeActionId.MoveGroup in two)
    }
}
