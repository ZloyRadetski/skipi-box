// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.config.TrafficConfigState
import features.config.TrafficConfigDnsInputs
import features.config.TrafficConfigReference
import features.config.TrafficConfigRuntimePlanInput
import features.config.TrafficConfigRuntimePlanner
import features.config.TrafficConfigServerTarget

/** The active Shadowrocket profile is applied only when a VPN start request is built. */
internal fun AppState.withActiveTrafficConfigApplied(): AppState {
    val config = activeTrafficConfig() ?: return this
    val androidSettings = config.androidSettings
    val plan = TrafficConfigRuntimePlanner.plan(
        TrafficConfigRuntimePlanInput(
            rawConfig = config.rawConfig,
            configs = trafficConfigs.map { profile -> TrafficConfigReference(profile.id, profile.rawConfig) },
            servers = proxyServers.map { server ->
                TrafficConfigServerTarget(
                    remarks = server.server.getInfo().remarks,
                    outboundTag = server.proxyServerOutboundTag(),
                )
            },
            enableIpv6Fallback = enableIpv6,
            enableIpv6PreferFallback = enableIpv6Prefer,
            dnsInputs = TrafficConfigDnsInputs(
                profileProxyDns = androidSettings.proxyDns,
                profileDirectDns = androidSettings.directDns,
                profileDirectDnsDomains = androidSettings.directDnsDomains,
                profileDnsHosts = androidSettings.dnsHosts,
                existingProxyDns = proxyDns,
                existingDirectDns = directDns,
                existingDirectDnsDomains = directDnsDomains,
                existingDnsHosts = dnsHosts,
            ),
        ),
    )

    return copy(
        routeRules = plan.routeRules,
        nextRouteRuleId = plan.nextRouteRuleId,
        defaultRouteOutboundTag = plan.defaultRouteOutboundTag,
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
        enableIpv6 = plan.enableIpv6,
        enableIpv6Prefer = plan.enableIpv6Prefer,
        enableDirectDnsForProxyServerDomains = androidSettings.enableDirectDnsForProxyServerDomains,
        enableResolveProxyServerDomain = androidSettings.enableResolveProxyServerDomain,
        proxyDns = plan.proxyDns,
        directDns = plan.directDns,
        directDnsDomains = plan.directDnsDomains,
        dnsHosts = plan.dnsHosts,
        proxyAppListMode = config.proxyAppListMode,
        proxyAppListSelectedApps = config.proxyAppListSelectedApps,
        shadowrocketPolicyGroups = plan.policyGroups,
    )
}

internal fun AppState.activeTrafficConfig(): TrafficConfigState? {
    return trafficConfigs.firstOrNull { config -> config.id == activeTrafficConfigId }
        ?: trafficConfigs.firstOrNull()
}
