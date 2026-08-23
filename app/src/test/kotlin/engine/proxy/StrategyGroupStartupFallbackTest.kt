// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrategyGroupStartupFallbackTest {
    @Test
    fun usesFastestVerifiedMemberAsTransientBalancerFallback() {
        val group = StrategyGroup(
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(10, 20, 30),
        )
        val state = AppState(
            proxyServers = listOf(
                ProxyServerState(10, VLESS(remarks = "Slow", id = "10", server = "slow.example", port = "443"), groupId = 0),
                ProxyServerState(20, VLESS(remarks = "Fast", id = "20", server = "fast.example", port = "443"), groupId = 0),
                ProxyServerState(30, VLESS(remarks = "Failed", id = "30", server = "failed.example", port = "443"), groupId = 0),
                ProxyServerState(100, group, groupId = 0),
            ),
        )

        val warmed = state.withStrategyGroupStartupFallback(
            serverId = 100,
            probeLatencies = mapOf(10 to 320L, 20 to 90L, 30 to -1L),
        )

        val warmedGroup = warmed.proxyServers.single { server -> server.id == 100 }.server as StrategyGroup
        assertEquals(20, warmedGroup.selectedMemberId)
        assertEquals(null, group.selectedMemberId)
    }

    @Test
    fun doesNotAssignFallbackWhenNoCandidatePassedRealConnectionTest() {
        val group = StrategyGroup(
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(10),
        )
        val state = AppState(
            proxyServers = listOf(
                ProxyServerState(10, VLESS(remarks = "Unavailable", id = "10", server = "node.example", port = "443"), groupId = 0),
                ProxyServerState(100, group, groupId = 0),
            ),
        )

        val warmed = state.withStrategyGroupStartupFallback(
            serverId = 100,
            probeLatencies = mapOf(10 to -1L),
        )

        val warmedGroup = warmed.proxyServers.single { server -> server.id == 100 }.server as StrategyGroup
        assertNull(warmedGroup.selectedMemberId)
    }
}
