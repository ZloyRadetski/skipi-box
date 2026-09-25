package features.proxy.server.presentation

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyGroupCatalogTest {
    @Test
    fun groups_keep_subscription_order_and_filter_inside_selected_group() {
        val catalog = createProxyGroupCatalog(
            servers = listOf(
                ProxyGroupServer(1, server = node("Manual")),
                ProxyGroupServer(2, subscriptionId = 7, server = node("Amsterdam")),
                ProxyGroupServer(3, server = StrategyGroup(remarks = "Auto")),
            ),
            subscriptions = listOf(ProxyGroupSubscription(7, "https://example.com/sub", "Provider")),
        )

        assertEquals(
            listOf(ProxyGroupIds.All, ProxyGroupIds.AutoBalancers, ProxyGroupIds.subscription(7), ProxyGroupIds.Manual),
            catalog.groups.map(ProxyGroup::id),
        )
        assertEquals(listOf(2), catalog.filter(ProxyGroupIds.subscription(7), "amsterdam").serverIds)
        assertEquals(emptyList(), catalog.filter(ProxyGroupIds.Manual, "amsterdam").serverIds)
    }

    @Test
    fun empty_catalog_is_safe_and_config_strategies_follow_display_mode() {
        val empty = createProxyGroupCatalog(emptyList(), emptyList())
        assertNull(empty.defaultGroupId)
        assertEquals(emptyList(), empty.select().serverIds)

        val catalog = createProxyGroupCatalog(
            servers = listOf(
                ProxyGroupServer(1, server = node("Manual")),
                ProxyGroupServer(
                    2,
                    server = StrategyGroup(
                        remarks = "Active",
                        sourceTrafficConfigId = 4,
                        displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
                    ),
                ),
            ),
            subscriptions = emptyList(),
            options = ProxyGroupOptions(activeTrafficConfigId = 4),
        )
        assertEquals(listOf(2), catalog.group(ProxyGroupIds.AutoBalancers)?.serverIds)
    }

    private fun node(remarks: String): ProxyServer<*> = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@example.com:443#$remarks",
    )
}
