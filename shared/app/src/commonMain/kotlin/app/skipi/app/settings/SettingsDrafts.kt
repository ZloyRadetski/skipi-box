// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxXudpConcurrency
import engine.xray.FragmentPacketsValues
import engine.xray.MuxUdp443Values
import engine.xray.MaxMuxConcurrency
import engine.xray.MaxMuxXudpConcurrency
import engine.vpn.VpnDefaults
import engine.network.isCidrAddress
import engine.network.isIpAddress
import engine.network.toPortOrNull
import utils.toIntInRangeOrNull
import features.settings.servicecontrol.ServiceControlSettings
import features.settings.servicecontrol.normalizeServiceControlSettings

/** Portable values read from persisted settings before editing. */
data class SettingsValues(
    val tunMtu: String,
    val tunVpnDns: String,
    val tunIpv4Cidr: String,
    val tunIpv6Cidr: String,
    val tunTcpKeepAliveInterval: String,
    val tunTcpUserTimeout: String,
    val localProxyPort: String,
    val dynamicLocalProxyPort: Boolean,
    val localProxyListenAllInterfaces: Boolean,
    val localProxyAuth: Boolean,
    val localProxyUsername: String,
    val localProxyPassword: String,
    val enableVpnLocalDns: Boolean,
    val enableFakeDns: Boolean,
    val enableResolveProxyServerDomain: Boolean,
    val proxyDns: List<String>,
    val directDns: List<String>,
    val directDnsDomains: List<String>,
    val enableDirectDnsForProxyServerDomains: Boolean,
    val dnsHosts: List<String>,
    val enableMux: Boolean,
    val muxConcurrency: String,
    val muxXudpConcurrency: String,
    val muxXudpProxyUdp443: Int,
    val enableFragment: Boolean,
    val fragmentPackets: String,
    val fragmentLength: String,
    val fragmentInterval: String,
    val subscriptionPingUrl: String,
    val subscriptionPingTimeoutMillis: String,
    val serviceControl: ServiceControlSettings,
)

data class TunSettingsDraft(
    val mtu: String = "",
    val vpnDns: String = "",
    val ipv4Cidr: String = "",
    val ipv6Cidr: String = "",
    val tcpKeepAliveInterval: String = "",
    val tcpUserTimeout: String = "",
)

data class LocalProxySettingsDraft(
    val port: String = "",
    val enableDynamicPort: Boolean = false,
    val listenAllInterfaces: Boolean = false,
    val enableAuth: Boolean = true,
    val username: String = "",
    val password: String = "",
)

data class DnsSettingsDraft(
    val enableVpnLocalDns: Boolean = true,
    val enableFakeDns: Boolean = false,
    val enableResolveProxyServerDomain: Boolean = false,
    val proxyDns: List<String> = emptyList(),
    val directDns: List<String> = emptyList(),
    val directDnsDomains: List<String> = emptyList(),
    val enableDirectDnsForProxyServerDomains: Boolean = true,
    val dnsHosts: List<String> = emptyList(),
)

data class MuxSettingsDraft(
    val enabled: Boolean = false,
    val concurrency: String = "",
    val xudpConcurrency: String = "",
    /** Persisted ordinal of [MuxUdp443Values], retained for existing profile compatibility. */
    val xudpProxyUdp443: Int = 0,
)

data class FragmentSettingsDraft(
    val enabled: Boolean = false,
    val packets: String = "",
    val length: String = "",
    val interval: String = "",
)

data class SubscriptionPingSettingsDraft(val url: String = "", val timeoutMillis: String = "")

fun generateRandomProxyCredential(prefix: String = "skipi_"): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val randomPart = (1..6).map { chars.random() }.joinToString("")
    return "$prefix$randomPart"
}

fun SettingsValues.toTunSettingsDraft() = TunSettingsDraft(
    tunMtu, tunVpnDns, tunIpv4Cidr, tunIpv6Cidr, tunTcpKeepAliveInterval, tunTcpUserTimeout,
)

fun SettingsValues.toLocalProxySettingsDraft() = LocalProxySettingsDraft(
    localProxyPort, dynamicLocalProxyPort, localProxyListenAllInterfaces,
    localProxyAuth, localProxyUsername, localProxyPassword,
)

fun SettingsValues.toDnsSettingsDraft() = DnsSettingsDraft(
    enableVpnLocalDns, enableVpnLocalDns && enableFakeDns, enableResolveProxyServerDomain,
    proxyDns, directDns, directDnsDomains,
    enableDirectDnsForProxyServerDomains, dnsHosts,
)

fun SettingsValues.toMuxSettingsDraft() = MuxSettingsDraft(
    enableMux, muxConcurrency, muxXudpConcurrency, muxXudpProxyUdp443.coerceIn(MuxUdp443Values.indices),
)

fun SettingsValues.toFragmentSettingsDraft() = FragmentSettingsDraft(
    enableFragment, fragmentPackets, fragmentLength, fragmentInterval,
)

