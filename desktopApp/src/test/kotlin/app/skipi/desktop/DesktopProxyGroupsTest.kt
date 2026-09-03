// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopProxyGroupsTest {
    @Test
    fun creates_android_like_groups_with_stable_ids_and_group_local_filtering() {
        val library = DesktopServerLibrary(
            servers = listOf(
                storedServer(10, "Manual node"),
                storedServer(11, "Fast Amsterdam", subscriptionId = 2),
                storedServer(12, "Disabled provider", subscriptionId = 3),
                storedStrategy(20, StrategyGroup(remarks = "Fastest", proxyServerIds = listOf(10, 11))),
                storedStrategy(
                    21,
                    StrategyGroup(
                        remarks = "Active profile group",
                        sourceTrafficConfigId = 7,
                        displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                    ),
                ),
            ),
        )
        val subscriptions = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                DesktopStoredSubscription(id = 2, url = "https://one.example/sub", name = "Torvalds VPN"),
                DesktopStoredSubscription(id = 3, url = "https://two.example/sub", name = "Disabled subscription"),
            ),
        )

        val catalog = DesktopProxyGroups.create(
            serverLibrary = library,
            subscriptionLibrary = subscriptions,
            options = DesktopProxyGroupOptions(
                subscriptionEnabledById = mapOf(3 to false),
                activeTrafficConfigId = 7,
            ),
        )

        assertEquals(
            listOf(
                DesktopProxyGroupIds.All,
                DesktopProxyGroupIds.AutoBalancers,
                DesktopProxyGroupIds.subscription(2),
                DesktopProxyGroupIds.Manual,
            ),
            catalog.groups.map(DesktopProxyGroup::id),
        )
        assertEquals("Все прокси", catalog.group(DesktopProxyGroupIds.All)?.title)
        assertEquals(3, catalog.group(DesktopProxyGroupIds.All)?.serverCount)
        assertEquals(listOf(10, 11, 20), catalog.group(DesktopProxyGroupIds.All)?.serverIds)
        assertEquals(listOf(20, 21), catalog.group(DesktopProxyGroupIds.AutoBalancers)?.serverIds)
        assertEquals(1, catalog.group(DesktopProxyGroupIds.subscription(2))?.serverCount)
        assertNull(catalog.group(DesktopProxyGroupIds.subscription(3)))
        assertEquals(listOf(10), catalog.group(DesktopProxyGroupIds.Manual)?.serverIds)

        assertEquals(
            listOf(11),
            catalog.filter(DesktopProxyGroupIds.subscription(2), query = "amsterdam").serverIds,
        )
        assertEquals(
            emptyList(),
            catalog.filter(DesktopProxyGroupIds.Manual, query = "amsterdam").serverIds,
        )
    }

    @Test
    fun hides_inactive_or_hidden_config_strategy_groups_and_keeps_manual_strategy_in_all() {
        val catalog = DesktopProxyGroups.create(
            serverLibrary = DesktopServerLibrary(
                servers = listOf(
                    storedServer(1, "Manual node"),
                    storedStrategy(2, StrategyGroup(remarks = "Manual balancer")),
                    storedStrategy(
                        3,
                        StrategyGroup(
                            remarks = "Inactive config group",
                            sourceTrafficConfigId = 8,
                            displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                        ),
                    ),
                    storedStrategy(
                        4,
                        StrategyGroup(
                            remarks = "Always config group",
                            sourceTrafficConfigId = 9,
                            displayMode = StrategyGroupDisplayMode.ALWAYS,
                        ),
                    ),
                    storedStrategy(
                        5,
                        StrategyGroup(
                            remarks = "Hidden group",
                            showInAutoBalancerList = false,
                        ),
                    ),
                ),
            ),
            subscriptionLibrary = DesktopSubscriptionLibrary(
                subscriptions = listOf(DesktopStoredSubscription(id = 2, url = "https://example.com/sub")),
            ),
            options = DesktopProxyGroupOptions(activeTrafficConfigId = 7),
        )

        assertEquals(
            listOf(2, 4),
            catalog.group(DesktopProxyGroupIds.AutoBalancers)?.serverIds,
        )
        assertEquals(
            listOf(1, 2),
            catalog.group(DesktopProxyGroupIds.All)?.serverIds,
        )
    }

    @Test
    fun selection_falls_back_to_first_real_group_and_empty_catalog_is_safe() {
        val catalog = DesktopProxyGroups.create(
            serverLibrary = DesktopServerLibrary(servers = listOf(storedServer(1, "Manual"))),
            subscriptionLibrary = DesktopSubscriptionLibrary(),
        )

        assertEquals(DesktopProxyGroupIds.Manual, catalog.defaultGroupId)
        assertEquals(DesktopProxyGroupIds.Manual, catalog.select("unknown").group?.id)
        assertEquals(listOf(1), catalog.select(DesktopProxyGroupIds.All).serverIds)

        val empty = DesktopProxyGroups.create(DesktopServerLibrary(), DesktopSubscriptionLibrary())
        assertNull(empty.defaultGroupId)
        assertNull(empty.select().group)
        assertEquals(emptyList(), empty.filter(query = "anything").serverIds)
    }

    private fun storedServer(id: Int, remarks: String, subscriptionId: Int? = null): DesktopStoredProxyServer =
        DesktopStoredProxyServer(
            id = id,
            subscriptionId = subscriptionId,
            serverJson = ProxyServer.parse(
                "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@$id.example:443#$remarks",
            ).encodePersistedProxyServer(),
        )

    private fun storedStrategy(id: Int, strategy: StrategyGroup): DesktopStoredProxyServer =
        DesktopStoredProxyServer(
            id = id,
            serverJson = strategy.encodePersistedProxyServer(),
        )
}
