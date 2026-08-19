// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.effectiveLocalDnsEnabled
import app.proxyServerOutboundTag
import features.routing.model.RouteRule
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.customXrayConfigPrimaryProxyOutbound
import features.proxy.server.model.customXrayConfigProxyServerHosts
import features.proxy.server.model.isCompositeProxyServer
import features.proxy.server.model.isCustomProxyServer
import features.proxy.server.model.serverHost
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.config.analyzeShadowrocketConfig

internal fun AppState.buildXrayOutboundPlan(selectedServer: ProxyServerState): XrayOutboundPlan {
    return XrayOutboundPlanner(this).build(selectedServer)
}

private class XrayOutboundPlanner(
    private val appState: AppState,
) {
    private val proxyOutbounds = mutableListOf<XrayProxyOutboundServer>()
    private val balancers = mutableListOf<XrayBalancerPlan>()
    private val observatorySelectors = mutableListOf<String>()
    private val burstObservatorySelectors = mutableListOf<String>()
    private val routeTargets = linkedMapOf<String, XrayRouteTarget>()
    private val addedOutboundTags = mutableSetOf<String>()
    private val dnsHostServers = mutableListOf<String>()

    fun build(selectedServer: ProxyServerState): XrayOutboundPlan {
        addRouteTarget(XrayTags.PROXY, selectedServer)
        appState.routeTargetServers().forEach { server ->
            addRouteTarget(server.proxyServerOutboundTag(), server)
        }
        addShadowrocketPolicyGroupTargets()
        addFixedRouteTargets()
        return XrayOutboundPlan(
            proxyOutbounds = proxyOutbounds,
            balancers = balancers,
            observatorySelectors = observatorySelectors.distinct(),
            burstObservatorySelectors = burstObservatorySelectors.distinct(),
            routeTargets = routeTargets,
            dnsHostServers = dnsHostServers.distinct(),
        )
    }

    private fun addFixedRouteTargets() {
        routeTargets[XrayTags.DIRECT] = XrayRouteTarget(XrayTags.DIRECT, XrayRouteTargetKind.Outbound)
        routeTargets[XrayTags.BLOCK] = XrayRouteTarget(XrayTags.BLOCK, XrayRouteTargetKind.Outbound)
        if (appState.effectiveLocalDnsEnabled) {
            routeTargets[XrayTags.DNS_OUT] = XrayRouteTarget(XrayTags.DNS_OUT, XrayRouteTargetKind.Outbound)
        }
        if (appState.enableFragment) {
            routeTargets[XrayTags.FRAGMENT] = XrayRouteTarget(XrayTags.FRAGMENT, XrayRouteTargetKind.Outbound)
        }
    }

    /**
     * Shadowrocket profile groups are aliases over existing subscription/manual
     * servers.  They never create a second hidden copy of an endpoint.
     */
    private fun addShadowrocketPolicyGroupTargets() {
        val usedTags = (appState.routeRules.map { rule -> rule.outboundTag } + appState.defaultRouteOutboundTag)
            .map(String::trim)
            .filter { tag -> tag.startsWith(ShadowrocketPolicyGroupTagPrefix) }
            .toSet()
        usedTags.forEach { tag ->
            val group = appState.shadowrocketPolicyGroups.firstOrNull { it.outboundTag == tag } ?: return@forEach
            addShadowrocketPolicyGroup(tag, group)
        }
    }

    private fun addShadowrocketPolicyGroup(
        tag: String,
        group: ShadowrocketPolicyGroup,
    ) {
        val members = appState.shadowrocketPolicyGroupMembers(group)
        if (members.isEmpty()) {
            return
        }
        when (group.type) {
            "select" -> addNormalOutbound(tag = tag, server = members.first())
            "url-test", "fallback", "load-balance" -> {
                val selector = "$tag-policy-"
                val memberTags = members.map { member -> "$selector${member.id}" }
                members.zip(memberTags).forEach { (member, memberTag) ->
                    addNormalOutbound(tag = memberTag, server = member)
                }
                val strategy = if (group.type == "load-balance") {
                    StrategyGroupConstants.TYPE_RANDOM
                } else {
                    StrategyGroupConstants.TYPE_LEAST_PING
                }
                balancers += XrayBalancerPlan(
                    tag = tag,
                    selector = selector,
                    strategy = strategy,
                    fallbackTag = memberTags.first(),
                )
                observatorySelectors += selector
                routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Balancer)
            }

            else -> addNormalOutbound(tag = tag, server = members.first())
        }
    }

    private fun addRouteTarget(tag: String, server: ProxyServerState) {
        when (val proxyServer = server.server) {
            is StrategyGroup -> addStrategyGroup(tag, proxyServer)
            is ChainProxy -> addChainProxy(tag, proxyServer)
            is Custom -> addCustomOutbound(tag, proxyServer)
            else -> addNormalOutbound(tag, server)
        }
    }

    private fun addCustomOutbound(
        tag: String,
        custom: Custom,
    ) {
        if (tag in addedOutboundTags) return
        val primaryOutbound = customXrayConfigPrimaryProxyOutbound(custom.configJson)
            ?: error("Custom server '${custom.remarks}' has no usable proxy outbound")
        proxyOutbounds += XrayProxyOutboundServer(
            tag = tag,
            customOutbound = primaryOutbound,
        )
        if (custom.overrideInboundAndDns) {
            dnsHostServers += customXrayConfigProxyServerHosts(custom.configJson)
        }
        routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
        addedOutboundTags += tag
    }

    private fun addNormalOutbound(
        tag: String,
        server: ProxyServerState,
        dialerProxyTag: String? = null,
        allowFragment: Boolean = true,
    ) {
        if (tag in addedOutboundTags) return
        (server.server as? Custom)?.let { custom ->
            addCustomOutbound(tag, custom)
            return
        }
        proxyOutbounds += XrayProxyOutboundServer(
            tag = tag,
            server = server.server,
            dialerProxyTag = dialerProxyTag,
            allowFragment = allowFragment,
        )
        dnsHostServers += server.server.serverHost()
        routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
        addedOutboundTags += tag
    }

    private fun addStrategyGroup(tag: String, strategyGroup: StrategyGroup) {
        val members = appState.strategyGroupMembers(strategyGroup)
        if (members.isEmpty()) {
            error("Strategy group '${strategyGroup.remarks}' has no available proxy servers")
        }
        val selector = "$tag-policy-"
        val memberTags = members.map { member -> "$selector${member.id}" }
        members.zip(memberTags).forEach { (member, memberTag) ->
            addNormalOutbound(
                tag = memberTag,
                server = member,
            )
        }
        balancers += XrayBalancerPlan(
            tag = tag,
            selector = selector,
            strategy = strategyGroup.strategy,
            fallbackTag = memberTags.first(),
        )
        when (strategyGroup.strategy) {
            StrategyGroupConstants.TYPE_LEAST_LOAD -> burstObservatorySelectors += selector
            StrategyGroupConstants.TYPE_LEAST_PING -> observatorySelectors += selector
        }
        routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Balancer)
    }

    private fun addChainProxy(tag: String, chainProxy: ChainProxy) {
        val members = appState.chainProxyMembers(chainProxy)
        if (members.size < 2) {
            error("Proxy chain '${chainProxy.remarks}' requires at least two available proxy servers")
        }
        val chainOutbounds = members.reversed()
        chainOutbounds.forEachIndexed { index, member ->
            addNormalOutbound(
                tag = chainProxyOutboundTag(tag, index),
                server = member,
                dialerProxyTag = if (index < chainOutbounds.lastIndex) chainProxyOutboundTag(tag, index + 1) else null,
                allowFragment = false,
            )
        }
        routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
    }
}

