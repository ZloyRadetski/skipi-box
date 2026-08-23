// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.config.TrafficConfigState
import features.config.withConfigProxyGroupsReflected
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayBurstBalancerTest {
    @Test
    fun configPolicyGroupsUseConcurrentStandardObservatoryByDefault() {
        val state = AppState(
            activeTrafficConfigId = 1,
            proxyServers = listOf(
                ProxyServerState(10, VLESS(remarks = "Node A", id = "a", server = "a.example", port = "443"), groupId = 0),
                ProxyServerState(20, VLESS(remarks = "Node B", id = "b", server = "b.example", port = "443"), groupId = 0),
            ),
            trafficConfigs = listOf(
                TrafficConfigState(
                    id = 1,
                    name = "Fast start",
                    rawConfig = """
                        [Proxy Group]
                        Auto = url-test, Node A, Node B, skipi-display=always
                    """.trimIndent(),
                ),
            ),
        ).withConfigProxyGroupsReflected()
        val balancer = state.proxyServers.single { server -> server.groupId == AutoBalancerGroupId }

        val plan = state.buildXrayOutboundPlan(balancer)

        assertTrue(plan.observatorySelectors.isNotEmpty())
    }
}
