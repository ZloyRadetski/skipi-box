// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Source-derived oracle for Android's strategy-group member picker catalog projection.
 * It calls the extracted Android helper; it does not reproduce the grouping algorithm or claim
 * Compose, navigation, or pointer-interaction coverage.
 */
class StrategyGroupMemberSelectorAndroidOracleTest {
    @Test
    fun eligibilityHonorsSelfChainGeneratedCustomAndOptionalRemarkExclusions() {
        val state = AppState(
            subscriptionGroups = listOf(
                subscriptionGroup(DefaultSubscriptionGroupId, "Manual"),
                subscriptionGroup(7, "Provider"),
            ),
            proxyServers = listOf(
                server(11, 7, vless("Allowed node")),
                server(12, 7, ChainProxy(remarks = "Chain")),
                server(
                    13,
                    7,
                    Custom(
                        remarks = "Runnable Custom",
                        configJson = """{"outbounds":[{"tag":"proxy","protocol":"socks","settings":{}}]}""",
                    ),
                ),
                server(
                    14,
                    7,
                    Custom(
                        remarks = "Unsupported Custom",
                        configJson = """{"outbounds":[{"tag":"direct","protocol":"freedom","settings":{}}]}""",
                    ),
                ),
                server(15, 7, StrategyGroup(remarks = "Generated", sourceTrafficConfigId = 901)),
                server(16, 7, StrategyGroup(remarks = "Standalone strategy")),
                server(17, 7, vless(" \t ")),
                server(18, 7, vless("Current strategy")),
            ),
        )

        val requireRemarks = project(
            state = state,
            excludedServerId = 18,
            requireServerRemarks = true,
        )
        assertEquals(listOf("subscription-7"), requireRemarks.map { it.key })
        assertEquals(listOf("Provider"), requireRemarks.map { it.title })
        assertEquals(listOf(listOf(11, 13, 16)), requireRemarks.map { group -> group.servers.map { it.id } })

        val allowBlankRemarks = project(
            state = state,
            excludedServerId = 18,
            requireServerRemarks = false,
        )
        assertEquals(listOf("subscription-7"), allowBlankRemarks.map { it.key })
        assertEquals(listOf(listOf(11, 13, 16, 17)), allowBlankRemarks.map { group -> group.servers.map { it.id } })
    }

    @Test
    fun subscriptionsKeepInputOrderIncludeEmptyAndDisabledGroupsAndPreferProfileTitle() {
        val state = AppState(
            subscriptionGroups = listOf(
                subscriptionGroup(9, "Stored nine", profileTitle = "Provider Nine", enabled = false),
                subscriptionGroup(4, "Stored four", profileTitle = "  "),
                subscriptionGroup(DefaultSubscriptionGroupId, "Manual"),
            ),
            proxyServers = listOf(server(41, 4, vless("Four node"))),
        )

        val groups = project(state)

        assertEquals(listOf("subscription-9", "subscription-4"), groups.map { it.key })
        assertEquals(listOf("Provider Nine", "Stored four"), groups.map { it.title })
        assertEquals(listOf(emptyList<Int>(), listOf(41)), groups.map { group -> group.servers.map { it.id } })
    }

    @Test
    fun autoBalancerGroupIsFirstAndContainsOnlyStandaloneStrategyServersInCatalogOrder() {
        val state = AppState(
            subscriptionGroups = listOf(
                subscriptionGroup(DefaultSubscriptionGroupId, "Manual"),
                subscriptionGroup(7, "Provider"),
            ),
            proxyServers = listOf(
                server(31, AutoBalancerGroupId, StrategyGroup(remarks = "First standalone")),
                server(32, AutoBalancerGroupId, vless("Non-strategy in auto group")),
                server(33, AutoBalancerGroupId, StrategyGroup(remarks = "Generated", sourceTrafficConfigId = 71)),
                server(34, AutoBalancerGroupId, ChainProxy(remarks = "Chain in auto group")),
                server(35, AutoBalancerGroupId, StrategyGroup(remarks = "Second standalone")),
                server(70, 7, vless("Provider node")),
            ),
        )

        val groups = project(state)

        assertEquals(listOf("auto-balancers", "subscription-7"), groups.map { it.key })
        assertEquals(listOf("Auto balancers", "Provider"), groups.map { it.title })
        assertEquals(listOf(listOf(31, 35), listOf(70)), groups.map { group -> group.servers.map { it.id } })
    }

    @Test
    fun manualGroupAppearsOnlyWithManualServersOrWhenThereAreNoProviderGroups() {
        val providerState = AppState(
            subscriptionGroups = listOf(
                subscriptionGroup(DefaultSubscriptionGroupId, "Manual"),
                subscriptionGroup(8, "Provider"),
            ),
        )
        val withoutManualServers = project(providerState)

        assertEquals(listOf("subscription-8"), withoutManualServers.map { it.key })
        assertEquals(listOf(emptyList<Int>()), withoutManualServers.map { group -> group.servers.map { it.id } })

        val withManualServer = project(
            providerState.copy(
                proxyServers = listOf(server(51, DefaultSubscriptionGroupId, vless("Manual node"))),
            ),
        )
        assertEquals(listOf("subscription-8", "manual-servers"), withManualServer.map { it.key })
        assertEquals(listOf("Provider", "Manual servers"), withManualServer.map { it.title })
        assertEquals(listOf(emptyList<Int>(), listOf(51)), withManualServer.map { group -> group.servers.map { it.id } })

        val noSubscriptions = project(
            AppState(subscriptionGroups = emptyList(), proxyServers = emptyList()),
        )
        assertEquals(listOf("manual-servers"), noSubscriptions.map { it.key })
        assertEquals(listOf("Manual servers"), noSubscriptions.map { it.title })
        assertEquals(listOf(emptyList<Int>()), noSubscriptions.map { group -> group.servers.map { it.id } })
    }

    private fun project(
        state: AppState,
        excludedServerId: Int? = null,
        requireServerRemarks: Boolean = false,
    ) = projectStrategyGroupMemberGroups(
        appState = state,
        excludedServerId = excludedServerId,
        requireServerRemarks = requireServerRemarks,
        defaultGroupName = "Manual servers",
        autoBalancerName = "Auto balancers",
    )

    private fun server(id: Int, groupId: Int, server: features.proxy.server.model.ProxyServer<*>): ProxyServerState =
        ProxyServerState(id = id, groupId = groupId, server = server)

    private fun vless(remarks: String) = VLESS(
        remarks = remarks,
        id = remarks.ifBlank { "blank" },
        server = "node.example.test",
        port = "443",
    )

    private fun subscriptionGroup(
        id: Int,
        name: String,
        profileTitle: String = "",
        enabled: Boolean = true,
    ) = SubscriptionGroupState(
        id = id,
        name = name,
        url = "https://subscription-$id.example.test/list",
        userAgent = "",
        updateInterval = "",
        enabled = enabled,
        builtIn = id == DefaultSubscriptionGroupId,
        profileTitle = profileTitle,
    )
}
