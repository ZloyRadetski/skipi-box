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
import features.proxy.server.model.toXrayBalancerStrategy
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.config.analyzeShadowrocketConfig
import features.proxy.server.display.CountryFlagUtils
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.resolveStrategyGroupMembers
import app.skipi.app.proxy.resolveShadowrocketPolicyGroupMembers

internal fun AppState.buildXrayOutboundPlan(selectedServer: ProxyServerState): XrayOutboundPlan {
    return XrayOutboundPlanner(this).build(selectedServer)
}

private class XrayOutboundPlanner(
    private val appState: AppState,
) {
    private val proxyOutbounds = mutableListOf<XrayProxyOutboundServer>()
    private val balancers = mutableListOf<XrayBalancerPlan>()
    private val observatorySelectors = mutableListOf<String>()
    private val routeTargets = linkedMapOf<String, XrayRouteTarget>()
    private val addedOutboundTags = mutableSetOf<String>()
    private val dnsHostServers = mutableListOf<String>()
    private var observatoryProbeUrl: String? = null
    private var observatoryProbeInterval: String? = null
    private var observatoryProbeTimeout: String? = null

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
            routeTargets = routeTargets,
            dnsHostServers = dnsHostServers.distinct(),
            observatoryProbeUrl = observatoryProbeUrl,
            observatoryProbeInterval = observatoryProbeInterval,
            observatoryProbeTimeout = observatoryProbeTimeout,
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
        val matchingStrategy = appState.proxyServers.mapNotNull { it.server as? StrategyGroup }
            .firstOrNull { it.sourcePolicyGroupName.equals(group.name, ignoreCase = true) }
        val members = if (matchingStrategy != null) {
            appState.strategyGroupMembers(matchingStrategy)
        } else {
            appState.shadowrocketPolicyGroupMembers(group)
        }
        if (members.isEmpty()) {
            return
        }
        when (group.type.lowercase()) {
            "select" -> {
                val targetMember = members.firstOrNull { it.id == matchingStrategy?.selectedMemberId } ?: members.first()
                addNormalOutbound(tag = tag, server = targetMember)
            }
            "url-test", "fallback", "leastping", "load-balance", "random", "round-robin", "roundrobin", "least-load", "leastload" -> {
                val selector = "$tag-policy-"
                val memberTags = members.map { member -> "$selector${member.id}" }
                members.zip(memberTags).forEach { (member, memberTag) ->
                    addNormalOutbound(tag = memberTag, server = member)
                }
                val strategy = group.type.toXrayBalancerStrategy()
                val customProbeUrl = matchingStrategy?.probeUrl?.trim()?.takeIf(String::isNotEmpty)
                    ?: group.url.trim().takeIf(String::isNotEmpty)
                    ?: appState.subscriptionPingUrl.trim().takeIf(String::isNotEmpty)
                val customProbeInterval = matchingStrategy?.probeInterval?.trim()?.takeIf(String::isNotEmpty)
                    ?: group.intervalSeconds?.let { "${it}s" }
                val customProbeTimeout = matchingStrategy?.probeTimeout?.trim()?.takeIf(String::isNotEmpty)
                    ?: group.timeoutSeconds?.let { "${it}s" }
                if (customProbeUrl != null && observatoryProbeUrl == null) {
                    observatoryProbeUrl = customProbeUrl
                }
                if (customProbeInterval != null && observatoryProbeInterval == null) {
                    observatoryProbeInterval = customProbeInterval
                }
                if (customProbeTimeout != null && observatoryProbeTimeout == null) {
                    observatoryProbeTimeout = customProbeTimeout
                }
                val selectedTag = matchingStrategy?.selectedMemberId?.let { selectedId ->
                    members.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { memberTags[it] }
                }
                val bestFallbackTag = selectedTag ?: members.zip(memberTags)
                    .filter { (member, _) ->
                        val lat = member.latency.trim()
                        lat.isNotBlank() && !lat.contains("Failed", ignoreCase = true) && !lat.contains("Timeout", ignoreCase = true)
                    }
                    .minByOrNull { (member, _) -> member.latency.latencySortKey() }
                    ?.second
                    ?: members.zip(memberTags)
                        .minByOrNull { (member, _) -> member.latency.latencySortKey() }
                        ?.second
                    ?: memberTags.first()

                // leastPing only consumes the regular Observatory.  Burst
                // Observatory schedules its initial probes randomly, so it
                // can leave the balancer without a target despite a verified
                // startup fallback.  The regular Observatory probes all
                // members concurrently and lets fallbackTag carry traffic
                // until it has its first result.
                observatorySelectors += selector
                balancers += XrayBalancerPlan(
                    tag = tag,
                    selector = selector,
                    strategy = strategy,
                    fallbackTag = bestFallbackTag,
                )
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
        if (strategyGroup.strategy == StrategyGroupConstants.TYPE_SELECT) {
            val targetMember = members.firstOrNull { it.id == strategyGroup.selectedMemberId } ?: members.first()
            addNormalOutbound(
                tag = tag,
                server = targetMember,
            )
            routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
            return
        }
        val selector = "$tag-policy-"
        val memberTags = members.map { member -> "$selector${member.id}" }
        members.zip(memberTags).forEach { (member, memberTag) ->
            addNormalOutbound(
                tag = memberTag,
                server = member,
            )
        }
        val balancerStrategy = strategyGroup.strategy.toXrayBalancerStrategy()
        val selectedTag = strategyGroup.selectedMemberId?.let { selectedId ->
            members.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { memberTags[it] }
        }
        val bestFallbackTag = if (strategyGroup.strategy == StrategyGroupConstants.TYPE_FALLBACK) {
            // A verified startup member is carried in selectedMemberId only for
            // this generated config.  It must also be Xray's real fallbackTag:
            // otherwise the UI reports the verified member while initial
            // traffic still goes to the first configured server.
            selectedTag ?: memberTags.first()
        } else {
            selectedTag ?: members.zip(memberTags)
                .filter { (member, _) ->
                    val lat = member.latency.trim()
                    lat.isNotBlank() && !lat.contains("Failed", ignoreCase = true) && !lat.contains("Timeout", ignoreCase = true)
                }
                .minByOrNull { (member, _) -> member.latency.latencySortKey() }
                ?.second
                ?: members.zip(memberTags)
                    .minByOrNull { (member, _) -> member.latency.latencySortKey() }
                    ?.second
                ?: memberTags.first()
        }

        val customProbeUrl = strategyGroup.probeUrl.trim().takeIf(String::isNotEmpty)
            ?: appState.subscriptionPingUrl.trim().takeIf(String::isNotEmpty)
        val customProbeInterval = strategyGroup.probeInterval.trim().takeIf(String::isNotEmpty)
        val customProbeTimeout = strategyGroup.probeTimeout.trim().takeIf(String::isNotEmpty)
        if (customProbeUrl != null && observatoryProbeUrl == null) {
            observatoryProbeUrl = customProbeUrl
        }
        if (customProbeInterval != null && observatoryProbeInterval == null) {
            observatoryProbeInterval = customProbeInterval
        }
        if (customProbeTimeout != null && observatoryProbeTimeout == null) {
            observatoryProbeTimeout = customProbeTimeout
        }

        // enableBurstProbe controls SKIPI's pre-start verification. Xray's
        // running leastPing balancer must use the standard, concurrent
        // Observatory; a Burst Observatory has no deterministic first probe
        // and is not a supported leastPing strategy setting.
        observatorySelectors += selector

        balancers += XrayBalancerPlan(
            tag = tag,
            selector = selector,
            strategy = balancerStrategy,
            fallbackTag = bestFallbackTag,
        )
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

internal fun AppState.strategyGroupMembers(
    strategyGroup: StrategyGroup,
    visitingStrategyGroupIds: Set<Int> = emptySet(),
): List<ProxyServerState> {
    val records = proxyServers.map { server ->
        ProxyServerRecord(server.id, server.groupId, server.server, server.latency)
    }
    val resolved = resolveStrategyGroupMembers(
        strategyGroup = strategyGroup,
        servers = records,
        trafficConfigContentById = trafficConfigs.associate { it.id to it.rawConfig },
        stripLeadingFlag = CountryFlagUtils::stripLeadingCountryFlag,
        visitingStrategyGroupIds = visitingStrategyGroupIds,
    )
    return resolved.mapNotNull { record -> proxyServers.firstOrNull { it.id == record.id } }
}

private fun AppState.shadowrocketPolicyGroupMembers(
    group: ShadowrocketPolicyGroup,
    policyGroups: List<ShadowrocketPolicyGroup> = shadowrocketPolicyGroups,
): List<ProxyServerState> {
    val records = proxyServers.map { server ->
        ProxyServerRecord(server.id, server.groupId, server.server, server.latency)
    }
    val resolved = resolveShadowrocketPolicyGroupMembers(
        group = group,
        policyGroups = policyGroups,
        servers = records,
        trafficConfigContentById = trafficConfigs.associate { it.id to it.rawConfig },
        stripLeadingFlag = CountryFlagUtils::stripLeadingCountryFlag,
    )
    return resolved.mapNotNull { record -> proxyServers.firstOrNull { it.id == record.id } }
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

private fun String.latencySortKey(): Int {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed.startsWith("-") || trimmed.contains("Failed", ignoreCase = true) || trimmed.contains("Timeout", ignoreCase = true) || trimmed.contains("Error", ignoreCase = true)) {
        return Int.MAX_VALUE
    }
    val number = latencyRegex.find(trimmed)?.value?.toIntOrNull()
    return number ?: Int.MAX_VALUE
}

private val latencyRegex = Regex("""\d+""")
