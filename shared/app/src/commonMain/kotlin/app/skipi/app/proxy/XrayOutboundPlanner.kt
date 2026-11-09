// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import engine.xray.XrayBalancerPlan
import engine.xray.XrayOutboundPlan
import engine.xray.XrayProxyOutboundServer
import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.XrayTags
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.customXrayConfigPrimaryProxyOutbound
import features.proxy.server.model.customXrayConfigProxyServerHosts
import features.proxy.server.model.isCompositeProxyServer
import features.proxy.server.model.isCustomProxyServer
import features.proxy.server.model.serverHost
import features.proxy.server.model.toXrayBalancerStrategy
import features.routing.model.RouteRule

/** Explicit platform inputs for planning Xray outbounds from the live server list. */
data class XrayOutboundPlanningInput(
    val selectedServer: ProxyServerRecord,
    val servers: List<ProxyServerRecord>,
    val routeRules: List<RouteRule>,
    val defaultRouteOutboundTag: String,
    val shadowrocketPolicyGroups: List<ShadowrocketPolicyGroup>,
    val trafficConfigContentById: Map<Int, String>,
    val enableLocalDns: Boolean,
    val enableFragment: Boolean,
    val subscriptionPingUrl: String,
    val stripLeadingFlag: (String) -> String,
)

/** Portable counterpart of Android's live-list Xray outbound planner. */
object XrayOutboundPlanner {
    fun plan(input: XrayOutboundPlanningInput): XrayOutboundPlan = Planning(input).build()

    private class Planning(
        private val input: XrayOutboundPlanningInput,
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

        fun build(): XrayOutboundPlan {
            addRouteTarget(XrayTags.PROXY, input.selectedServer)
            routeTargetServers().forEach { server ->
                addRouteTarget(server.id.toString(), server)
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
                observatoryProbeInterval = observatoryProbeInterval
                    ?: DefaultBalancerProbeInterval.takeIf { balancers.isNotEmpty() },
                observatoryProbeTimeout = observatoryProbeTimeout,
            )
        }

        private fun addFixedRouteTargets() {
            routeTargets[XrayTags.DIRECT] = XrayRouteTarget(XrayTags.DIRECT, XrayRouteTargetKind.Outbound)
            routeTargets[XrayTags.BLOCK] = XrayRouteTarget(XrayTags.BLOCK, XrayRouteTargetKind.Outbound)
            if (input.enableLocalDns) {
                routeTargets[XrayTags.DNS_OUT] = XrayRouteTarget(XrayTags.DNS_OUT, XrayRouteTargetKind.Outbound)
            }
            if (input.enableFragment) {
                routeTargets[XrayTags.FRAGMENT] = XrayRouteTarget(XrayTags.FRAGMENT, XrayRouteTargetKind.Outbound)
            }
        }

        private fun routeTargetServers(): List<ProxyServerRecord> {
            val routeOutboundTags = (input.routeRules
                .filter(RouteRule::enabled)
                .map { rule -> rule.outboundTag } + input.defaultRouteOutboundTag)
                .map { tag -> tag.trim() }
                .filter { tag -> tag.isNotEmpty() && tag !in XrayTags.FIXED_OUTBOUND_TAGS }
                .toSet()
            return input.servers.filter { server -> server.id.toString() in routeOutboundTags }
        }

        /**
         * Shadowrocket profile groups are aliases over existing subscription/manual
         * servers. They never create a second hidden copy of an endpoint.
         */
        private fun addShadowrocketPolicyGroupTargets() {
            val usedTags = (input.routeRules.map { rule -> rule.outboundTag } + input.defaultRouteOutboundTag)
                .map(String::trim)
                .filter { tag -> tag.startsWith(ShadowrocketPolicyGroupTagPrefix) }
                .toSet()
            usedTags.forEach { tag ->
                val group = input.shadowrocketPolicyGroups.firstOrNull { it.outboundTag == tag } ?: return@forEach
                addShadowrocketPolicyGroup(tag, group)
            }
        }

        private fun addShadowrocketPolicyGroup(
            tag: String,
            group: ShadowrocketPolicyGroup,
        ) {
            val matchingStrategy = input.servers.mapNotNull { it.server as? StrategyGroup }
                .firstOrNull { it.sourcePolicyGroupName.equals(group.name, ignoreCase = true) }
            val members = if (matchingStrategy != null) {
                strategyGroupMembers(matchingStrategy)
            } else {
                shadowrocketPolicyGroupMembers(group)
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
                    addBalancer(
                        tag = tag,
                        strategy = group.type.toXrayBalancerStrategy(),
                        members = members,
                        selectedMemberId = matchingStrategy?.selectedMemberId,
                        keepsOrder = group.type.equals("fallback", ignoreCase = true),
                        probe = BalancerProbe(
                            url = matchingStrategy?.probeUrl?.trim()?.takeIf(String::isNotEmpty)
                                ?: group.url.trim().takeIf(String::isNotEmpty)
                                ?: input.subscriptionPingUrl.trim().takeIf(String::isNotEmpty),
                            interval = matchingStrategy?.probeInterval?.trim()?.takeIf(String::isNotEmpty)
                                ?: group.intervalSeconds?.let { "${it}s" },
                            timeout = matchingStrategy?.probeTimeout?.trim()?.takeIf(String::isNotEmpty)
                                ?: group.timeoutSeconds?.let { "${it}s" },
                        ),
                    )
                }

                else -> addNormalOutbound(tag = tag, server = members.first())
            }
        }

