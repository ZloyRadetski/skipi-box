// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.config.TrafficConfigState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigProxyGroupReconcilerTest {
    @Test
    fun refreshKeepsGeneratedIdentityAndSelectedMemberWhileResolvingLatestProfileMembers() {
        val before = profile(
            """[Proxy Group]
                Stable = select, Node A, Node B, skipi-display=always
            """.trimIndent(),
        )
        val initial = reconcile(
            trafficConfigs = listOf(before),
            activeConfigId = before.id,
            servers = listOf(node(10, "Node A"), node(20, "Node B")),
            nextServerId = 30,
        )
        val generatedBefore = initial.servers.single { it.id == 30 }
        val selectedB = generatedBefore.copy(
            server = (generatedBefore.server as StrategyGroup).copy(selectedMemberId = 20),
        )
        val latest = before.copy(
            rawConfig = """[Proxy Group]
                Stable = select, Node B, Node C, skipi-display=always
            """.trimIndent(),
        )

        val refreshed = reconcile(
            trafficConfigs = listOf(latest),
            activeConfigId = latest.id,
            servers = listOf(
                node(10, "Node A"),
                node(20, "Node B"),
                node(40, "Node C"),
                selectedB,
            ),
            nextServerId = initial.nextServerId,
            selectedServerId = generatedBefore.id,
        )
        val generatedAfter = refreshed.servers.single { it.id == generatedBefore.id }
        val strategy = generatedAfter.server as StrategyGroup

        assertEquals(generatedBefore.id, generatedAfter.id)
        assertEquals(latest.id, strategy.sourceTrafficConfigId)
        assertEquals("Stable", strategy.sourcePolicyGroupName)
        assertEquals(listOf(20, 40), strategy.proxyServerIds)
        assertEquals(20, strategy.selectedMemberId)
        assertEquals(generatedBefore.id, refreshed.selectedServerId)
        assertEquals(initial.nextServerId, refreshed.nextServerId)
    }

    @Test
    fun activeVisibilityAndOpaqueSelectedIdFollowTheSharedCatalogContract() {
        val config = profile(
            """[Proxy Group]
                Always = select, Node A, skipi-display=always
                Active = select, Node A, skipi-display=active_config
                Hidden = select, Node A, skipi-display=never
            """.trimIndent(),
        )
        val servers = listOf(node(10, "Node A"))

        val selected = reconcile(
            trafficConfigs = listOf(config),
            activeConfigId = config.id,
            servers = servers,
            nextServerId = 20,
            selectedServerId = 999,
            retainedServerIds = setOf(999),
        )
        val inactive = reconcile(
            trafficConfigs = listOf(config),
            activeConfigId = config.id + 1,
            servers = selected.servers,
            nextServerId = selected.nextServerId,
            selectedServerId = selected.selectedServerId,
            retainedServerIds = setOf(999),
        )

        assertEquals(listOf("Always", "Active"), selected.servers.mapNotNull { record ->
            (record.server as? StrategyGroup)?.takeIf { it.sourceTrafficConfigId != null }?.remarks
        })
        assertEquals(999, selected.selectedServerId)
        assertEquals(listOf("Always"), inactive.servers.mapNotNull { record ->
            (record.server as? StrategyGroup)?.takeIf { it.sourceTrafficConfigId != null }?.remarks
        })
        assertEquals(999, inactive.selectedServerId)
    }

    private fun reconcile(
        trafficConfigs: List<TrafficConfigState>,
        activeConfigId: Int,
        servers: List<ProxyServerRecord>,
        nextServerId: Int,
        selectedServerId: Int = 0,
        retainedServerIds: Set<Int> = emptySet(),
    ) = reconcileConfigProxyGroups(
        trafficConfigs = trafficConfigs,
        activeTrafficConfigId = activeConfigId,
        servers = servers,
        nextServerId = nextServerId,
        selectedServerId = selectedServerId,
        autoBalancerGroupId = DefaultAutoBalancerGroupId,
        stripLeadingCountryFlag = { it },
        retainedServerIds = retainedServerIds,
    )

    private fun profile(rawConfig: String) = TrafficConfigState(
        id = 7,
        name = "Profile",
        rawConfig = rawConfig,
    )

    private fun node(id: Int, remarks: String) = ProxyServerRecord(
        id = id,
        groupId = DefaultSubscriptionGroupId,
        server = VLESS(
            remarks = remarks,
            id = "user-$id",
            server = "node-$id.example",
            port = "443",
        ),
    )
}
