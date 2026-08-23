// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants

/**
 * Applies a member that has already completed a real proxy request only to the
 * in-flight VPN request. The persisted selection remains untouched; Xray still
 * keeps validating the selected member through its observatory after startup.
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
                (group.proxyServerIds.isNotEmpty() && fastestMemberId !in group.proxyServerIds)
            ) {
                state
            } else {
                state.copy(server = group.copy(selectedMemberId = fastestMemberId))
            }
        },
    )
}
