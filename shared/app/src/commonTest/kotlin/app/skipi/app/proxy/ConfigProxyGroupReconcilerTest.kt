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
    fun aGroupShownOnlyForTheActiveConfigKeepsItsRecordSoOnDemandAndRoutingRulesKeepTheirTarget() {
        val config = profile(
            """[Proxy Group]
                Always = select, Node A, skipi-display=always
                Active = select, Node A, skipi-display=active_config
                Hidden = select, Node A, skipi-display=never
            """.trimIndent(),
        )
        val servers = listOf(node(10, "Node A"))
        fun generated(result: ConfigProxyGroupReconciliation) = result.servers.mapNotNull { record ->
            (record.server as? StrategyGroup)?.takeIf { it.sourceTrafficConfigId != null }?.let { record.id to it.remarks }
        }

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
        val active = reconcile(
            trafficConfigs = listOf(config),
            activeConfigId = config.id,
            servers = inactive.servers,
            nextServerId = inactive.nextServerId,
            selectedServerId = inactive.selectedServerId,
            retainedServerIds = setOf(999),
        )

        // A hidden group is not materialized at all; the other two are, whichever config is active.
        assertEquals(listOf("Always", "Active"), generated(selected).map { it.second })
        assertEquals(999, selected.selectedServerId)
        // Switching to another config only hides the group; its id survives, so a rule that targets it still works.
        assertEquals(generated(selected), generated(inactive))
        assertEquals(generated(selected), generated(active))
        assertEquals(999, inactive.selectedServerId)
    }

    private fun reconcile(
        trafficConfigs: List<TrafficConfigState>,
        activeConfigId: Int,
        servers: List<ProxyServerRow>,
        nextServerId: Int,
        selectedServerId: Int = 0,
        retainedServerIds: Set<Int> = emptySet(),
    ) = reconcileConfigProxyGroups(
        trafficConfigs = trafficConfigs,
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

    private fun node(id: Int, remarks: String) = ProxyServerRow(
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
