// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.ui.server.editor.ProxyServerEditorGroupOption
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopProxyServerEditorProjectionTest {
    @Test
    fun flagOnlyServerRemarkRemainsTheDisplayedTitle() {
        val inputs = projectDesktopProxyServerEditorInputs(
            proxyServers = listOf(
                ProxyServerRecord(
                    id = 45,
                    groupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    server = VLESS(remarks = "🇺🇸"),
                ),
            ),
            subscriptionCatalog = SubscriptionCatalog(),
            subscriptionSummaries = emptyMap(),
            allGroupsLabel = "All groups",
            defaultGroupName = "Manual servers",
            autoBalancerName = "Auto balancers",
            defaultProxyServerTemplate = "Server {id}",
            excludedServerId = null,
        )

        val item = inputs.memberGroups.single { it.key == "manual-servers" }.items.single()
        assertEquals("🇺🇸", item.flag)
        assertEquals("🇺🇸", item.displayTitle)
    }

    @Test
    fun sourceOptionsOfferSyntheticDesktopManualGroupWithoutMergingSubscriptionOne() {
        val emptyCatalog = projectDesktopProxyServerEditorInputs(
            proxyServers = emptyList(),
            subscriptionCatalog = SubscriptionCatalog(),
            subscriptionSummaries = emptyMap(),
            allGroupsLabel = "All groups",
            defaultGroupName = "Manual servers",
            autoBalancerName = "Auto balancers",
            defaultProxyServerTemplate = "Server {id}",
            excludedServerId = null,
        )
        val catalogWithSubscriptionOne = projectDesktopProxyServerEditorInputs(
            proxyServers = emptyList(),
            subscriptionCatalog = SubscriptionCatalog(
                subscriptions = listOf(subscription(id = 1, title = "Subscription one")),
            ),
            subscriptionSummaries = emptyMap(),
            allGroupsLabel = "All groups",
            defaultGroupName = "Manual servers",
            autoBalancerName = "Auto balancers",
            defaultProxyServerTemplate = "Server {id}",
            excludedServerId = null,
        )

        assertEquals(
            listOf(
                ProxyServerEditorGroupOption(id = null, label = "All groups"),
                ProxyServerEditorGroupOption(
                    id = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    label = "Manual servers",
                ),
            ),
            emptyCatalog.groupOptions,
        )
        assertEquals(
            listOf(
                ProxyServerEditorGroupOption(id = null, label = "All groups"),
                ProxyServerEditorGroupOption(id = 1, label = "Subscription one"),
                ProxyServerEditorGroupOption(
                    id = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    label = "Manual servers",
                ),
            ),
            catalogWithSubscriptionOne.groupOptions,
        )
    }

    @Test
    fun forwardsSummaryForMatchingSubscriptionIdAndLeavesManualAndAutoGroupsWithoutOne() {
        val expectedSummary = ServerPickerSubscriptionSummary(
            traffic = "Traffic: 420 MB / 2 GB",
            expiry = "12 days remaining",
            trafficProgress = 0.75f,
            announce = "Maintenance at 22:00",
            lastUpdated = "Updated: 21 Oct 2026 09:41",
        )
        val inputs = projectDesktopProxyServerEditorInputs(
            proxyServers = listOf(
                ProxyServerRecord(
                    id = 11,
                    groupId = 1,
                    server = VLESS(remarks = "Provider node"),
                ),
                ProxyServerRecord(
                    id = 22,
                    groupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    server = VLESS(remarks = "Manual node"),
                ),
                ProxyServerRecord(
                    id = 33,
                    groupId = DefaultAutoBalancerGroupId,
                    server = StrategyGroup(remarks = "Balancer"),
                ),
            ),
            subscriptionCatalog = SubscriptionCatalog(
                subscriptions = listOf(subscription(id = 1, title = "Subscription one")),
            ),
            subscriptionSummaries = mapOf(1 to expectedSummary),
            allGroupsLabel = "All groups",
            defaultGroupName = "Manual servers",
            autoBalancerName = "Auto balancers",
            defaultProxyServerTemplate = "Server {id}",
            excludedServerId = null,
        )

        val providerGroup = inputs.memberGroups.single { it.key == "subscription-1" }
        val manualGroup = inputs.memberGroups.single { it.key == "manual-servers" }
        val autoGroup = inputs.memberGroups.single { it.key == "auto-balancers" }

        assertEquals(expectedSummary, providerGroup.summary)
        assertNull(manualGroup.summary)
        assertNull(autoGroup.summary)
    }

    private fun subscription(id: Int, title: String) = SubscriptionRecord(
        id = id,
        title = title,
        url = "https://example.test/subscription/$id",
        userAgent = "",
        updateInterval = "",
        hwid = "",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = true,
        enabled = true,
        builtIn = false,
        notifyOnExpiry = true,
        customExpiryReminders = null,
    )
}
