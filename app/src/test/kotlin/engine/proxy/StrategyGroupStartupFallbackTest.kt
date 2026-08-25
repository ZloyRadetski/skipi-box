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
    fun usesFastestReachableMemberAsTransientBalancerFallback() {
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
    fun doesNotAssignFallbackWhenNoCandidateWasReachable() {
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

    @Test
    fun fallbackGroupUsesReachableStartupMember() {
        val group = StrategyGroup(
            strategy = StrategyGroupConstants.TYPE_FALLBACK,
            proxyServerIds = listOf(10, 20),
        )
        val state = AppState(
            proxyServers = listOf(
                ProxyServerState(10, VLESS(remarks = "Primary", id = "10", server = "primary.example", port = "443"), groupId = 0),
                ProxyServerState(20, VLESS(remarks = "Reachable", id = "20", server = "reachable.example", port = "443"), groupId = 0),
                ProxyServerState(100, group, groupId = 0),
            ),
        )

        val warmed = state.withStrategyGroupStartupFallback(
            serverId = 100,
            probeLatencies = mapOf(10 to -1L, 20 to 50L),
        )

        val warmedGroup = warmed.proxyServers.single { server -> server.id == 100 }.server as StrategyGroup
        assertEquals(20, warmedGroup.selectedMemberId)
    }

    @Test
    fun strategyGroupWithMultipleReachableMembersPicksLowestLatency() {
        val group = StrategyGroup(
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(1, 2, 3),
        )
        val state = AppState(
            proxyServers = listOf(
                ProxyServerState(1, VLESS(remarks = "Server 1", id = "1", server = "s1.example", port = "443"), latency = "250ms", groupId = 0),
                ProxyServerState(2, VLESS(remarks = "Server 2", id = "2", server = "s2.example", port = "443"), latency = "45ms", groupId = 0),
                ProxyServerState(3, VLESS(remarks = "Server 3", id = "3", server = "s3.example", port = "443"), latency = "120ms", groupId = 0),
                ProxyServerState(10, group, groupId = 0),
            ),
        )

        val warmed = state.withStrategyGroupStartupFallback(
            serverId = 10,
            probeLatencies = mapOf(1 to 250L, 2 to 45L, 3 to 120L),
        )
        val warmedGroup = warmed.proxyServers.single { server -> server.id == 10 }.server as StrategyGroup
        assertEquals(2, warmedGroup.selectedMemberId)
    }
}
