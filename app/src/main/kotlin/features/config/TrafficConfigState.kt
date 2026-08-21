// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.net.Uri
import app.modes.ProxyAppListModeGlobal
import app.AppState
import app.CustomResourceFileState
import app.ProxyServerState
import engine.vpn.VpnDefaults
import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import features.resources.ResourceFileDirectCidrIpv4Url
import features.resources.ResourceFileDirectCidrIpv6Url
import features.resources.ResourceFileLoyalsoldierGeoIpUrl
import features.resources.ResourceFileLoyalsoldierGeoSiteUrl
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileV2FlyGeoIpOnlyCnPrivateUrl
import features.subscription.DefaultSubscriptionUserAgent
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import kotlinx.serialization.Serializable

/**
 * Android-only additions to a standard Shadowrocket profile.
 *
 * Shadowrocket has no syntax for these Xray/VpnService capabilities. SKIPI
 * stores them as ordinary values in its `[SKIPI]` section, so a `.conf` is a
 * complete portable profile rather than a split file plus local metadata.
 */
@Serializable
data class TrafficConfigAndroidSettings(
    val enableSniffing: Boolean = true,
    val enableSniffingRouteOnly: Boolean = true,
    val enableMux: Boolean = false,
    val muxConcurrency: String = DefaultMuxConcurrency,
    val muxXudpConcurrency: String = DefaultMuxXudpConcurrency,
    val muxXudpProxyUdp443: Int = DefaultMuxUdp443Mode,
    val enableFragment: Boolean = false,
    val fragmentPackets: String = DefaultFragmentPackets,
    val fragmentLength: String = DefaultFragmentLength,
    val fragmentInterval: String = DefaultFragmentInterval,
    val enableVpnLocalDns: Boolean = true,
    val enableFakeDns: Boolean = false,
    val enableDirectDnsForProxyServerDomains: Boolean = false,
    val enableVpnAppendHttpProxy: Boolean = false,
    val enableVpnHevTun: Boolean = false,
    val tunMtu: String = VpnDefaults.MTU.toString(),
    val tunVpnDns: String = VpnDefaults.IPV4_DNS,
    val tunIpv4Cidr: String = VpnDefaults.IPV4_CIDR,
    val tunIpv6Cidr: String = VpnDefaults.IPV6_CIDR,
    val proxyDns: List<String> = VpnDefaults.PROXY_DNS_SERVERS,
    val directDns: List<String> = VpnDefaults.DIRECT_DNS_SERVERS,
    val directDnsDomains: List<String> = emptyList(),
    val dnsHosts: List<String> = emptyList(),
)

@Serializable
data class TrafficConfigNetworkActivation(
    val enabled: Boolean = false,
    val transport: Int = TrafficConfigNetworkTransportWifi,
)

const val TrafficConfigNetworkTransportWifi = 0
const val TrafficConfigNetworkTransportCellular = 1

/**
 * The data files used by this profile's routing rules.
 *
 * Files are cached application-wide because Xray loads them from one data
 * directory, while their source, custom files and update user agent belong to
 * the selected traffic profile.  Nothing is copied from legacy global
 * settings: a new SKIPI profile always starts with these explicit defaults.
 */
@Serializable
data class TrafficConfigResourceSettings(
    val source: Int = ResourceFileSourceLoyalsoldierGithub,
    val customGeoIpUrl: String = ResourceFileLoyalsoldierGeoIpUrl,
    val customGeoSiteUrl: String = ResourceFileLoyalsoldierGeoSiteUrl,
    val customGeoIpOnlyCnPrivateUrl: String = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
    val customDirectCidrIpv4Url: String = ResourceFileDirectCidrIpv4Url,
    val customDirectCidrIpv6Url: String = ResourceFileDirectCidrIpv6Url,
    val customFiles: List<CustomResourceFileState> = emptyList(),
    val nextCustomFileId: Int = 1,
    val userAgent: String = DefaultSubscriptionUserAgent,
    val autoUpdate: Boolean = true,
    val updateInterval: String = "24",
)

