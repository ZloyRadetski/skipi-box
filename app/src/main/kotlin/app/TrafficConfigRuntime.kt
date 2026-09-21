// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import engine.xray.XrayTags
import features.config.ShadowrocketRule
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.config.ShadowrocketPolicyGroup
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.toXrayRouteRule
import features.routing.model.RouteRule
import utils.toTrimmedNonEmptyDistinctList

/** The active Shadowrocket profile is applied only when a VPN start request is built. */
internal fun AppState.withActiveTrafficConfigApplied(): AppState {
    val config = activeTrafficConfig() ?: return this
    val androidSettings = config.androidSettings
    val analysis = config.rawConfig.analyzeShadowrocketConfig()
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
    val configuredProxyDns = androidSettings.proxyDns.takeIf {
        "proxy-dns" in skipiDnsKeys && it.isNotEmpty()
    }
    val configuredDirectDns = androidSettings.directDns.takeIf {
        "direct-dns" in skipiDnsKeys && it.isNotEmpty()
    }
    val configuredDnsHosts = androidSettings.dnsHosts.takeIf {
        "dns-hosts" in skipiDnsKeys && it.isNotEmpty()
    }
    val finalRule = analysis.rules.lastOrNull(ShadowrocketRule::isFinal)
    val resolvedRules = analysis.rules
        .filterNot(ShadowrocketRule::isFinal)
        .mapIndexedNotNull { index, rule ->
            rule.toXrayRouteRule(index + 1) { policy ->
                resolveShadowrocketPolicy(policy, policyGroups = analysis.proxyGroups)
            }
        }

    return copy(
        routeRules = resolvedRules,
        nextRouteRuleId = (resolvedRules.maxOfOrNull(RouteRule::id) ?: 0) + 1,
        defaultRouteOutboundTag = finalRule
            ?.policy
            ?.let { policy -> resolveShadowrocketPolicy(policy, policyGroups = analysis.proxyGroups) }
            ?: XrayTags.PROXY,
        enableSniffing = androidSettings.enableSniffing,
        enableSniffingRouteOnly = androidSettings.enableSniffingRouteOnly,
        routeDomainStrategy = androidSettings.routeDomainStrategy,
        enableMux = androidSettings.enableMux,
        muxConcurrency = androidSettings.muxConcurrency,
        muxXudpConcurrency = androidSettings.muxXudpConcurrency,
        muxXudpProxyUdp443 = androidSettings.muxXudpProxyUdp443,
        enableFragment = androidSettings.enableFragment,
        fragmentPackets = androidSettings.fragmentPackets,
        fragmentLength = androidSettings.fragmentLength,
        fragmentInterval = androidSettings.fragmentInterval,
        enableVpnLocalDns = androidSettings.enableVpnLocalDns,
        enableFakeDns = androidSettings.enableFakeDns,
        fakeDnsIpPool = androidSettings.fakeDnsIpPool,
        fakeDnsPoolSize = androidSettings.fakeDnsPoolSize,
        tunVpnDns = androidSettings.tunVpnDns,
        enableIpv6 = general["ipv6"].toConfigBooleanOrDefault(enableIpv6),
        enableIpv6Prefer = general["prefer-ipv6"].toConfigBooleanOrDefault(enableIpv6Prefer),
        enableDirectDnsForProxyServerDomains = androidSettings.enableDirectDnsForProxyServerDomains,
        enableResolveProxyServerDomain = androidSettings.enableResolveProxyServerDomain,
        proxyDns = configuredProxyDns ?: dnsServers.takeIf(List<String>::isNotEmpty) ?: androidSettings.proxyDns.takeIf(List<String>::isNotEmpty) ?: proxyDns,
        directDns = configuredDirectDns ?: dnsServers.takeIf(List<String>::isNotEmpty) ?: androidSettings.directDns.takeIf(List<String>::isNotEmpty) ?: directDns,
        directDnsDomains = androidSettings.directDnsDomains.takeIf(List<String>::isNotEmpty) ?: directDnsDomains,
        dnsHosts = configuredDnsHosts ?: hosts.takeIf(List<String>::isNotEmpty) ?: androidSettings.dnsHosts.takeIf(List<String>::isNotEmpty) ?: dnsHosts,
        proxyAppListMode = config.proxyAppListMode,
        proxyAppListSelectedApps = config.proxyAppListSelectedApps,
        shadowrocketPolicyGroups = analysis.proxyGroups,
    )
}

internal fun AppState.activeTrafficConfig(): TrafficConfigState? {
    return trafficConfigs.firstOrNull { config -> config.id == activeTrafficConfigId }
        ?: trafficConfigs.firstOrNull()
}

/** A CONFIG:id policy is SKIPI metadata: it resolves to that profile's FINAL target. */
private fun AppState.resolveShadowrocketPolicy(
    policy: String,
    policyGroups: List<ShadowrocketPolicyGroup> = shadowrocketPolicyGroups,
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
                ?.let { id -> trafficConfigs.firstOrNull { config -> config.id == id } }
            referenced
                ?.rawConfig
                ?.analyzeShadowrocketConfig()
                ?.rules
                ?.lastOrNull(ShadowrocketRule::isFinal)
                ?.let { finalRule ->
                    resolveShadowrocketPolicy(
                        policy = finalRule.policy,
                        policyGroups = referenced.rawConfig.analyzeShadowrocketConfig().proxyGroups,
                        visitedConfigIds = visitedConfigIds + referenced.id,
                    )
                }
                ?: XrayTags.PROXY
        }
        else -> proxyServers.firstOrNull { state ->
            state.server.getInfo().remarks.equals(normalized, ignoreCase = true)
        }?.proxyServerOutboundTag()
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
    val value = trimmed.substring(separator + 1).trim()
        .removePrefix("server:")
    return "$domain:$value".takeIf { domain.isNotEmpty() && value.isNotEmpty() }
}

private fun String?.toConfigBooleanOrDefault(defaultValue: Boolean): Boolean {
    return when (this?.trim()?.lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> defaultValue
    }
}
