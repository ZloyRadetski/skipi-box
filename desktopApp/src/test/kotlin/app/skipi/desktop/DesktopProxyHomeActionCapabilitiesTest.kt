// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeServerTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProxyHomeActionCapabilitiesTest {
    @Test
    fun disabledSubscriptionWithUrlStillExposesBatchRefreshWhenCallbackExists() {
        val capabilities = desktopProxyHomeActionCapabilities(
            subscriptions = listOf(
                DesktopStoredSubscription(
                    id = 7,
                    url = "https://example.test/subscription",
                    enabled = false,
                ),
            ),
            onRefreshAllSubscriptions = {},
            onDeleteDuplicateServers = null,
            onDeleteInvalidServers = null,
        )

        assertTrue(ProxyHomeActionId.RefreshAllSubscriptions in capabilities.actions)
        assertTrue(ProxyHomeActionId.RunServerTool in capabilities.actions)
        assertEquals(setOf(ProxyHomeServerTool.UpdateSubscriptions), capabilities.serverTools)
    }

    @Test
    fun refreshCapabilityRequiresBothAUrlAndAConfiguredCallback() {
        val missingCallback = desktopProxyHomeActionCapabilities(
            subscriptions = listOf(DesktopStoredSubscription(id = 7, url = "https://example.test/subscription")),
            onRefreshAllSubscriptions = null,
            onDeleteDuplicateServers = null,
            onDeleteInvalidServers = null,
        )
        val blankUrl = desktopProxyHomeActionCapabilities(
            subscriptions = listOf(DesktopStoredSubscription(id = 8, url = "  ")),
            onRefreshAllSubscriptions = {},
            onDeleteDuplicateServers = null,
            onDeleteInvalidServers = null,
        )

        assertFalse(ProxyHomeActionId.RefreshAllSubscriptions in missingCallback.actions)
        assertFalse(ProxyHomeServerTool.UpdateSubscriptions in missingCallback.serverTools)
        assertFalse(ProxyHomeActionId.RunServerTool in missingCallback.actions)
        assertFalse(ProxyHomeActionId.RefreshAllSubscriptions in blankUrl.actions)
        assertFalse(ProxyHomeServerTool.UpdateSubscriptions in blankUrl.serverTools)
        assertFalse(ProxyHomeActionId.RunServerTool in blankUrl.actions)
    }

    @Test
    fun cleanupToolsAreAdvertisedOnlyForTheirConfiguredCallbacks() {
        val duplicateOnly = desktopProxyHomeActionCapabilities(
            subscriptions = emptyList(),
            onRefreshAllSubscriptions = null,
            onDeleteDuplicateServers = { _ -> },
            onDeleteInvalidServers = null,
        )
        val both = desktopProxyHomeActionCapabilities(
            subscriptions = emptyList(),
            onRefreshAllSubscriptions = null,
            onDeleteDuplicateServers = { _ -> },
            onDeleteInvalidServers = { _ -> },
        )

        assertEquals(setOf(ProxyHomeServerTool.DeleteDuplicateServers), duplicateOnly.serverTools)
        assertTrue(ProxyHomeActionId.RunServerTool in duplicateOnly.actions)
        assertEquals(
            setOf(ProxyHomeServerTool.DeleteDuplicateServers, ProxyHomeServerTool.DeleteInvalidServers),
            both.serverTools,
        )
        assertTrue(ProxyHomeActionId.RunServerTool in both.actions)
    }
}
