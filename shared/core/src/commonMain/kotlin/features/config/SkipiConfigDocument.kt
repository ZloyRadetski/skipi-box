// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** Standard name of SKIPI's portable profile metadata section. */
const val SkipiSection = "SKIPI"

const val SkipiPerAppModeComment = "# SKIPI-PER-APP-MODE:"
const val SkipiPerAppItemComment = "# SKIPI-PER-APP:"

const val SkipiProfileName = "profile-name"
const val SkipiProfileUpdateUrl = "profile-update-url"
const val SkipiProfileUpdateLocked = "profile-update-locked"
const val SkipiProfileAutoUpdate = "profile-auto-update"
const val SkipiProfileUpdateInterval = "profile-update-interval"
const val SkipiPerAppMode = "per-app-mode"
const val SkipiPerAppPackage = "per-app-package"
const val SkipiSniffing = "sniffing"
const val SkipiSniffingRouteOnly = "sniffing-route-only"
const val SkipiMux = "mux"
const val SkipiMuxConcurrency = "mux-concurrency"
const val SkipiMuxXudpConcurrency = "mux-xudp-concurrency"
const val SkipiMuxUdp443 = "mux-udp-443"
const val SkipiFragment = "fragment"
const val SkipiFragmentPackets = "fragment-packets"
const val SkipiFragmentLength = "fragment-length"
const val SkipiFragmentInterval = "fragment-interval"
const val SkipiVpnLocalDns = "vpn-local-dns"
const val SkipiFakeDns = "fake-dns"
const val SkipiFakeDnsIpPool = "fake-dns-ip-pool"
const val SkipiFakeDnsPoolSize = "fake-dns-pool-size"
const val SkipiTunDns = "tun-dns"
const val SkipiResolveProxyServerDomain = "resolve-proxy-server-domain"
const val SkipiDirectDnsForProxyServerDomains = "direct-dns-fallback-proxy"
const val SkipiProxyDns = "proxy-dns"
const val SkipiDirectDns = "direct-dns"
const val SkipiDirectDnsDomains = "direct-dns-domains"
const val SkipiDnsHosts = "dns-hosts"
const val SkipiRouteDomainStrategy = "route-domain-strategy"
const val SkipiNetworkActivation = "network-activation"
const val SkipiNetworkTransport = "network-transport"
const val SkipiResourceSource = "resource-source"
const val SkipiResourceGeoIpUrl = "resource-geoip-url"
const val SkipiResourceGeoSiteUrl = "resource-geosite-url"
const val SkipiResourceGeoIpOnlyCnPrivateUrl = "resource-geoip-cn-private-url"
const val SkipiResourceDirectCidrIpv4Url = "resource-direct-cidr-ipv4-url"
const val SkipiResourceDirectCidrIpv6Url = "resource-direct-cidr-ipv6-url"
const val SkipiResourceUserAgent = "resource-user-agent"
const val SkipiResourceAutoUpdate = "resource-auto-update"
const val SkipiResourceUpdateInterval = "resource-update-interval"
const val SkipiResourceCustomFile = "resource-custom-file"

/** Numeric values retained for compatibility with Android's persisted application-policy modes. */
const val SkipiPerAppModeBlacklist = 0
const val SkipiPerAppModeWhitelist = 1
const val SkipiPerAppModeGlobal = 2

data class SkipiPerAppSettings(
    val mode: Int,
    val selectedApps: List<String>,
)

/**
 * Reads the complete portable `[SKIPI]` section, retaining repeated keys in
 * document order. The final value is therefore the effective scalar value.
 */
fun String.skipiSectionValues(): Map<String, List<String>> =
    analyzeShadowrocketConfig().sections[SkipiSection.lowercase()].orEmpty()
        .asSequence()
        .map(String::trim)
        .filter { line -> line.isNotEmpty() && !line.startsWith('#') && !line.startsWith(';') }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else {
                line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }
        }
        .groupBy({ (key, _) -> key }, { (_, value) -> value })

/** Converts parsed values back to ordinary `key = value` lines for `[SKIPI]`. */
fun Map<String, List<String>>.toSkipiSectionLines(): List<String> =
    flatMap { (key, values) -> values.map { value -> "$key = $value" } }

/**
 * Compatibility writer for the portable per-app policy. Old comment-based
 * profiles are normalized to ordinary `[SKIPI]` key/value entries on save.
 */
fun String.withSkipiPerAppSettings(
    mode: Int,
    selectedApps: List<String>,
): String {
    val modeValue = when (mode) {
        SkipiPerAppModeBlacklist -> "blacklist"
        SkipiPerAppModeWhitelist -> "whitelist"
        else -> "global"
    }
    val values = skipiSectionValues().toMutableMap()
    values[SkipiPerAppMode] = listOf(modeValue)
    values[SkipiPerAppPackage] = selectedApps.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    return withoutLegacySkipiPerAppComments().withShadowrocketSectionLines(
        SkipiSection,
        values.toSkipiSectionLines(),
    )
}

/**
 * Reads the per-app policy from `[SKIPI]`, or from the short-lived legacy
 * comments when a profile has not yet been normalized.
 */
fun String.parseSkipiPerAppSettings(): SkipiPerAppSettings {
    val values = skipiSectionValues()
    values[SkipiPerAppMode]?.lastOrNull()?.let { modeValue ->
        val mode = when (modeValue.trim().lowercase()) {
            "blacklist" -> SkipiPerAppModeBlacklist
            "whitelist" -> SkipiPerAppModeWhitelist
            else -> SkipiPerAppModeGlobal
        }
        return SkipiPerAppSettings(
            mode = mode,
            selectedApps = values[SkipiPerAppPackage].orEmpty()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
        )
    }

    var mode = SkipiPerAppModeGlobal
    val selectedApps = mutableListOf<String>()
    lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith(SkipiPerAppModeComment, ignoreCase = true) -> {
                mode = when (trimmed.substringAfter(':').trim().lowercase()) {
                    "blacklist" -> SkipiPerAppModeBlacklist
                    "whitelist" -> SkipiPerAppModeWhitelist
                    else -> SkipiPerAppModeGlobal
                }
            }

            trimmed.startsWith(SkipiPerAppItemComment, ignoreCase = true) -> {
                trimmed.substringAfter(':').trim().takeIf(String::isNotBlank)?.let(selectedApps::add)
            }
        }
    }
    return SkipiPerAppSettings(mode = mode, selectedApps = selectedApps.distinct())
}

/** Removes only the legacy per-app comments and leaves every other line intact. */
fun String.withoutLegacySkipiPerAppComments(): String =
    lineSequence()
        .filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith(SkipiPerAppModeComment, ignoreCase = true) ||
                trimmed.startsWith(SkipiPerAppItemComment, ignoreCase = true)
        }
        .joinToString("\n")
        .trimEnd()

/** Parses the documented boolean spellings while retaining the caller's fallback for unknown values. */
fun String.toSkipiConfigBoolean(fallback: Boolean): Boolean = when (trim().lowercase()) {
    "true", "yes", "1" -> true
    "false", "no", "0" -> false
    else -> fallback
}
