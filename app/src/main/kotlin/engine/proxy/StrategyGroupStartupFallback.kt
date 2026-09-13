// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import app.proxyServerIdFromOutboundTag
import engine.xray.strategyGroupMembers
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants

/**
 * Applies the first member reached by the short startup TCP race only to the
 * in-flight VPN request. The persisted selection remains untouched; Xray keeps
 * validating members through its Observatory after the tunnel starts.
 */
internal fun AppState.withStrategyGroupStartupFallback(
    serverId: Int,
    probeLatencies: Map<Int, Long>,
): AppState {
    val fastestMemberId = probeLatencies
        .filterValues { latency -> latency >= 0 }
        .minByOrNull { (_, latency) -> latency }
        ?.key
        ?: return this
    return copy(
        proxyServers = proxyServers.map { state ->
            if (state.id != serverId) return@map state
            val group = state.server as? StrategyGroup ?: return@map state
            if (
                group.strategy == StrategyGroupConstants.TYPE_SELECT ||
                !strategyGroupMembers(group).any { member -> member.id == fastestMemberId }
            ) {
                state
            } else {
                state.copy(server = group.copy(selectedMemberId = fastestMemberId))
            }
        },
    )
}

/**
 * Resolves a concrete member observed in Xray outbound counters back to the
 * selected strategy group.  Tags from unrelated policy groups are rejected.
 */
internal fun AppState.strategyGroupMemberIdForOutbound(
    strategyGroupId: Int,
    outboundTag: String,
): Int? {
    val memberId = outboundTag.proxyServerIdFromOutboundTag() ?: return null
    val group = proxyServers
        .firstOrNull { server -> server.id == strategyGroupId }
        ?.server as? StrategyGroup
        ?: return null
    return memberId.takeIf { id ->
        strategyGroupMembers(group).any { member -> member.id == id }
    }
}