/** A user-visible traffic profile. [rawConfig] is the complete portable profile document. */
data class TrafficConfigState(
    val id: Int,
    val name: String,
    val rawConfig: String,
    val sourceUrl: String = "",
    val updateLocked: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val autoUpdate: Boolean = false,
    val updateInterval: String = "",
    val proxyAppListMode: Int = ProxyAppListModeGlobal,
    val proxyAppListSelectedApps: List<String> = emptyList(),
    val androidSettings: TrafficConfigAndroidSettings = TrafficConfigAndroidSettings(),
    val networkActivation: TrafficConfigNetworkActivation = TrafficConfigNetworkActivation(),
    val resourceSettings: TrafficConfigResourceSettings = TrafficConfigResourceSettings(),
)

val DefaultTrafficConfigs = listOf(
    TrafficConfigState(
        id = 1,
        name = "Default",
        rawConfig = defaultShadowrocketConfig(),
    ).withSkipiSettingsInRawConfig(),
)

internal fun defaultSkipiTrafficConfigRaw(name: String = ""): String {
    return TrafficConfigState(
        id = 0,
        name = name,
        rawConfig = defaultShadowrocketConfig(),
    ).withSkipiSettingsInRawConfig().rawConfig
}

private const val SkipiPerAppModeComment = "# SKIPI-PER-APP-MODE:"
private const val SkipiPerAppItemComment = "# SKIPI-PER-APP:"
private const val SkipiSection = "SKIPI"
private const val SkipiProfileName = "profile-name"
private const val SkipiProfileUpdateUrl = "profile-update-url"
private const val SkipiProfileUpdateLocked = "profile-update-locked"
private const val SkipiProfileAutoUpdate = "profile-auto-update"
private const val SkipiProfileUpdateInterval = "profile-update-interval"
private const val SkipiPerAppMode = "per-app-mode"
private const val SkipiPerAppPackage = "per-app-package"
private const val SkipiSniffing = "sniffing"
private const val SkipiSniffingRouteOnly = "sniffing-route-only"
private const val SkipiMux = "mux"
private const val SkipiMuxConcurrency = "mux-concurrency"
private const val SkipiMuxXudpConcurrency = "mux-xudp-concurrency"
private const val SkipiMuxUdp443 = "mux-udp-443"
private const val SkipiFragment = "fragment"
private const val SkipiFragmentPackets = "fragment-packets"
private const val SkipiFragmentLength = "fragment-length"
private const val SkipiFragmentInterval = "fragment-interval"
private const val SkipiVpnLocalDns = "vpn-local-dns"
private const val SkipiFakeDns = "fake-dns"
private const val SkipiDirectDnsForProxyServerDomains = "direct-dns-fallback-proxy"
private const val SkipiProxyDns = "proxy-dns"
private const val SkipiDirectDns = "direct-dns"
private const val SkipiDirectDnsDomains = "direct-dns-domains"
private const val SkipiDnsHosts = "dns-hosts"
private const val SkipiVpnAppendHttpProxy = "vpn-append-http-proxy"
private const val SkipiVpnHevTun = "vpn-hev-tun"
private const val SkipiTunMtu = "tun-mtu"
private const val SkipiTunDns = "tun-dns"
private const val SkipiTunIpv4 = "tun-ipv4-cidr"
private const val SkipiTunIpv6 = "tun-ipv6-cidr"
private const val SkipiNetworkActivation = "network-activation"
private const val SkipiNetworkTransport = "network-transport"
private const val SkipiResourceSource = "resource-source"
private const val SkipiResourceGeoIpUrl = "resource-geoip-url"
private const val SkipiResourceGeoSiteUrl = "resource-geosite-url"
private const val SkipiResourceGeoIpOnlyCnPrivateUrl = "resource-geoip-cn-private-url"
private const val SkipiResourceDirectCidrIpv4Url = "resource-direct-cidr-ipv4-url"
private const val SkipiResourceDirectCidrIpv6Url = "resource-direct-cidr-ipv6-url"
private const val SkipiResourceUserAgent = "resource-user-agent"
private const val SkipiResourceAutoUpdate = "resource-auto-update"
private const val SkipiResourceUpdateInterval = "resource-update-interval"
private const val SkipiResourceCustomFile = "resource-custom-file"

