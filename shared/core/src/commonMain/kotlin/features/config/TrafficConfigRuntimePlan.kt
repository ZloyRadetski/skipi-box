// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.XrayTags
import features.routing.model.RouteRule
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
        val resolvedRules = analysis.rules
            .filterNot(ShadowrocketRule::isFinal)
            .mapIndexedNotNull { index, rule ->
                rule.toXrayRouteRule(index + 1) { policy ->
                    resolveShadowrocketPolicy(
                        policy = policy,
                        policyGroups = analysis.proxyGroups,
                        configs = input.configs,
                        servers = input.servers,
                    )
                }
            }
        val defaultRouteOutboundTag = finalRule
            ?.policy
            ?.let { policy ->
                resolveShadowrocketPolicy(
                    policy = policy,
                    policyGroups = analysis.proxyGroups,
                    configs = input.configs,
                    servers = input.servers,
                )
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

private fun resolveShadowrocketPolicy(
    policy: String,
    policyGroups: List<ShadowrocketPolicyGroup>,
    configs: List<TrafficConfigReference>,
    servers: List<TrafficConfigServerTarget>,
    visitedConfigIds: Set<Int> = emptySet(),
): String {
    val normalized = policy.trim()
    return when {
        policyGroups.any { group -> group.name.equals(normalized, ignoreCase = true) } ->
            policyGroups.first { group -> group.name.equals(normalized, ignoreCase = true) }.outboundTag
        normalized.equals("PROXY", ignoreCase = true) -> XrayTags.PROXY
        normalized.equals("DIRECT", ignoreCase = true) -> XrayTags.DIRECT
        normalized.startsWith("REJECT", ignoreCase = true) || normalized.equals("BLOCK", ignoreCase = true) -> XrayTags.BLOCK
        normalized.startsWith("CONFIG:", ignoreCase = true) -> {
            val configId = normalized.substringAfter(':').trim().toIntOrNull()
            val referenced = configId
                ?.takeUnless { it in visitedConfigIds }
                ?.let { id -> configs.firstOrNull { config -> config.id == id } }
            val referencedAnalysis = referenced?.rawConfig?.analyzeShadowrocketConfig()
            referencedAnalysis
                ?.rules
                ?.lastOrNull(ShadowrocketRule::isFinal)
                ?.let { finalRule ->
                    resolveShadowrocketPolicy(
                        policy = finalRule.policy,
                        policyGroups = referencedAnalysis.proxyGroups,
                        configs = configs,
                        servers = servers,
                        visitedConfigIds = visitedConfigIds + requireNotNull(referenced).id,
                    )
                }
                ?: XrayTags.PROXY
        }
        else -> servers.firstOrNull { server -> server.remarks.equals(normalized, ignoreCase = true) }?.outboundTag
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

private fun shadowrocketHostToXrayHost(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isBlank() || trimmed.startsWith('#') || trimmed.startsWith(';')) return null
    val separator = trimmed.indexOf('=')
    if (separator <= 0 || separator == trimmed.lastIndex) return null
    val domain = trimmed.substring(0, separator).trim()
    val value = trimmed.substring(separator + 1).trim().removePrefix("server:")
    return "$domain:$value".takeIf { domain.isNotEmpty() && value.isNotEmpty() }
}

private fun String?.toConfigBooleanOrDefault(defaultValue: Boolean): Boolean = when (this?.trim()?.lowercase()) {
    "true", "yes", "1" -> true
    "false", "no", "0" -> false
    else -> defaultValue
}
