// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigNetworkTransportCellular
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.config.TrafficConfigNetworkTransportWifi
import features.config.TrafficConfigAndroidSettings
import features.config.withShadowrocketGeneralValue

/** Applies the portable general options while preserving unrelated profile text and sections. */
fun TrafficConfigState.withGeneralOptions(
    ipv6: Boolean,
    preferIpv6: Boolean,
): TrafficConfigState = copy(
    rawConfig = rawConfig
        .withShadowrocketGeneralValue("ipv6", ipv6.toString())
        .withShadowrocketGeneralValue("prefer-ipv6", preferIpv6.toString()),
)

/** Applies an editor selection using only transport values understood by the shared model. */
fun TrafficConfigState.withNetworkActivation(
    enabled: Boolean,
    transport: Int,
): TrafficConfigState = copy(
    networkActivation = TrafficConfigNetworkActivation(
        enabled = enabled,
        transport = transport.coerceIn(
            TrafficConfigNetworkTransportWifi,
            TrafficConfigNetworkTransportCellular,
        ),
    ),
)

/** Applies portable profile identity and update controls while preserving other profile data. */
fun TrafficConfigState.withProfileBasics(
    name: String,
    sourceUrl: String,
    updateLocked: Boolean,
    autoUpdate: Boolean,
    updateInterval: String,
    resourceAutoUpdate: Boolean,
    resourceUpdateInterval: String,
): TrafficConfigState = copy(
    name = name.trim().ifBlank { this.name },
    sourceUrl = sourceUrl.trim(),
    updateLocked = updateLocked,
    autoUpdate = autoUpdate,
    updateInterval = updateInterval.trim(),
    resourceSettings = resourceSettings.copy(
        autoUpdate = resourceAutoUpdate,
        updateInterval = resourceUpdateInterval.trim(),
    ),
).withSkipiSettingsInRawConfig()

/** Applies DNS editor values, retaining DNS list order and unrelated `.conf` content. */
fun TrafficConfigState.withDnsOptions(settings: TrafficConfigAndroidSettings): TrafficConfigState {
    val normalized = settings.copy(
        tunVpnDns = settings.tunVpnDns.trim().takeIf(String::isNotEmpty) ?: features.config.TrafficProfileDefaultTunDns,
        proxyDns = settings.proxyDns.normalizedEditorValues(),
        directDns = settings.directDns.normalizedEditorValues(),
        directDnsDomains = settings.directDnsDomains.normalizedEditorValues(),
        dnsHosts = settings.dnsHosts.normalizedEditorValues(),
    )
    return copy(
        androidSettings = normalized,
        rawConfig = rawConfig.withShadowrocketGeneralValue(
            "dns-server",
            normalized.directDns.firstOrNull() ?: "system",
        ),
    ).withSkipiSettingsInRawConfig()
}

/** Applies portable sniffing/mux/fragment options and preserves every other setting. */
fun TrafficConfigState.withTunnelOptions(
    settings: TrafficConfigAndroidSettings,
    muxConcurrency: String,
): TrafficConfigState = copy(
    androidSettings = settings.copy(muxConcurrency = muxConcurrency.trim()),
)

private fun List<String>.normalizedEditorValues(): List<String> =
    map(String::trim).filter(String::isNotEmpty).distinct()