internal data class SkipiPerAppSettings(
    val mode: Int,
    val selectedApps: List<String>,
)

/**
 * Compatibility writer retained for callers that update only Per-App data.
 * New profiles use normal `key = value` entries in the [SKIPI] section rather
 * than comments, so exported files remain editable raw configuration files.
 */
internal fun String.withSkipiPerAppSettings(
    mode: Int,
    selectedApps: List<String>,
): String {
    val modeValue = when (mode) {
        0 -> "blacklist"
        1 -> "whitelist"
        else -> "global"
    }
    val values = skipiValues().toMutableMap()
    values[SkipiPerAppMode] = listOf(modeValue)
    values[SkipiPerAppPackage] = selectedApps.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    return withoutLegacySkipiPerAppComments().withShadowrocketSectionLines(
        SkipiSection,
        values.toSectionLines(),
    )
}

internal fun String.parseSkipiPerAppSettings(): SkipiPerAppSettings {
    val values = skipiValues()
    values[SkipiPerAppMode]?.lastOrNull()?.let { modeValue ->
        val mode = when (modeValue.trim().lowercase()) {
            "blacklist" -> 0
            "whitelist" -> 1
            else -> ProxyAppListModeGlobal
        }
        return SkipiPerAppSettings(
            mode = mode,
            selectedApps = values[SkipiPerAppPackage].orEmpty()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
        )
    }

    // Read the short-lived comment format once so development profiles created
    // before the [SKIPI] section are converted on their next save.
    var mode = ProxyAppListModeGlobal
    val selectedApps = mutableListOf<String>()
    lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith(SkipiPerAppModeComment, ignoreCase = true) -> {
                mode = when (trimmed.substringAfter(':').trim().lowercase()) {
                    "blacklist" -> 0
                    "whitelist" -> 1
                    else -> ProxyAppListModeGlobal
                }
            }

            trimmed.startsWith(SkipiPerAppItemComment, ignoreCase = true) -> {
                trimmed.substringAfter(':').trim().takeIf(String::isNotBlank)?.let(selectedApps::add)
            }
        }
    }
    return SkipiPerAppSettings(mode = mode, selectedApps = selectedApps.distinct())
}

/** Rewrites every SKIPI-specific setting as normal INI values in `[SKIPI]`. */
internal fun TrafficConfigState.withSkipiSettingsInRawConfig(): TrafficConfigState {
    return copy(
        rawConfig = rawConfig
            .withoutLegacySkipiPerAppComments()
            .withShadowrocketSectionLines(SkipiSection, skipiSettingsSectionLines()),
    )
}

