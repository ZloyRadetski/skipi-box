// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan

/** Platform-independent server input used to build choices for a profile proxy group. */
data class TrafficConfigProxyGroupServerInput(
    val id: Int,
    val server: ProxyServer<*>,
)

/** Produces profile-facing server choices while preserving the source order. */
fun trafficConfigProxyGroupServerChoices(
    servers: List<TrafficConfigProxyGroupServerInput>,
): List<ProxyGroupServerChoice> {
    return servers
        .filter { input ->
            input.server !is ChainProxy &&
                (input.server !is Custom || input.server.canBeUsedInGeneratedProxyPlan())
        }
        .map { input ->
            ProxyGroupServerChoice(
                id = input.id,
                rawName = input.server.getInfo().remarks.trim(),
            )
        }
        .filter { choice -> choice.rawName.isNotBlank() }
}

/** Creates the default editable strategy for a newly added profile proxy group. */
fun newTrafficConfigProxyGroupStrategy(trafficConfigId: Int): StrategyGroup {
    return StrategyGroup(
        strategy = StrategyGroupConstants.TYPE_SELECT,
        sourceTrafficConfigId = trafficConfigId,
        displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
    )
}
