// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.routing

import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord
import features.config.TrafficConfigState
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteOutboundPickerProjectionTest {

    @Test
    fun profilePolicyProjectionPreservesAndroidOrderEmptyGroupsAndTags() {
        val currentProfile = profile(
            id = 42,
            rawConfig = """
                [Proxy Group]
                Preferred = select, Manual Node, Provider Node
            """.trimIndent(),
        )
        val provider = server(12, groupId = 7, VLESS(remarks = " Provider Node "))
        val manual = server(21, groupId = DefaultSubscriptionGroupId, VLESS(remarks = "Manual Node"))
        val automatic = server(
            31,
            groupId = DefaultAutoBalancerGroupId,
            StrategyGroup(remarks = "Balance"),
        )
        val configOwned = server(
            32,
            groupId = DefaultAutoBalancerGroupId,
            StrategyGroup(remarks = "Config-owned", sourceTrafficConfigId = currentProfile.id),
        )

        val result = projectRouteOutboundPicker(
            servers = listOf(provider, manual, automatic, configOwned),
            subscriptionGroups = subscriptionGroups,
            trafficConfigs = listOf(currentProfile, profile(99, name = "Other profile")),
            selectedTag = "Provider Node",
            trafficConfigId = currentProfile.id,
            labels = labels,
        )

        assertEquals(
            listOf(
                "routing-system-targets",
                "routing-auto-balancers",
                "routing-subscription-7",
                "routing-subscription-8",
                "routing-manual-servers",
                "routing-config-proxy-groups",
                "routing-config-targets",
            ),
            result.groups.map { it.key },
        )
        assertEquals(listOf("PROXY", "DIRECT", "REJECT"), result.groups[0].tags())
        assertEquals(listOf("Balance"), result.groups[1].tags())
        assertEquals(listOf("Provider Node"), result.groups[2].tags())
        val providerOption = result.groups[2].items.single() as RouteOutboundItem.Server
        assertEquals(provider, providerOption.server)
        assertEquals("server-12", providerOption.key)
        assertTrue(result.groups[3].items.isEmpty(), "empty subscription groups remain present")
        assertEquals(listOf("Manual Node"), result.groups[4].tags())
        assertEquals(listOf("Preferred"), result.groups[5].tags())
        assertEquals(listOf("CONFIG:99"), result.groups[6].tags())
        assertEquals(7, result.groups[2].subscriptionGroupId)
        assertEquals(DefaultSubscriptionGroupId, result.groups[4].subscriptionGroupId)
        assertEquals("select", (result.groups[5].items.single() as RouteOutboundItem.Static).summary)
        assertEquals("routing-subscription-7", result.initiallyExpandedGroupKey)
    }

    @Test
    fun globalProjectionUsesNumericServerTagsAndOmitsProfileTargets() {
        val selectedProfile = profile(
            id = 42,
            rawConfig = """
                [Proxy Group]
                Preferred = select, DIRECT
            """.trimIndent(),
        )
        val result = projectRouteOutboundPicker(
            servers = listOf(server(12, groupId = 7, VLESS(remarks = "Provider Node"))),
            subscriptionGroups = subscriptionGroups,
            trafficConfigs = listOf(selectedProfile),
            selectedTag = "12",
            trafficConfigId = null,
            labels = labels,
        )

        assertEquals(
            listOf(
                "routing-system-targets",
                "routing-auto-balancers",
                "routing-subscription-7",
                "routing-subscription-8",
                "routing-manual-servers",
            ),
            result.groups.map { it.key },
        )
        assertEquals(listOf("proxy", "direct", "block"), result.groups[0].tags())
        assertEquals(listOf("12"), result.groups[2].tags())
        assertTrue(result.groups[3].items.isEmpty())
        assertFalse(result.groups.any { it.key == "routing-config-proxy-groups" })
        assertFalse(result.groups.any { it.key == "routing-config-targets" })
        assertEquals("routing-subscription-7", result.initiallyExpandedGroupKey)
    }

    @Test
    fun profilePolicyPreservesUnknownCaseVariantAndExpandsSystemGroup() {
        val selectedProfile = profile(
            id = 42,
            rawConfig = """
                [Proxy Group]
                Preferred = select, DIRECT
            """.trimIndent(),
        )
        val result = projectOutbound(
            selectedTag = "preferred",
            trafficConfigId = selectedProfile.id,
            trafficConfigs = listOf(selectedProfile),
        )

        assertEquals(listOf("PROXY", "DIRECT", "REJECT", "preferred"), result.groups[0].tags())
        assertEquals(listOf("Preferred"), result.groups[5].tags())
        assertEquals("routing-system-targets", result.initiallyExpandedGroupKey)
    }

    @Test
    fun missingExplicitProfileIdStillUsesPolicyTargetsAndAllOtherConfigs() {
        val result = projectOutbound(
            selectedTag = "Provider Node",
            trafficConfigId = 404,
            trafficConfigs = listOf(profile(42), profile(99, name = "Other profile")),
            servers = listOf(
                server(12, groupId = 7, VLESS(remarks = " Provider Node ")),
                server(13, groupId = 7, VLESS(remarks = "  ")),
            ),
        )

        assertEquals(listOf("PROXY", "DIRECT", "REJECT"), result.groups[0].tags())
        assertEquals(listOf("Provider Node"), result.groups[2].tags())
        assertTrue(result.groups[5].items.isEmpty())
        assertEquals(listOf("CONFIG:42", "CONFIG:99"), result.groups[6].tags())
        assertEquals("routing-subscription-7", result.initiallyExpandedGroupKey)
    }

    @Test
    fun customEligibilityAndBlankRemarkFilteringMatchEachMode() {
        val supportedCustom = server(
            70,
            groupId = 7,
            Custom(
                remarks = "Runnable Custom",
                configJson = """{"outbounds":[{"tag":"proxy","protocol":"socks","settings":{}}]}""",
            ),
        )
        val unsupportedCustom = server(
            71,
            groupId = 7,
            Custom(
                remarks = "Unsupported Custom",
                configJson = """{"outbounds":[{"tag":"direct","protocol":"freedom","settings":{}}]}""",
            ),
        )
        val blankRemark = server(72, groupId = 7, VLESS(remarks = " \t "))
        val servers = listOf(supportedCustom, unsupportedCustom, blankRemark)
        val selectedProfile = profile(42)

        val profileResult = projectOutbound(
            servers = servers,
            selectedTag = "Runnable Custom",
            trafficConfigId = selectedProfile.id,
            trafficConfigs = listOf(selectedProfile),
        )
        val globalResult = projectOutbound(
            servers = servers,
            selectedTag = "70",
            trafficConfigId = null,
            trafficConfigs = listOf(selectedProfile),
        )

        assertEquals(listOf("Runnable Custom"), profileResult.groups[2].tags())
        assertEquals(listOf("70", "72"), globalResult.groups[2].tags())
    }

    @Test
    fun systemTargetMatchingIgnoresCaseWithoutAddingDuplicateOption() {
        val result = projectOutbound(
            selectedTag = "direct",
            trafficConfigId = 42,
            trafficConfigs = listOf(profile(42)),
        )

        assertEquals(listOf("PROXY", "DIRECT", "REJECT"), result.groups[0].tags())
        assertEquals("routing-system-targets", result.initiallyExpandedGroupKey)
    }

    @Test
    fun selectedConfigProxyGroupExpandsItsContainingGroup() {
        val selectedProfile = profile(
            id = 42,
            rawConfig = """
                [Proxy Group]
                Preferred = select, DIRECT
            """.trimIndent(),
        )
        val result = projectOutbound(
            selectedTag = "Preferred",
            trafficConfigId = selectedProfile.id,
            trafficConfigs = listOf(selectedProfile),
        )

        assertEquals(listOf("Preferred"), result.groups[5].tags())
        assertEquals("routing-config-proxy-groups", result.initiallyExpandedGroupKey)
    }

    @Test
    fun knownServerWithoutAnEmittedGroupIsNotReintroducedAsSystemTarget() {
        val orphan = server(44, groupId = 404, VLESS(remarks = "Unrendered server"))
        val result = projectOutbound(
            servers = listOf(orphan),
            selectedTag = "44",
            trafficConfigId = null,
        )

        assertEquals(listOf("proxy", "direct", "block"), result.groups[0].tags())
        assertTrue(result.groups.none { group -> group.items.any { it is RouteOutboundItem.Server } })
        assertEquals("routing-system-targets", result.initiallyExpandedGroupKey)
    }

    @Test
    fun customDefaultGroupIdKeepsSubscriptionOneSeparateFromManualMinusOne() {
        val result = projectRouteOutboundPicker(
            servers = listOf(
                server(101, groupId = 1, VLESS(remarks = "Subscription one node")),
                server(202, groupId = -1, VLESS(remarks = "Manual node")),
            ),
            subscriptionGroups = listOf(
                RouteOutboundPickerSubscription(id = 1, name = "Subscription one"),
                RouteOutboundPickerSubscription(id = -1, name = "Manual source", builtIn = true),
            ),
            trafficConfigs = emptyList(),
            selectedTag = "101",
            trafficConfigId = null,
            labels = labels,
            defaultGroupId = -1,
        )

        assertEquals(
            listOf(
                "routing-system-targets",
                "routing-auto-balancers",
                "routing-subscription-1",
                "routing-manual-servers",
            ),
            result.groups.map { it.key },
        )
        val subscriptionOne = result.groups[2]
        val manualServers = result.groups[3]
        assertEquals(1, subscriptionOne.subscriptionGroupId)
        assertEquals(listOf("101"), subscriptionOne.tags())
        assertEquals(1, (subscriptionOne.items.single() as RouteOutboundItem.Server).server.groupId)
        assertEquals(-1, manualServers.subscriptionGroupId)
        assertEquals(listOf("202"), manualServers.tags())
        assertEquals(-1, (manualServers.items.single() as RouteOutboundItem.Server).server.groupId)
        assertEquals("routing-subscription-1", result.initiallyExpandedGroupKey)
    }

    private fun projectOutbound(
        servers: List<ProxyServerRecord> = emptyList(),
        selectedTag: String,
        trafficConfigId: Int?,
        trafficConfigs: List<TrafficConfigState> = emptyList(),
    ): RouteOutboundPickerProjection = projectRouteOutboundPicker(
        servers = servers,
        subscriptionGroups = subscriptionGroups,
        trafficConfigs = trafficConfigs,
        selectedTag = selectedTag,
        trafficConfigId = trafficConfigId,
        labels = labels,
    )

    private fun RouteOutboundPickerGroup.tags(): List<String> = items.map { item ->
        when (item) {
            is RouteOutboundItem.Static -> item.tag
            is RouteOutboundItem.Server -> item.tag
        }
    }

    private fun server(id: Int, groupId: Int, server: features.proxy.server.model.ProxyServer<*>) =
        ProxyServerRecord(id = id, groupId = groupId, server = server)

    private fun profile(
        id: Int,
        name: String = "Profile $id",
        rawConfig: String = "",
    ) = TrafficConfigState(id = id, name = name, rawConfig = rawConfig)

    private val subscriptionGroups = listOf(
        RouteOutboundPickerSubscription(
            id = DefaultSubscriptionGroupId,
            name = "Stored default",
            builtIn = true,
        ),
        RouteOutboundPickerSubscription(id = 7, name = "Provider", builtIn = false),
        RouteOutboundPickerSubscription(id = 8, name = "Empty provider", builtIn = false),
    )

    private val labels = RouteOutboundPickerLabels(
        defaultGroupName = "Manual",
        autoBalancerName = "Auto balancers",
        systemTargetsName = "System targets",
        proxyLabel = "Proxy",
        directLabel = "Direct",
        blockLabel = "Block",
        proxyGroupsName = "Proxy groups",
        configTargetsName = "Configs",
    )
}