        private fun addRouteTarget(tag: String, server: ProxyServerRecord) {
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
            server: ProxyServerRecord,
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
            val members = strategyGroupMembers(strategyGroup)
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
            addBalancer(
                tag = tag,
                strategy = strategyGroup.strategy.toXrayBalancerStrategy(),
                members = members,
                selectedMemberId = strategyGroup.selectedMemberId,
                // A verified startup member is carried in selectedMemberId only for this generated
                // config; for an ordered fallback it must also be Xray's real fallbackTag.
                keepsOrder = strategyGroup.strategy == StrategyGroupConstants.TYPE_FALLBACK,
                probe = BalancerProbe(
                    url = strategyGroup.probeUrl.trim().takeIf(String::isNotEmpty)
                        ?: input.subscriptionPingUrl.trim().takeIf(String::isNotEmpty),
                    interval = strategyGroup.probeInterval.trim().takeIf(String::isNotEmpty),
                    timeout = strategyGroup.probeTimeout.trim().takeIf(String::isNotEmpty),
                ),
            )
        }

        /**
         * One balancer over [members]. A latency-driven strategy probes its members every round, so only the
         * best [BalancerProbeMemberLimit] join it: probing a whole subscription at once saturates the link,
         * times the probes out and marks healthy servers dead. A leastPing balancer only consumes the regular
         * Observatory, which probes members concurrently and lets fallbackTag carry traffic until its first result.
         */
        private fun addBalancer(
            tag: String,
            strategy: String,
            members: List<ProxyServerRecord>,
            selectedMemberId: Int?,
            keepsOrder: Boolean,
            probe: BalancerProbe,
        ) {
            val selector = "$tag-policy-"
            val joined = if (strategy.isLatencyDriven()) members.fastestFirst(selectedMemberId, BalancerProbeMemberLimit) else members
            joined.forEach { member -> addNormalOutbound(tag = "$selector${member.id}", server = member) }

            val fallbackMember = joined.firstOrNull { it.id == selectedMemberId }
                ?: if (keepsOrder) joined.first() else joined.minBy { it.latency.latencySortKey() }
            observatoryProbeUrl = observatoryProbeUrl ?: probe.url
            observatoryProbeInterval = shorterProbeInterval(observatoryProbeInterval, probe.interval)
            observatoryProbeTimeout = observatoryProbeTimeout ?: probe.timeout
            observatorySelectors += selector
            balancers += XrayBalancerPlan(
                tag = tag,
                selector = selector,
                strategy = strategy,
                fallbackTag = "$selector${fallbackMember.id}",
            )
            routeTargets[tag] = XrayRouteTarget(tag, XrayRouteTargetKind.Balancer)
        }

