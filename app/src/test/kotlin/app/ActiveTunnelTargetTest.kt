// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import engine.stats.ProxyTrafficStatsRuntime
import engine.xray.XrayTags
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveTunnelTargetTest {
    @Test
    fun strategyGroup_displaysStartupFallbackBeforeTrafficObservation() {
        val state = AppState(
            selectedProxyServerId = 100,
            proxyServers = listOf(
                ProxyServerState(
                    id = 100,
                    server = StrategyGroup(
                        remarks = "Fast group",
                        proxyServerIds = listOf(200),
                    ),
                    groupId = 0,
                ),
                ProxyServerState(
                    id = 200,
                    server = VLESS(
                        remarks = "Node B",
                        id = "uuid",
                        server = "node.example",
                        port = "443",
                    ),
                    groupId = 0,
                ),
            ),
        )
        val runtime = ProxyTrafficStatsRuntime(
            listenAddress = "127.0.0.1",
            port = 10085,
            serverName = "Fast group",
            finalOutboundTag = XrayTags.PROXY,
            selectedServerId = 100,
            startupStrategyMemberId = 200,
        )

        assertEquals(
            "Fast group \u2014 Node B",
            state.activeTunnelTargetDisplayName(
                runtime = runtime,
                activeOutboundTag = null,
                directName = "Direct",
                blockName = "Block",
            ),
        )
    }
}