private fun AppState.routeTargetServers(): List<ProxyServerState> {
    val routeOutboundTags = (routeRules
        .filter(RouteRule::enabled)
        .map { rule -> rule.outboundTag } + defaultRouteOutboundTag)
        .map { tag -> tag.trim() }
        .filter { tag -> tag.isNotEmpty() && tag !in XrayTags.FIXED_OUTBOUND_TAGS }
        .toSet()
    return proxyServers.filter { server -> server.proxyServerOutboundTag() in routeOutboundTags }
}

private fun AppState.strategyGroupMembers(
    strategyGroup: StrategyGroup,
    visitingStrategyGroupIds: Set<Int> = emptySet(),
): List<ProxyServerState> {
    if (strategyGroup.proxyServerIds.isNotEmpty()) {
        return strategyGroup.proxyServerIds.flatMap { memberId ->
            val member = proxyServers.firstOrNull { server -> server.id == memberId }
            when (val server = member?.server) {
                is StrategyGroup -> {
                    if (member.id in visitingStrategyGroupIds) {
                        emptyList()
                    } else {
                        strategyGroupMembers(server, visitingStrategyGroupIds + member.id)
                    }
                }

                null,
                is ChainProxy -> emptyList()

                is Custom -> member.takeIf { candidate ->
                    (candidate.server as Custom).canBeUsedInGeneratedProxyPlan()
                }?.let(::listOf).orEmpty()

                else -> listOf(member)
            }
        }.distinctBy(ProxyServerState::id)
    }
    strategyGroup.sourceTrafficConfigId?.let { configId ->
        val sourceGroups = trafficConfigs.firstOrNull { config -> config.id == configId }
            ?.rawConfig
            ?.analyzeShadowrocketConfig()
            ?.proxyGroups
            .orEmpty()
        val sourceGroup = sourceGroups.firstOrNull { group ->
            group.name.equals(strategyGroup.sourcePolicyGroupName, ignoreCase = true)
        }
        if (sourceGroup != null) {
            return shadowrocketPolicyGroupMembers(sourceGroup, policyGroups = sourceGroups)
        }
    }
    val regex = strategyGroup.filter.takeIf(String::isNotBlank)?.let { filter ->
        runCatching { Regex(filter) }.getOrNull()
    }
    return proxyServers
        .asSequence()
        .filter { server -> !server.server.isCompositeProxyServer() }
        .filter { server ->
            server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()
        }
        .filter { server ->
            strategyGroup.subscriptionGroupId == null || server.groupId == strategyGroup.subscriptionGroupId
        }
        .filter { server ->
            val filter = strategyGroup.filter
            filter.isBlank() ||
                regex?.containsMatchIn(server.server.getInfo().remarks) == true ||
                (regex == null && server.server.getInfo().remarks.contains(filter))
        }
        .toList()
}

