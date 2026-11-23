// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.XrayTags
import features.routing.model.RouteRule
import utils.toConfigBooleanOrDefault
import utils.toTrimmedNonEmptyDistinctList

data class TrafficConfigReference(val id: Int, val rawConfig: String)

data class TrafficConfigServerTarget(val remarks: String, val outboundTag: String)

data class TrafficConfigDnsInputs(
    val profileProxyDns: List<String> = emptyList(),
    val profileDirectDns: List<String> = emptyList(),
    val profileDirectDnsDomains: List<String> = emptyList(),
    val profileDnsHosts: List<String> = emptyList(),
    val existingProxyDns: List<String> = emptyList(),
    val existingDirectDns: List<String> = emptyList(),
    val existingDirectDnsDomains: List<String> = emptyList(),
    val existingDnsHosts: List<String> = emptyList(),
)

data class TrafficConfigRuntimePlanInput(
    val rawConfig: String,
    val configs: List<TrafficConfigReference>,
    val servers: List<TrafficConfigServerTarget>,
    val enableIpv6Fallback: Boolean,
    val enableIpv6PreferFallback: Boolean,
    val dnsInputs: TrafficConfigDnsInputs = TrafficConfigDnsInputs(),
)

data class TrafficConfigRuntimePlan(
    val routeRules: List<RouteRule>,
    val nextRouteRuleId: Int,
    val defaultRouteOutboundTag: String,
    val proxyDns: List<String>,
    val directDns: List<String>,
    val directDnsDomains: List<String>,
    val dnsHosts: List<String>,
    val enableIpv6: Boolean,
    val enableIpv6Prefer: Boolean,
    val policyGroups: List<ShadowrocketPolicyGroup>,
)