/** Reads the complete SKIPI section from raw text while preserving old-profile fallbacks. */
internal fun TrafficConfigState.withSkipiSettingsReadFromRawConfig(): TrafficConfigState {
    val values = rawConfig.skipiValues()
    if (values.isEmpty()) {
        val legacyPerApp = rawConfig.parseSkipiPerAppSettings()
        return copy(
            proxyAppListMode = legacyPerApp.mode,
            proxyAppListSelectedApps = legacyPerApp.selectedApps,
        )
    }
    fun value(key: String, fallback: String): String = values[key]?.lastOrNull() ?: fallback
    fun bool(key: String, fallback: Boolean): Boolean = value(key, fallback.toString()).toConfigBoolean(fallback)
    fun int(key: String, fallback: Int): Int = value(key, fallback.toString()).toIntOrNull() ?: fallback
    val mode = when (value(SkipiPerAppMode, "global").trim().lowercase()) {
        "blacklist" -> 0
        "whitelist" -> 1
        else -> ProxyAppListModeGlobal
    }
    val parsedCustomFiles = values[SkipiResourceCustomFile].orEmpty()
        .mapNotNull(::parseSkipiResourceCustomFile)
        .distinctBy(CustomResourceFileState::id)
    val android = androidSettings
    val resources = resourceSettings
        val parsedProxyDns = values[SkipiProxyDns]?.flatMap { it.split(',') }
            ?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
            ?: android.proxyDns
        val parsedDirectDns = values[SkipiDirectDns]?.flatMap { it.split(',') }
            ?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
            ?: android.directDns
        val parsedDirectDnsDomains = values[SkipiDirectDnsDomains]?.flatMap { it.split(',') }
            ?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
            ?: android.directDnsDomains
        val parsedDnsHosts = values[SkipiDnsHosts]?.map(String::trim)
            ?.filter(String::isNotEmpty)?.distinct()
            ?: android.dnsHosts

        return copy(
            name = value(SkipiProfileName, name).trim().ifBlank { name },
            sourceUrl = value(SkipiProfileUpdateUrl, sourceUrl).trim(),
            updateLocked = bool(SkipiProfileUpdateLocked, updateLocked),
            autoUpdate = bool(SkipiProfileAutoUpdate, autoUpdate),
            updateInterval = value(SkipiProfileUpdateInterval, updateInterval).trim(),
            proxyAppListMode = mode,
            proxyAppListSelectedApps = values[SkipiPerAppPackage].orEmpty()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
            androidSettings = android.copy(
                enableSniffing = bool(SkipiSniffing, android.enableSniffing),
                enableSniffingRouteOnly = bool(SkipiSniffingRouteOnly, android.enableSniffingRouteOnly),
                enableMux = bool(SkipiMux, android.enableMux),
                muxConcurrency = value(SkipiMuxConcurrency, android.muxConcurrency),
                muxXudpConcurrency = value(SkipiMuxXudpConcurrency, android.muxXudpConcurrency),
                muxXudpProxyUdp443 = int(SkipiMuxUdp443, android.muxXudpProxyUdp443),
                enableFragment = bool(SkipiFragment, android.enableFragment),
                fragmentPackets = value(SkipiFragmentPackets, android.fragmentPackets),
                fragmentLength = value(SkipiFragmentLength, android.fragmentLength),
                fragmentInterval = value(SkipiFragmentInterval, android.fragmentInterval),
                enableVpnLocalDns = bool(SkipiVpnLocalDns, android.enableVpnLocalDns),
                enableFakeDns = bool(SkipiFakeDns, android.enableFakeDns),
                enableDirectDnsForProxyServerDomains = bool(SkipiDirectDnsForProxyServerDomains, android.enableDirectDnsForProxyServerDomains),
                enableVpnAppendHttpProxy = bool(SkipiVpnAppendHttpProxy, android.enableVpnAppendHttpProxy),
                enableVpnHevTun = bool(SkipiVpnHevTun, android.enableVpnHevTun),
                tunMtu = value(SkipiTunMtu, android.tunMtu),
                tunVpnDns = value(SkipiTunDns, android.tunVpnDns),
                tunIpv4Cidr = value(SkipiTunIpv4, android.tunIpv4Cidr),
                tunIpv6Cidr = value(SkipiTunIpv6, android.tunIpv6Cidr),
                proxyDns = parsedProxyDns,
                directDns = parsedDirectDns,
                directDnsDomains = parsedDirectDnsDomains,
                dnsHosts = parsedDnsHosts,
            ),
            networkActivation = TrafficConfigNetworkActivation(
                enabled = bool(SkipiNetworkActivation, networkActivation.enabled),
                transport = when (value(SkipiNetworkTransport, networkActivation.transport.toString()).lowercase()) {
                    "cellular", "mobile", TrafficConfigNetworkTransportCellular.toString() -> TrafficConfigNetworkTransportCellular
                    else -> TrafficConfigNetworkTransportWifi
                },
            ),
            resourceSettings = resources.copy(
                source = int(SkipiResourceSource, resources.source),
                customGeoIpUrl = value(SkipiResourceGeoIpUrl, resources.customGeoIpUrl),
                customGeoSiteUrl = value(SkipiResourceGeoSiteUrl, resources.customGeoSiteUrl),
                customGeoIpOnlyCnPrivateUrl = value(SkipiResourceGeoIpOnlyCnPrivateUrl, resources.customGeoIpOnlyCnPrivateUrl),
                customDirectCidrIpv4Url = value(SkipiResourceDirectCidrIpv4Url, resources.customDirectCidrIpv4Url),
                customDirectCidrIpv6Url = value(SkipiResourceDirectCidrIpv6Url, resources.customDirectCidrIpv6Url),
                customFiles = parsedCustomFiles,
                nextCustomFileId = (parsedCustomFiles.maxOfOrNull(CustomResourceFileState::id) ?: 0) + 1,
                userAgent = value(SkipiResourceUserAgent, resources.userAgent),
                autoUpdate = bool(SkipiResourceAutoUpdate, resources.autoUpdate),
                updateInterval = value(SkipiResourceUpdateInterval, resources.updateInterval).trim(),
            ),
        )
    }

