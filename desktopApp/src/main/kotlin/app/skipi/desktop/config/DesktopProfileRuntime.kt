// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.xray.XraySniffingOptions
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigDnsInputs
import features.config.TrafficConfigReference
import features.config.TrafficConfigRuntimePlan
import features.config.TrafficConfigRuntimePlanInput
import features.config.TrafficConfigRuntimePlanner
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig

/**
 * What the active profile decides for a start, read the way Android's `withActiveTrafficConfigApplied`
 * reads it: the profile owns IPv6, DNS, hosts, sniffing, Mux, Fragment and the TUN DNS, so none of
 * them is a global Desktop setting while a profile is active.
 */
internal class DesktopProfileRuntime(
    val settings: TrafficConfigAndroidSettings,
    val plan: TrafficConfigRuntimePlan,
)

internal fun planDesktopProfileRuntime(
    profile: DesktopStoredConfig,
    configs: List<DesktopStoredConfig>,
): DesktopProfileRuntime {
    val settings = TrafficConfigState(id = profile.id, name = profile.name, rawConfig = profile.content)
        .withSkipiSettingsReadFromRawConfig()
        .androidSettings
    val defaults = DesktopDnsInputs()
    val plan = TrafficConfigRuntimePlanner.plan(
        TrafficConfigRuntimePlanInput(
            rawConfig = profile.content,
            configs = configs.map { config -> TrafficConfigReference(config.id, config.content) },
            servers = emptyList(),
            enableIpv6Fallback = false,
            enableIpv6PreferFallback = false,
            dnsInputs = TrafficConfigDnsInputs(
                profileProxyDns = settings.proxyDns,
                profileDirectDns = settings.directDns,
                profileDirectDnsDomains = settings.directDnsDomains,
                profileDnsHosts = settings.dnsHosts,
                existingProxyDns = defaults.proxyDns,
                existingDirectDns = defaults.directDns,
                existingDirectDnsDomains = defaults.directDnsDomains,
                existingDnsHosts = defaults.dnsHosts,
            ),
        ),
    )
    return DesktopProfileRuntime(settings, plan)
}

/** The runtime of the selected profile; null when no profile is selected. */
internal fun DesktopConfigLibrary.activeProfileRuntime(): DesktopProfileRuntime? =
    activeProfile?.let { profile -> planDesktopProfileRuntime(profile, configs) }

internal fun DesktopProfileRuntime.toDnsInputs(): DesktopDnsInputs = DesktopDnsInputs(
    proxyDns = plan.proxyDns,
    directDns = plan.directDns,
    directDnsDomains = plan.directDnsDomains,
    dnsHosts = plan.dnsHosts,
    enableIpv6 = plan.enableIpv6,
    enableIpv6Prefer = plan.enableIpv6Prefer,
    tunDns = settings.tunVpnDns,
)

/** Android's TUN DNS: the active profile's value replaces the global one, even when the profile leaves it at the default. */
internal fun DesktopProfileRuntime?.tunVpnDnsOr(globalTunVpnDns: String): String =
    this?.settings?.tunVpnDns ?: globalTunVpnDns

internal fun DesktopProfileRuntime.sniffingOptions() = XraySniffingOptions(
    enabled = settings.enableSniffing,
    routeOnly = settings.enableSniffingRouteOnly,
)