/** Pure planning for applying a portable Shadowrocket profile to runtime routing and DNS values. */
object TrafficConfigRuntimePlanner {
    fun plan(input: TrafficConfigRuntimePlanInput): TrafficConfigRuntimePlan {
        val analysis = input.rawConfig.analyzeShadowrocketConfig()
        val general = analysis.general
        val dnsServers = general["dns-server"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter { server -> server.isNotEmpty() && !server.equals("system", ignoreCase = true) }
            ?.distinct()
            .orEmpty()
        val hosts = analysis.sections["host"].orEmpty()
            .mapNotNull(::shadowrocketHostToXrayHost)
            .toTrimmedNonEmptyDistinctList()
        val skipiDnsKeys = analysis.sections["skipi"].orEmpty()
            .asSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') && !line.startsWith(';') }
            .map { line -> line.substringBefore('=').trim().lowercase() }
            .filter(String::isNotEmpty)
            .toSet()

        val configuredProxyDns = input.dnsInputs.profileProxyDns.takeIf {
            "proxy-dns" in skipiDnsKeys && it.isNotEmpty()
        }
        val configuredDirectDns = input.dnsInputs.profileDirectDns.takeIf {
            "direct-dns" in skipiDnsKeys && it.isNotEmpty()
        }
        val configuredDnsHosts = input.dnsInputs.profileDnsHosts.takeIf {
            "dns-hosts" in skipiDnsKeys && it.isNotEmpty()
        }

        val finalRule = analysis.rules.lastOrNull(ShadowrocketRule::isFinal)
        val policyResolver = ShadowrocketPolicyResolver(input.configs, input.servers)
        val resolvedRules = buildList {
            var nextId = 1
            analysis.rules.filterNot(ShadowrocketRule::isFinal).forEachIndexed { index, rule ->
                // An OR / AND rule can expand to several Xray rules, so ids follow the produced rules.
                val produced = rule.toXrayRouteRules(maxOf(index + 1, nextId)) { policy ->
                    policyResolver.resolve(policy, analysis.proxyGroups, "")
                }
                addAll(produced)
                nextId = (produced.maxOfOrNull(RouteRule::id) ?: (nextId - 1)) + 1
            }
        }
        val defaultRouteOutboundTag = finalRule
            ?.policy
            ?.let { policy ->
                policyResolver.resolve(policy, analysis.proxyGroups, "")
            }
            ?: XrayTags.PROXY

        return TrafficConfigRuntimePlan(
            routeRules = resolvedRules,
            nextRouteRuleId = (resolvedRules.maxOfOrNull(RouteRule::id) ?: 0) + 1,
            defaultRouteOutboundTag = defaultRouteOutboundTag,
            proxyDns = configuredProxyDns ?: dnsServers.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.profileProxyDns.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.existingProxyDns,
            directDns = configuredDirectDns ?: dnsServers.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.profileDirectDns.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.existingDirectDns,
            directDnsDomains = input.dnsInputs.profileDirectDnsDomains.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.existingDirectDnsDomains,
            dnsHosts = configuredDnsHosts ?: hosts.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.profileDnsHosts.takeIf(List<String>::isNotEmpty)
                ?: input.dnsInputs.existingDnsHosts,
            enableIpv6 = general["ipv6"].toConfigBooleanOrDefault(input.enableIpv6Fallback),
            enableIpv6Prefer = general["prefer-ipv6"].toConfigBooleanOrDefault(input.enableIpv6PreferFallback),
            policyGroups = analysis.proxyGroups,
        )
    }
}

/**
 * Resolves a rule policy to an outbound tag. The groups of each profile, the servers and the analyses of
 * referenced CONFIG profiles are indexed once (first match wins, as a linear scan would) instead of being
 * rescanned or re-parsed for every rule.
 */
private class ShadowrocketPolicyResolver(
    private val configs: List<TrafficConfigReference>,
    servers: List<TrafficConfigServerTarget>,
) {
    private val outboundTagByRemarks = HashMap<String, String>().also { index ->
        servers.forEach { server -> index.getOrPut(server.remarks.lowercase()) { server.outboundTag } }
    }
    private val analysisByConfigId = HashMap<Int, ShadowrocketConfigAnalysis>()
    private val groupsByScope = HashMap<String, Map<String, ShadowrocketPolicyGroup>>()

    /** [groupsScope] names which profile [policyGroups] belong to ("" for the active one). */
    fun resolve(
        policy: String,
        policyGroups: List<ShadowrocketPolicyGroup>,
        groupsScope: String,
        visitedConfigIds: Set<Int> = emptySet(),
    ): String {
        val normalized = policy.trim()
        val groups = groupsByScope.getOrPut(groupsScope) {
            HashMap<String, ShadowrocketPolicyGroup>().also { index ->
                policyGroups.forEach { group -> index.getOrPut(group.name.lowercase()) { group } }
            }
        }
        groups[normalized.lowercase()]?.let { return it.outboundTag }
        return when {
            normalized.equals("PROXY", ignoreCase = true) -> XrayTags.PROXY
            normalized.equals("DIRECT", ignoreCase = true) -> XrayTags.DIRECT
            normalized.startsWith("REJECT", ignoreCase = true) || normalized.equals("BLOCK", ignoreCase = true) -> XrayTags.BLOCK
            normalized.startsWith("CONFIG:", ignoreCase = true) -> {
                val configId = normalized.substringAfter(':').trim().toIntOrNull()
                val referenced = configId
                    ?.takeUnless { it in visitedConfigIds }
                    ?.let { id -> configs.firstOrNull { config -> config.id == id } }
                val referencedAnalysis = referenced?.let { config ->
                    analysisByConfigId.getOrPut(config.id) { config.rawConfig.analyzeShadowrocketConfig() }
                }
                referencedAnalysis
                    ?.rules
                    ?.lastOrNull(ShadowrocketRule::isFinal)
                    ?.let { finalRule ->
                        resolve(
                            policy = finalRule.policy,
                            policyGroups = referencedAnalysis.proxyGroups,
                            groupsScope = "config:${requireNotNull(referenced).id}",
                            visitedConfigIds = visitedConfigIds + requireNotNull(referenced).id,
                        )
                    }
                    ?: XrayTags.PROXY
            }
            else -> outboundTagByRemarks[normalized.lowercase()]
                ?: normalized.removePrefix(ShadowrocketPolicyGroupTagPrefix)
                    .let { tag ->
                        when {
                            tag.equals("PROXY", ignoreCase = true) -> XrayTags.PROXY
                            tag.equals("DIRECT", ignoreCase = true) -> XrayTags.DIRECT
                            tag.equals("BLOCK", ignoreCase = true) || tag.startsWith("REJECT", ignoreCase = true) -> XrayTags.BLOCK
                            else -> tag
                        }
                    }
        }
    }
}

private fun shadowrocketHostToXrayHost(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isBlank() || trimmed.startsWith('#') || trimmed.startsWith(';')) return null
    val separator = trimmed.indexOf('=')
    if (separator <= 0 || separator == trimmed.lastIndex) return null
    val domain = trimmed.substring(0, separator).trim()
    val value = trimmed.substring(separator + 1).trim().removePrefix("server:")
    return "$domain:$value".takeIf { domain.isNotEmpty() && value.isNotEmpty() }
}
