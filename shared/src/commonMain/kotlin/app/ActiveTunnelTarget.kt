// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import engine.stats.ProxyTrafficStatsRuntime
import engine.xray.XrayTags
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.proxy.server.model.StrategyGroup

/**
 * Resolves the target frozen into a running tunnel. For a balancer, the
 * latest outbound carrying traffic identifies the member currently in use.
 */
fun AppState.activeTunnelTargetDisplayName(
    runtime: ProxyTrafficStatsRuntime?,
    activeOutboundTag: String?,
    directName: String,
    blockName: String,
    unavailableName: String = "—",
): String {
    val effectiveState = withActiveTrafficConfigApplied()
    val finalTag = runtime?.finalOutboundTag
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: effectiveState.defaultRouteOutboundTag.trim().ifBlank { DefaultRouteOutboundTag }
    val selectedServer = proxyServers.firstOrNull { server ->
        server.id == (runtime?.selectedServerId ?: selectedProxyServerId)
    }
    val observedServer = activeOutboundTag
        ?.proxyServerIdFromOutboundTag()
        ?.let { id -> proxyServers.firstOrNull { server -> server.id == id } }

    return when (finalTag) {
        XrayTags.DIRECT -> directName
        XrayTags.BLOCK -> blockName
        else -> {
            val targetServer = if (finalTag == XrayTags.PROXY) {
                selectedServer
            } else {
                proxyServers.firstOrNull { server -> server.proxyServerOutboundTag() == finalTag }
            }
            val policyGroupName = effectiveState.shadowrocketPolicyGroups
                .firstOrNull { group -> group.outboundTag == finalTag }
                ?.name
            when {
                policyGroupName != null -> policyGroupName.withObservedMember(observedServer)
                targetServer?.server is StrategyGroup -> targetServer.displayName().withObservedMember(observedServer)
                targetServer != null -> targetServer.displayName()
                finalTag.startsWith(ShadowrocketPolicyGroupTagPrefix) -> {
                    finalTag.removePrefix(ShadowrocketPolicyGroupTagPrefix).withObservedMember(observedServer)
                }

                selectedServer != null -> selectedServer.displayName()
                else -> unavailableName
            }
        }
    }
}

private fun String.withObservedMember(member: ProxyServerState?): String {
    val memberName = member?.displayName().orEmpty()
    return if (memberName.isBlank()) this else "$this — $memberName"
}

private fun ProxyServerState.displayName(): String {
    val info = server.getInfo()
    return info.remarks.ifBlank { info.protocol.ifBlank { "#$id" } }
}

fun String.proxyServerIdFromOutboundTag(): Int? {
    return toIntOrNull()
        ?: substringAfterLast("-policy-", missingDelimiterValue = "").toIntOrNull()
}