        private fun addChainProxy(tag: String, chainProxy: ChainProxy) {
            val members = chainProxyMembers(chainProxy)
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

        private fun chainProxyMembers(chainProxy: ChainProxy): List<ProxyServerRecord> {
            return chainProxy.proxyServerIds.mapNotNull { memberId ->
                input.servers.firstOrNull { server -> server.id == memberId && !server.server.isCompositeProxyServer() }
                    ?.takeUnless { server -> server.server.isCustomProxyServer() }
            }
        }

        private fun strategyGroupMembers(strategyGroup: StrategyGroup): List<ProxyServerRecord> =
            resolveStrategyGroupMembers(
                strategyGroup = strategyGroup,
                servers = input.servers,
                trafficConfigContentById = input.trafficConfigContentById,
                stripLeadingFlag = input.stripLeadingFlag,
            ).mapNotNull { resolved -> input.servers.firstOrNull { it.id == resolved.id } }

        private fun shadowrocketPolicyGroupMembers(group: ShadowrocketPolicyGroup): List<ProxyServerRecord> =
            resolveShadowrocketPolicyGroupMembers(
                group = group,
                policyGroups = input.shadowrocketPolicyGroups,
                servers = input.servers,
                trafficConfigContentById = input.trafficConfigContentById,
                stripLeadingFlag = input.stripLeadingFlag,
            ).mapNotNull { resolved -> input.servers.firstOrNull { it.id == resolved.id } }
    }
}

/** How many members a latency-driven balancer probes; the rest of a large subscription stays out of it. */
internal const val BalancerProbeMemberLimit = 16

/**
 * A dead member stays out of rotation until the next probe round, so a long default leaves every new
 * connection waiting on it. Used when no group asks for its own interval.
 */
private const val DefaultBalancerProbeInterval = "15s"

private class BalancerProbe(val url: String?, val interval: String?, val timeout: String?)

private fun String.isLatencyDriven(): Boolean =
    this == StrategyGroupConstants.TYPE_LEAST_PING || this == StrategyGroupConstants.TYPE_LEAST_LOAD

/**
 * At most [limit] members, the fastest known first. The member the user chose always stays; members never
 * measured keep their order behind the measured ones, and failed ones come last.
 */
private fun List<ProxyServerRecord>.fastestFirst(selectedMemberId: Int?, limit: Int): List<ProxyServerRecord> {
    if (size <= limit) return this
    val chosen = firstOrNull { it.id == selectedMemberId }
    val ranked = sortedBy { it.latency.latencySortKey() }.filter { it !== chosen }
    return (listOfNotNull(chosen) + ranked).take(limit)
}

/**
 * One observatory serves every balancer, so it probes at the shortest interval any group asks for.
 * A value that cannot be read ("soon") never replaces a valid one.
 */
private fun shorterProbeInterval(current: String?, candidate: String?): String? {
    val candidateSeconds = candidate?.probeIntervalSeconds() ?: return current
    val currentSeconds = current?.probeIntervalSeconds() ?: return candidate
    return if (candidateSeconds < currentSeconds) candidate else current
}

private fun String.probeIntervalSeconds(): Long? {
    val text = trim().lowercase()
    val unit = text.lastOrNull() ?: return null
    val amount = text.dropLast(1).toLongOrNull()?.takeIf { it > 0 } ?: return null
    return when (unit) {
        's' -> amount
        'm' -> amount * 60
        'h' -> amount * 3600
        else -> null
    }
}

private fun chainProxyOutboundTag(tag: String, index: Int): String =
    if (index == 0) tag else "$tag-chain-$index"

private fun String.latencySortKey(): Int {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed.startsWith("-") || trimmed.contains("Failed", ignoreCase = true) || trimmed.contains("Timeout", ignoreCase = true) || trimmed.contains("Error", ignoreCase = true)) {
        return Int.MAX_VALUE
    }
    val number = latencyRegex.find(trimmed)?.value?.toIntOrNull()
    return number ?: Int.MAX_VALUE
}

private val latencyRegex = Regex("""\d+""")