private fun TrafficConfigState.skipiSettingsSectionLines(): List<String> {
    val android = androidSettings
    val resources = resourceSettings
    val mode = when (proxyAppListMode) {
        0 -> "blacklist"
        1 -> "whitelist"
        else -> "global"
    }
    return buildList {
        add("$SkipiProfileName = ${name.trim()}")
        add("$SkipiProfileUpdateUrl = ${sourceUrl.trim()}")
        add("$SkipiProfileUpdateLocked = $updateLocked")
        add("$SkipiProfileAutoUpdate = $autoUpdate")
        if (updateInterval.isNotBlank()) {
            add("$SkipiProfileUpdateInterval = ${updateInterval.trim()}")
        }
        add("$SkipiPerAppMode = $mode")
        proxyAppListSelectedApps.asSequence().map(String::trim).filter(String::isNotBlank).distinct().forEach { appId ->
            add("$SkipiPerAppPackage = $appId")
        }
        add("$SkipiSniffing = ${android.enableSniffing}")
        add("$SkipiSniffingRouteOnly = ${android.enableSniffingRouteOnly}")
        add("$SkipiMux = ${android.enableMux}")
        add("$SkipiMuxConcurrency = ${android.muxConcurrency}")
        add("$SkipiMuxXudpConcurrency = ${android.muxXudpConcurrency}")
        add("$SkipiMuxUdp443 = ${android.muxXudpProxyUdp443}")
        add("$SkipiFragment = ${android.enableFragment}")
        add("$SkipiFragmentPackets = ${android.fragmentPackets}")
        add("$SkipiFragmentLength = ${android.fragmentLength}")
        add("$SkipiFragmentInterval = ${android.fragmentInterval}")
        add("$SkipiVpnLocalDns = ${android.enableVpnLocalDns}")
        add("$SkipiFakeDns = ${android.enableFakeDns}")
        add("$SkipiDirectDnsForProxyServerDomains = ${android.enableDirectDnsForProxyServerDomains}")
        add("$SkipiVpnAppendHttpProxy = ${android.enableVpnAppendHttpProxy}")
        add("$SkipiVpnHevTun = ${android.enableVpnHevTun}")
        add("$SkipiTunMtu = ${android.tunMtu}")
        add("$SkipiTunDns = ${android.tunVpnDns}")
        add("$SkipiTunIpv4 = ${android.tunIpv4Cidr}")
        add("$SkipiTunIpv6 = ${android.tunIpv6Cidr}")
        if (android.proxyDns.isNotEmpty()) {
            add("$SkipiProxyDns = ${android.proxyDns.joinToString(",")}")
        }
        if (android.directDns.isNotEmpty()) {
            add("$SkipiDirectDns = ${android.directDns.joinToString(",")}")
        }
        if (android.directDnsDomains.isNotEmpty()) {
            add("$SkipiDirectDnsDomains = ${android.directDnsDomains.joinToString(",")}")
        }
        android.dnsHosts.forEach { host ->
            add("$SkipiDnsHosts = $host")
        }
        add("$SkipiNetworkActivation = ${networkActivation.enabled}")
        add("$SkipiNetworkTransport = ${if (networkActivation.transport == TrafficConfigNetworkTransportCellular) "cellular" else "wifi"}")
        add("$SkipiResourceSource = ${resources.source}")
        add("$SkipiResourceGeoIpUrl = ${resources.customGeoIpUrl}")
        add("$SkipiResourceGeoSiteUrl = ${resources.customGeoSiteUrl}")
        add("$SkipiResourceGeoIpOnlyCnPrivateUrl = ${resources.customGeoIpOnlyCnPrivateUrl}")
        add("$SkipiResourceDirectCidrIpv4Url = ${resources.customDirectCidrIpv4Url}")
        add("$SkipiResourceDirectCidrIpv6Url = ${resources.customDirectCidrIpv6Url}")
        add("$SkipiResourceUserAgent = ${resources.userAgent}")
        add("$SkipiResourceAutoUpdate = ${resources.autoUpdate}")
        if (resources.updateInterval.isNotBlank()) {
            add("$SkipiResourceUpdateInterval = ${resources.updateInterval.trim()}")
        }
        resources.customFiles.forEach { file -> add(file.toSkipiResourceCustomFileLine()) }
    }
}

