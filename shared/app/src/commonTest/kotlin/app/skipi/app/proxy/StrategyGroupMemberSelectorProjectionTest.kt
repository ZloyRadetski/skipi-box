// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrategyGroupMemberSelectorProjectionTest {

    @Test
    fun eligibilityAndOptionalRemarkRequirementMatchAndroidSelector() {
        val servers = listOf(
            record(1, 7, VLESS(remarks = "First node")),
            record(2, 7, ChainProxy(remarks = "Chain")),
            record(
                3,
                7,
                Custom(
                    remarks = "Unsupported custom",
                    configJson = """{"outbounds":[{"tag":"direct","protocol":"freedom","settings":{}}]}""",
                ),
            ),
            record(
                4,
                7,
                Custom(
                    remarks = "Supported custom",
                    configJson = """{"outbounds":[{"tag":"proxy","protocol":"socks","settings":{}}]}""",
                ),
            ),
            record(5, 7, StrategyGroup(remarks = "Profile-owned", sourceTrafficConfigId = 42)),
            record(6, 7, StrategyGroup(remarks = "User strategy")),
            record(7, 7, VLESS(remarks = " \t ")),
            record(8, 7, VLESS(remarks = "Ordinary node after the blank-remark row")),
        )
        val subscriptionGroups = listOf(StrategyMemberSelectorSubscription(7, "Provider"))

        val withoutRemarkRequirement = projectStrategyGroupMemberGroups(
            servers = servers,
            subscriptionGroups = subscriptionGroups,
            defaultGroupName = "Manual",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )
        val withRemarkRequirement = projectStrategyGroupMemberGroups(
            servers = servers,
            subscriptionGroups = subscriptionGroups,
            defaultGroupName = "Manual",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
            requireServerRemarks = true,
        )

        assertEquals(listOf("subscription-7"), withoutRemarkRequirement.map { it.key })
        assertEquals(listOf(1, 4, 6, 7, 8), withoutRemarkRequirement.single().servers.map(ProxyServerRecord::id))
        assertEquals(listOf(1, 4, 6, 8), withRemarkRequirement.single().servers.map(ProxyServerRecord::id))
    }

    @Test
    fun explicitExcludedServerIdRemovesTheCurrentProfileLineFromMembershipChoices() {
        val groups = projectStrategyGroupMemberGroups(
            servers = listOf(
                record(41, 7, VLESS(remarks = "Available node")),
                record(42, 7, VLESS(remarks = "Current profile line")),
            ),
            subscriptionGroups = listOf(StrategyMemberSelectorSubscription(7, "Provider")),
            defaultGroupName = "Manual",
            autoBalancerName = "Auto balancers",
            excludedServerId = 42,
        )

        assertEquals(listOf(41), groups.single().servers.map(ProxyServerRecord::id))
    }

    @Test
    fun autoBalancerGroupContainsOnlyEligibleStrategyGroupsAndIsOmittedWhenEmpty() {
        val servers = listOf(
            record(40, DefaultAutoBalancerGroupId, StrategyGroup(remarks = "First balancer")),
            record(41, DefaultAutoBalancerGroupId, VLESS(remarks = "Ordinary auto-group row")),
            record(42, DefaultAutoBalancerGroupId, StrategyGroup(remarks = "Generated", sourceTrafficConfigId = 9)),
            record(43, DefaultAutoBalancerGroupId, StrategyGroup(remarks = "Second balancer")),
        )
        val subscriptions = listOf(StrategyMemberSelectorSubscription(7, "Empty provider"))

        val withBalancers = projectStrategyGroupMemberGroups(
            servers = servers,
            subscriptionGroups = subscriptions,
            defaultGroupName = "Manual",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )
        val withoutBalancers = projectStrategyGroupMemberGroups(
            servers = listOf(servers[1]),
            subscriptionGroups = subscriptions,
            defaultGroupName = "Manual",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )

        assertEquals(listOf("auto-balancers", "subscription-7"), withBalancers.map { it.key })
        assertEquals("Auto balancers", withBalancers.first().title)
        assertEquals(listOf(40, 43), withBalancers.first().servers.map(ProxyServerRecord::id))
        assertEquals(listOf("subscription-7"), withoutBalancers.map { it.key })
    }

    @Test
    fun subscriptionGroupsKeepInputOrderAndIncludeEmptyGroupsWithProfileTitleFallback() {
        val subscriptions = listOf(
            StrategyMemberSelectorSubscription(12, "First display name", profileTitle = "Provider title"),
            // The selector input has no enabled flag; supplied empty/disabled groups remain visible.
            StrategyMemberSelectorSubscription(4, "Disabled empty group", profileTitle = " \t "),
            StrategyMemberSelectorSubscription(DefaultSubscriptionGroupId, "Manual display name", profileTitle = "Not a subscription section"),
            StrategyMemberSelectorSubscription(9, "Last display name"),
        )

        val groups = projectStrategyGroupMemberGroups(
            servers = emptyList(),
            subscriptionGroups = subscriptions,
            defaultGroupName = "Manual",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )

        assertEquals(listOf("subscription-12", "subscription-4", "subscription-9"), groups.map { it.key })
        assertEquals(listOf("Provider title", "Disabled empty group", "Last display name"), groups.map { it.title })
        assertEquals(listOf(emptyList(), emptyList(), emptyList()), groups.map { it.servers })
    }

    @Test
    fun manualGroupAppearsWhenPopulatedOrWhenThereAreNoNonDefaultGroups() {
        val defaultGroup = StrategyMemberSelectorSubscription(DefaultSubscriptionGroupId, "Manual source")
        val provider = StrategyMemberSelectorSubscription(7, "Provider")

        val populated = projectStrategyGroupMemberGroups(
            servers = listOf(
                record(1, DefaultSubscriptionGroupId, VLESS(remarks = "Manual node")),
                record(2, 7, VLESS(remarks = "Provider node")),
            ),
            subscriptionGroups = listOf(defaultGroup, provider),
            defaultGroupName = "Manual label",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )
        val emptyBesideProvider = projectStrategyGroupMemberGroups(
            servers = emptyList(),
            subscriptionGroups = listOf(defaultGroup, provider),
            defaultGroupName = "Manual label",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )
        val emptyWithoutProviders = projectStrategyGroupMemberGroups(
            servers = emptyList(),
            subscriptionGroups = listOf(defaultGroup),
            defaultGroupName = "Manual label",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )
        val emptyWithoutAnyGroup = projectStrategyGroupMemberGroups(
            servers = emptyList(),
            subscriptionGroups = emptyList(),
            defaultGroupName = "Manual label",
            autoBalancerName = "Auto balancers",
            excludedServerId = null,
        )

        assertEquals(listOf("subscription-7", "manual-servers"), populated.map { it.key })
        assertEquals("Manual label", populated.last().title)
        assertEquals(DefaultSubscriptionGroupId, populated.last().subscriptionGroupId)
        assertEquals(listOf(1), populated.last().servers.map(ProxyServerRecord::id))
        assertEquals(listOf("subscription-7"), emptyBesideProvider.map { it.key })
        assertEquals(listOf("manual-servers"), emptyWithoutProviders.map { it.key })
        assertEquals(DefaultSubscriptionGroupId, emptyWithoutProviders.single().subscriptionGroupId)
        assertEquals(listOf("manual-servers"), emptyWithoutAnyGroup.map { it.key })
        assertNull(emptyWithoutAnyGroup.single().subscriptionGroupId)
    }

    private fun record(id: Int, groupId: Int, server: features.proxy.server.model.ProxyServer<*>) =
        ProxyServerRecord(id = id, groupId = groupId, server = server)
}