private fun AppState.shadowrocketPolicyGroupMembers(
    group: ShadowrocketPolicyGroup,
    policyGroups: List<ShadowrocketPolicyGroup> = shadowrocketPolicyGroups,
    visitingGroupNames: Set<String> = emptySet(),
): List<ProxyServerState> {
    if (group.name in visitingGroupNames) return emptyList()
    return group.members
        .flatMap { member ->
            when {
                member == ".*" -> proxyServers.filter { server ->
                    !server.server.isCompositeProxyServer() &&
                        (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan())
                }

                else -> {
                    val matchingServers = proxyServers.filter { server ->
                        server.server.getInfo().remarks.equals(member, ignoreCase = true) &&
                            (server.server !is ChainProxy) &&
                            (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan())
                    }
                    matchingServers.flatMap { matchingServer ->
                        when (val proxy = matchingServer.server) {
                            is StrategyGroup -> strategyGroupMembers(proxy, setOf(matchingServer.id))
                            else -> matchingServer.takeUnless { it.server.isCompositeProxyServer() }?.let(::listOf).orEmpty()
                        }
                    }.ifEmpty {
                        policyGroups
                            .firstOrNull { candidate -> candidate.name.equals(member, ignoreCase = true) }
                            ?.let { nested ->
                                shadowrocketPolicyGroupMembers(
                                    group = nested,
                                    policyGroups = policyGroups,
                                    visitingGroupNames = visitingGroupNames + group.name,
                                )
                            }
                            .orEmpty()
                    }
                }
            }
        }
        .distinctBy(ProxyServerState::id)
}

private fun AppState.chainProxyMembers(chainProxy: ChainProxy): List<ProxyServerState> {
    return chainProxy.proxyServerIds.mapNotNull { memberId ->
        proxyServers.firstOrNull { server -> server.id == memberId && !server.server.isCompositeProxyServer() }
            ?.takeUnless { server -> server.server.isCustomProxyServer() }
    }
}

private fun chainProxyOutboundTag(tag: String, index: Int): String {
    return if (index == 0) tag else "$tag-chain-$index"
}