private fun String.skipiValues(): Map<String, List<String>> {
    return analyzeShadowrocketConfig().sections[SkipiSection.lowercase()].orEmpty()
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
}

private fun Map<String, List<String>>.toSectionLines(): List<String> {
    return flatMap { (key, values) -> values.map { value -> "$key = $value" } }
}

private fun String.withoutLegacySkipiPerAppComments(): String {
    return lineSequence()
        .filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith(SkipiPerAppModeComment, ignoreCase = true) ||
                trimmed.startsWith(SkipiPerAppItemComment, ignoreCase = true)
        }
        .joinToString("\n")
        .trimEnd()
}

private fun String.toConfigBoolean(fallback: Boolean): Boolean {
    return when (trim().lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> fallback
    }
}

private fun CustomResourceFileState.toSkipiResourceCustomFileLine(): String {
    return "$SkipiResourceCustomFile = $id,${Uri.encode(name)},${Uri.encode(url)}"
}

private fun parseSkipiResourceCustomFile(value: String): CustomResourceFileState? {
    val parts = value.split(',', limit = 3)
    val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val name = parts.getOrNull(1)?.let(Uri::decode)?.trim().orEmpty()
    val url = parts.getOrNull(2)?.let(Uri::decode)?.trim().orEmpty()
    return CustomResourceFileState(id = id, name = name, url = url)
        .takeIf { it.id > 0 && it.name.isNotEmpty() && it.url.isNotEmpty() }
}

internal fun AppState.withUpdatedTrafficConfig(
    configId: Int,
    transform: (TrafficConfigState) -> TrafficConfigState,
): AppState {
    return copy(
        trafficConfigs = trafficConfigs.map { config ->
            if (config.id == configId) {
                val updated = transform(config)
                val parsed = if (updated.rawConfig != config.rawConfig) {
                    updated.withSkipiSettingsReadFromRawConfig()
                } else {
                    updated
                }
                parsed.withSkipiSettingsInRawConfig()
            } else {
                config
            }
        },
    ).withConfigProxyGroupsReflected()
}

/**
 * Materializes only the `[Proxy Group]` entries explicitly marked for home
 * display. They keep a source reference, so their member set is resolved from
 * the config on every connection instead of becoming stale after a refresh.
 */
