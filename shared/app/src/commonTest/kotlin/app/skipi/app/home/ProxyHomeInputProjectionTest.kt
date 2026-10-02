// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.TunnelFailure
import platform.TunnelPhase
import platform.TunnelSnapshot

class ProxyHomeInputProjectionTest {
    @Test
    fun tunnelAvailabilityUsesConnectionPhaseBusyStateAndSelectedServerPresence() {
        fun project(phase: TunnelPhase, busy: Boolean = false, hasSelectedServer: Boolean = true) =
            ProxyHomeInputSnapshot(
                tunnelSnapshot = TunnelSnapshot(phase = phase),
                selectedServerId = "server-1",
                hasSelectedServer = hasSelectedServer,
                tunnelBusy = busy,
            ).toProxyHomeInput()

        assertTrue(project(TunnelPhase.Connected).canToggleTunnel)
        assertTrue(project(TunnelPhase.Disconnected).canToggleTunnel)
        assertFalse(project(TunnelPhase.Disconnected, hasSelectedServer = false).canToggleTunnel)
        assertFalse(project(TunnelPhase.Connected, busy = true).canToggleTunnel)
        assertFalse(project(TunnelPhase.Connecting).canToggleTunnel)
        assertFalse(project(TunnelPhase.Disconnecting).canToggleTunnel)
        assertFalse(project(TunnelPhase.Failed).canToggleTunnel)
    }

    @Test
    fun actionAvailabilityIsDerivedFromSubscriptionGroupsSortAndCapabilities() {
        val actions = ProxyHomeActionId.values().toSet()
        val noSubscription = ProxyHomeInputSnapshot(
            groups = listOf(group("manual", canDelete = false, canMove = false)),
            hasSubscriptions = false,
            sortMode = ProxyHomeSortMode.Name,
            supportedActions = actions,
        ).toProxyHomeInput()
        assertFalse(ProxyHomeActionId.RefreshAllSubscriptions in noSubscription.availableActions)
        assertFalse(ProxyHomeActionId.DeleteGroup in noSubscription.availableActions)
        assertFalse(ProxyHomeActionId.MoveGroup in noSubscription.availableActions)
        assertFalse(ProxyHomeActionId.MoveServer in noSubscription.availableActions)

        val subscription = ProxyHomeInputSnapshot(
            groups = listOf(group("subscription", canDelete = true, canMove = true)),
            hasSubscriptions = true,
            sortMode = ProxyHomeSortMode.Default,
            supportedActions = actions - ProxyHomeActionId.CopyServer,
        ).toProxyHomeInput()
        assertTrue(ProxyHomeActionId.RefreshAllSubscriptions in subscription.availableActions)
        assertTrue(ProxyHomeActionId.DeleteGroup in subscription.availableActions)
        assertTrue(ProxyHomeActionId.MoveGroup in subscription.availableActions)
        assertTrue(ProxyHomeActionId.MoveServer in subscription.availableActions)
        assertFalse(ProxyHomeActionId.CopyServer in subscription.availableActions)
    }

    @Test
    fun projectionCarriesSelectedSummaryDisplayAndTunnelFailure() {
        val server = ProxyServerSummary(
            id = "server-1",
            title = "Selected server",
            address = "proxy.example:443",
            protocol = "vless",
        )
        val projected = ProxyHomeInputSnapshot(
            tunnelSnapshot = TunnelSnapshot(
                phase = TunnelPhase.Failed,
                failure = TunnelFailure("start_failed", "Could not start tunnel", recoverable = true),
            ),
            selectedServerId = server.id,
            selectedServerTitle = "fallback title",
            hasSelectedServer = true,
            servers = listOf(server),
            displayOptions = ProxyHomeDisplayOptions(showAllGroup = true),
            runtimeOutboundMetric = "42 MB",
        ).toProxyHomeInput()

        assertEquals(server.title, projected.selectedServerTitle)
        assertEquals("Could not start tunnel", projected.statusMessage)
        assertTrue(projected.isStatusError)
        assertEquals(ProxyHomeDisplayOptions(showAllGroup = true), projected.displayOptions)
        assertEquals("42 MB", projected.runtimeOutboundMetric)
    }

    @Test
    fun sharedPresentationReducerRetainsUnrelatedFieldsAndGatesSearchVisibility() {
        val initial = ProxyHomePresentation(
            selectedGroupId = "group-1",
            searchQuery = "old",
            collapsedSubscriptionGroupIds = setOf("group-2"),
        )
        val changed = reduceProxyHomePresentation(
            initial,
            ProxyHomeAction.SetSearchQuery("new"),
        )
        assertEquals("new", changed.searchQuery)
        assertEquals("group-1", changed.selectedGroupId)
        assertEquals(setOf("group-2"), changed.collapsedSubscriptionGroupIds)
        assertFalse(
            reduceProxyHomePresentation(
                changed,
                ProxyHomeAction.SetSearchVisible(true),
                searchEnabled = false,
            ).isSearchVisible,
        )
        assertEquals(
            "group-3",
            reduceProxyHomePresentation(changed, ProxyHomeAction.SelectGroup("group-3")).selectedGroupId,
        )
    }

    private fun group(id: String, canDelete: Boolean, canMove: Boolean) = ProxyGroupSummary(
        id = id,
        title = id,
        serverCount = 0,
        enabled = true,
        canDelete = canDelete,
        canMove = canMove,
    )
}