fun SettingsValues.toSubscriptionPingSettingsDraft() = SubscriptionPingSettingsDraft(
    subscriptionPingUrl, subscriptionPingTimeoutMillis,
)

/** Match Android's save behavior: disabling local DNS also clears effective FakeDNS. */
fun DnsSettingsDraft.toSavedSettings(): DnsSettingsDraft = copy(
    enableFakeDns = enableVpnLocalDns && enableFakeDns,
    proxyDns = proxyDns.toSettingsList(),
    directDns = directDns.toSettingsList(),
    directDnsDomains = directDnsDomains.toSettingsList(),
    dnsHosts = dnsHosts.toSettingsList(),
)

fun MuxSettingsDraft.toSavedSettings(): MuxSettingsDraft = copy(
    concurrency = normalizeMuxInteger(concurrency, DefaultMuxConcurrency),
    xudpConcurrency = normalizeMuxInteger(xudpConcurrency, DefaultMuxXudpConcurrency),
    xudpProxyUdp443 = xudpProxyUdp443.coerceIn(MuxUdp443Values.indices),
)

fun FragmentSettingsDraft.toSavedSettings(): FragmentSettingsDraft = copy(
    packets = normalizeFragmentPackets(packets),
    length = normalizeFragmentRange(length, DefaultFragmentLength, min = 1),
    interval = normalizeFragmentRange(interval, DefaultFragmentInterval, min = 0),
)

fun normalizeMuxInteger(value: String, fallback: String = ""): String =
    value.trim().toIntOrNull()?.toString() ?: fallback

fun isValidMuxConcurrency(value: String): Boolean =
    value.toIntInRangeOrNull(-1..MaxMuxConcurrency) != null

fun isValidMuxXudpConcurrency(value: String): Boolean =
    value.toIntInRangeOrNull(-1..MaxMuxXudpConcurrency) != null

fun sanitizeMuxUdp443Index(index: Int): Int = index.coerceIn(MuxUdp443Values.indices)

fun normalizeFragmentPackets(value: String): String =
    value.trim().lowercase().takeIf { it in FragmentPacketsValues } ?: DefaultFragmentPackets

data class FragmentIntegerRange(val start: Int, val end: Int?)

fun parseFragmentIntegerRange(value: String, min: Int): FragmentIntegerRange? {
    val parts = value.trim().split("-")
    if (parts.size !in 1..2 || parts.any(String::isBlank)) return null
    val range = min..Int.MAX_VALUE
    val start = parts[0].toIntInRangeOrNull(range) ?: return null
    val end = if (parts.size == 2) parts[1].toIntInRangeOrNull(range) ?: return null else start
    if (start > end) return null
    return FragmentIntegerRange(start, end.takeIf { it != start })
}

fun isValidFragmentRange(value: String, min: Int): Boolean = parseFragmentIntegerRange(value, min) != null

fun normalizeFragmentRange(value: String, fallback: String, min: Int): String {
    val range = parseFragmentIntegerRange(value, min) ?: return fallback
    return range.end?.let { end -> "${range.start}-$end" } ?: range.start.toString()
}

fun TunSettingsDraft.hasValidRanges(): Boolean =
    isValidTunMtu(mtu) && isValidTunIpv4Cidr(ipv4Cidr) && isValidTunIpv6Cidr(ipv6Cidr) &&
        isValidTunTcpKeepAliveInterval(tcpKeepAliveInterval) && isValidTunTcpUserTimeout(tcpUserTimeout)

fun isValidTunMtu(value: String): Boolean =
    value.trim().toIntOrNull() in VpnDefaults.MTU_MIN..VpnDefaults.MTU_MAX

fun isValidTunVpnDns(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.contains('.') && !trimmed.contains(':') && isIpAddress(trimmed)
}

fun isValidTunIpv4Cidr(value: String): Boolean =
    value.contains('.') && !value.contains(':') && isCidrAddress(value)

fun isValidTunIpv6Cidr(value: String): Boolean = value.contains(':') && isCidrAddress(value)

fun isValidTunTcpKeepAliveInterval(value: String): Boolean =
    value.trim().toIntOrNull() in VpnDefaults.TCP_KEEP_ALIVE_INTERVAL_MIN..VpnDefaults.TCP_KEEP_ALIVE_INTERVAL_MAX

fun isValidTunTcpUserTimeout(value: String): Boolean =
    value.trim().toIntOrNull() in VpnDefaults.TCP_USER_TIMEOUT_MIN..VpnDefaults.TCP_USER_TIMEOUT_MAX

fun LocalProxySettingsDraft.hasValidPort(): Boolean = port.toPortOrNull() != null

fun ServiceControlSettings.toSettingsDraft(): ServiceControlSettings = normalizeServiceControlSettings(this)

private fun List<String>.toSettingsList(): List<String> =
    asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