internal fun AppState.withConfigProxyGroupsReflected(): AppState {
    val desired = trafficConfigs.flatMap { config ->
        val isConfigActive = config.id == activeTrafficConfigId
        config.rawConfig.analyzeShadowrocketConfig().proxyGroups
            .filter { group ->
                when (group.displayMode) {
                    features.proxy.server.model.StrategyGroupDisplayMode.ALWAYS -> true
                    features.proxy.server.model.StrategyGroupDisplayMode.NEVER -> false
                    features.proxy.server.model.StrategyGroupDisplayMode.ACTIVE_CONFIG -> isConfigActive
                    else -> isConfigActive
                }
            }
            .map { group -> ConfigAutoBalancerSource(config.id, group) }
    }
    val existingGenerated = proxyServers.filter { server ->
        (server.server as? StrategyGroup)?.sourceTrafficConfigId != null
    }
    val regularServers = proxyServers.filterNot(existingGenerated::contains)
    var nextId = nextProxyServerId
    val generatedServers = desired.map { source ->
        val existing = existingGenerated.firstOrNull { server ->
            val strategy = server.server as StrategyGroup
            strategy.sourceTrafficConfigId == source.configId &&
                strategy.sourcePolicyGroupName.equals(source.group.name, ignoreCase = true)
        }
        val id = existing?.id ?: nextId++
        val existingStrategy = existing?.server as? StrategyGroup
        val resolvedMemberIds = source.group.members.flatMap { rawMember ->
            val cleanMember = rawMember.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            regularServers.filter { candidate ->
                val remarks = candidate.server.getInfo().remarks.trim()
                val cleanRemarks = CountryFlagUtils.stripLeadingCountryFlag(remarks).trim()
                remarks.equals(cleanMember, ignoreCase = true) ||
                    cleanRemarks.equals(cleanMember, ignoreCase = true) ||
                    CountryFlagUtils.stripLeadingCountryFlag(cleanMember).trim().equals(cleanRemarks, ignoreCase = true)
            }.map { it.id }
        }.distinct()
        val effectiveMemberIds = if (existingStrategy?.proxyServerIds?.isNotEmpty() == true) {
            existingStrategy.proxyServerIds
        } else {
            resolvedMemberIds
        }
        val effectiveSelectedMemberId = existingStrategy?.selectedMemberId
            ?.takeIf { mid -> mid in effectiveMemberIds || effectiveMemberIds.isEmpty() }
            ?: effectiveMemberIds.firstOrNull()

        ProxyServerState(
            id = id,
            groupId = AutoBalancerGroupId,
            latency = existing?.latency.orEmpty(),
            server = StrategyGroup(
                remarks = source.group.name,
                strategy = source.group.toStrategyGroupType(),
                proxyServerIds = effectiveMemberIds,
                selectedMemberId = effectiveSelectedMemberId,
                displayMode = source.group.displayMode,
                showInAutoBalancerList = source.group.displayMode != features.proxy.server.model.StrategyGroupDisplayMode.NEVER,
                sourceTrafficConfigId = source.configId,
                sourcePolicyGroupName = source.group.name,
                probeInterval = source.group.intervalSeconds?.let { "${it}s" } ?: "15s",
                probeUrl = source.group.url,
            ),
        )
    }
    val resolvedServers = regularServers + generatedServers
    val resolvedSelectedId = selectedProxyServerId.takeIf { selectedId ->
        resolvedServers.any { server -> server.id == selectedId }
    } ?: resolvedServers.firstOrNull()?.id ?: selectedProxyServerId
    return copy(
        proxyServers = resolvedServers,
        nextProxyServerId = maxOf(nextProxyServerId, nextId),
        selectedProxyServerId = resolvedSelectedId,
    )
}

private data class ConfigAutoBalancerSource(
    val configId: Int,
    val group: ShadowrocketPolicyGroup,
)

private fun ShadowrocketPolicyGroup.toStrategyGroupType(): String {
    return when (type.lowercase()) {
        "select" -> StrategyGroupConstants.TYPE_SELECT
        "load-balance", "random" -> StrategyGroupConstants.TYPE_RANDOM
        "round-robin" -> StrategyGroupConstants.TYPE_ROUND_ROBIN
        "least-load" -> StrategyGroupConstants.TYPE_LEAST_LOAD
        "fallback" -> StrategyGroupConstants.TYPE_FALLBACK
        "url-test", "leastping" -> StrategyGroupConstants.TYPE_LEAST_PING
        else -> StrategyGroupConstants.TYPE_SELECT
    }
}
