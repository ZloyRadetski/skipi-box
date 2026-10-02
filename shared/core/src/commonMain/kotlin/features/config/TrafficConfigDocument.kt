// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.toSupportedXrayDnsServers
import features.resources.ResourceFileDirectCidrIpv4Url
import features.resources.ResourceFileDirectCidrIpv6Url
import features.resources.ResourceFileLoyalsoldierGeoIpUrl
import features.resources.ResourceFileLoyalsoldierGeoSiteUrl
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileV2FlyGeoIpOnlyCnPrivateUrl
import kotlinx.serialization.Serializable

/** Portable identity for a user supplied resource file. */
@Serializable
data class TrafficConfigCustomResourceFile(
    val id: Int,
    val name: String,
    val url: String,
)

/** Resource update settings stored with one portable traffic profile. */
@Serializable
data class TrafficConfigResourceSettings(
    val source: Int = ResourceFileSourceLoyalsoldierGithub,
    val customGeoIpUrl: String = ResourceFileLoyalsoldierGeoIpUrl,
    val customGeoSiteUrl: String = ResourceFileLoyalsoldierGeoSiteUrl,
    val customGeoIpOnlyCnPrivateUrl: String = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
    val customDirectCidrIpv4Url: String = ResourceFileDirectCidrIpv4Url,
    val customDirectCidrIpv6Url: String = ResourceFileDirectCidrIpv6Url,
    val customFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    val nextCustomFileId: Int = 1,
    val userAgent: String = "",
    val autoUpdate: Boolean = true,
    val updateInterval: String = "24",
)

/** Complete profile state that can be represented in the portable profile document. */
@Serializable
data class TrafficConfigState(
    val id: Int,
    val name: String,
    val rawConfig: String,
    val sourceUrl: String = "",
    val updateLocked: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val autoUpdate: Boolean = false,
    val updateInterval: String = "",
    val proxyAppListMode: Int = SkipiPerAppModeGlobal,
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

fun defaultSkipiTrafficConfigRaw(name: String = ""): String =
    TrafficConfigState(
        id = 0,
        name = name,
        rawConfig = defaultShadowrocketConfig(),
    ).withSkipiSettingsInRawConfig().rawConfig

/** Writes current settings as ordinary `[SKIPI]` key/value lines. */
fun TrafficConfigState.withSkipiSettingsInRawConfig(): TrafficConfigState = copy(
    rawConfig = rawConfig
        .withoutLegacySkipiPerAppComments()
        .withShadowrocketSectionLines(SkipiSection, skipiSettingsSectionLines()),
)

/** Reads `[SKIPI]` settings and applies the historical fallbacks for legacy profiles. */
fun TrafficConfigState.withSkipiSettingsReadFromRawConfig(): TrafficConfigState {
    val analysis = rawConfig.analyzeShadowrocketConfig()
    val hasGeneralDns = analysis.general["dns-server"]
        ?.split(',')
        ?.map(String::trim)
        ?.any { server -> server.isNotEmpty() && !server.equals("system", ignoreCase = true) }
        ?: false
    val hasShadowrocketHosts = analysis.sections["host"].orEmpty().any { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith('#') && !trimmed.startsWith(';')
    }
    val values = rawConfig.skipiSectionValues()
    if (values.isEmpty()) {
        val legacyPerApp = rawConfig.parseSkipiPerAppSettings()
        return copy(
            proxyAppListMode = legacyPerApp.mode,
            proxyAppListSelectedApps = legacyPerApp.selectedApps,
            androidSettings = androidSettings.copy(
                proxyDns = (if (hasGeneralDns) emptyList() else androidSettings.proxyDns).toSupportedXrayDnsServers(),
                directDns = (if (hasGeneralDns) emptyList() else androidSettings.directDns).toSupportedXrayDnsServers(),
                dnsHosts = if (hasShadowrocketHosts) emptyList() else androidSettings.dnsHosts,
            ),
        )
    }
    fun value(key: String, fallback: String): String = values[key]?.lastOrNull() ?: fallback
    fun bool(key: String, fallback: Boolean): Boolean = value(key, fallback.toString()).toSkipiConfigBoolean(fallback)
    fun int(key: String, fallback: Int): Int = value(key, fallback.toString()).toIntOrNull() ?: fallback
    val mode = when (value(SkipiPerAppMode, "global").trim().lowercase()) {
        "blacklist" -> SkipiPerAppModeBlacklist
        "whitelist" -> SkipiPerAppModeWhitelist
        else -> SkipiPerAppModeGlobal
    }
    val parsedCustomFiles = values[SkipiResourceCustomFile].orEmpty()
        .mapNotNull(::parseSkipiResourceCustomFile)
        .distinctBy(TrafficConfigCustomResourceFile::id)
    val android = androidSettings
    val resources = resourceSettings
    val parsedProxyDns = values[SkipiProxyDns]?.flatMap { it.split(',') }
        ?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
        ?: if (hasGeneralDns) emptyList() else android.proxyDns
    val parsedDirectDns = values[SkipiDirectDns]?.flatMap { it.split(',') }
        ?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
        ?: if (hasGeneralDns) emptyList() else android.directDns
    val parsedDirectDnsDomains = values[SkipiDirectDnsDomains]?.flatMap { it.split(',') }
        ?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
        ?: android.directDnsDomains
    val parsedDnsHosts = values[SkipiDnsHosts]?.map(String::trim)
        ?.filter(String::isNotEmpty)?.distinct()
        ?: if (hasShadowrocketHosts) emptyList() else android.dnsHosts
    val parsedRouteDomainStrategy = value(SkipiRouteDomainStrategy, "").trim().lowercase()
        .takeIf(String::isNotEmpty)
        ?.let { strategy ->
            when (strategy) {
                "asis", "as-is", "0" -> 0
                "ipondemand", "ip-on-demand", "2" -> 2
                "ipifnonmatch", "ip-if-non-match", "1" -> 1
                else -> null
            }
        }
        ?: android.routeDomainStrategy
    val parsedFakeDnsPoolSize = int(SkipiFakeDnsPoolSize, android.fakeDnsPoolSize)
        .takeIf { it > 0 }
        ?: android.fakeDnsPoolSize

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
            enableResolveProxyServerDomain = bool(SkipiResolveProxyServerDomain, android.enableResolveProxyServerDomain),
            enableDirectDnsForProxyServerDomains = bool(SkipiDirectDnsForProxyServerDomains, android.enableDirectDnsForProxyServerDomains),
            tunVpnDns = value(SkipiTunDns, android.tunVpnDns),
            proxyDns = parsedProxyDns.toSupportedXrayDnsServers(),
            directDns = parsedDirectDns.toSupportedXrayDnsServers(),
            directDnsDomains = parsedDirectDnsDomains,
            dnsHosts = parsedDnsHosts,
            routeDomainStrategy = parsedRouteDomainStrategy,
            fakeDnsIpPool = value(SkipiFakeDnsIpPool, android.fakeDnsIpPool).trim(),
            fakeDnsPoolSize = parsedFakeDnsPoolSize,
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
            nextCustomFileId = (parsedCustomFiles.maxOfOrNull(TrafficConfigCustomResourceFile::id) ?: 0) + 1,
            userAgent = value(SkipiResourceUserAgent, resources.userAgent),
            autoUpdate = bool(SkipiResourceAutoUpdate, resources.autoUpdate),
            updateInterval = value(SkipiResourceUpdateInterval, resources.updateInterval).trim(),
        ),
    )
}

private fun TrafficConfigState.skipiSettingsSectionLines(): List<String> {
    val android = androidSettings
    val proxyDns = android.proxyDns.toSupportedXrayDnsServers()
    val directDns = android.directDns.toSupportedXrayDnsServers()
    val resources = resourceSettings
    val mode = when (proxyAppListMode) {
        SkipiPerAppModeBlacklist -> "blacklist"
        SkipiPerAppModeWhitelist -> "whitelist"
        else -> "global"
    }
    return buildList {
        add("$SkipiProfileName = ${name.trim()}")
        add("$SkipiProfileUpdateUrl = ${sourceUrl.trim()}")
        add("$SkipiProfileUpdateLocked = $updateLocked")
        add("$SkipiProfileAutoUpdate = $autoUpdate")
        if (updateInterval.isNotBlank()) add("$SkipiProfileUpdateInterval = ${updateInterval.trim()}")
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
        if (android.enableFakeDns) {
            if (android.fakeDnsIpPool.isNotBlank()) add("$SkipiFakeDnsIpPool = ${android.fakeDnsIpPool}")
            add("$SkipiFakeDnsPoolSize = ${android.fakeDnsPoolSize}")
        }
        add("$SkipiResolveProxyServerDomain = ${android.enableResolveProxyServerDomain}")
        add("$SkipiDirectDnsForProxyServerDomains = ${android.enableDirectDnsForProxyServerDomains}")
        add("$SkipiTunDns = ${android.tunVpnDns}")
        if (proxyDns.isNotEmpty()) add("$SkipiProxyDns = ${proxyDns.joinToString(",")}")
        if (directDns.isNotEmpty()) add("$SkipiDirectDns = ${directDns.joinToString(",")}")
        if (android.directDnsDomains.isNotEmpty()) add("$SkipiDirectDnsDomains = ${android.directDnsDomains.joinToString(",")}")
        android.dnsHosts.forEach { host -> add("$SkipiDnsHosts = $host") }
        add("$SkipiRouteDomainStrategy = ${android.routeDomainStrategy.toRouteDomainStrategyValue()}")
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
        if (resources.updateInterval.isNotBlank()) add("$SkipiResourceUpdateInterval = ${resources.updateInterval.trim()}")
        resources.customFiles.forEach { file -> add("$SkipiResourceCustomFile = ${file.id},${file.name.encodeProfileUriComponent()},${file.url.encodeProfileUriComponent()}") }
    }
}

/** Xray routing domain strategy: 0 = AsIs, 1 = IPIfNonMatch, 2 = IPOnDemand. */
fun Int.toRouteDomainStrategyValue(): String = when (this) {
    0 -> "AsIs"
    2 -> "IPOnDemand"
    else -> "IPIfNonMatch"
}

private fun parseSkipiResourceCustomFile(value: String): TrafficConfigCustomResourceFile? {
    val parts = value.split(',', limit = 3)
    val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val name = parts.getOrNull(1)?.decodeProfileUriComponent()?.trim().orEmpty()
    val url = parts.getOrNull(2)?.decodeProfileUriComponent()?.trim().orEmpty()
    return TrafficConfigCustomResourceFile(id = id, name = name, url = url)
        .takeIf { it.id > 0 && it.name.isNotEmpty() && it.url.isNotEmpty() }
}

/* Android Uri.encode's default safe set, retained byte-for-byte for old profile documents. */
private fun String.encodeProfileUriComponent(): String = buildString {
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xff
        val char = value.toChar()
        if (char.isLetterOrDigit() && value < 128 || char in "_-!.~'()*") {
            append(char)
        } else {
            append('%')
            append("0123456789ABCDEF"[value ushr 4])
            append("0123456789ABCDEF"[value and 0x0f])
        }
    }
}

private fun String.decodeProfileUriComponent(): String = buildString {
    var index = 0
    while (index < length) {
        if (this@decodeProfileUriComponent[index] == '%' && index + 2 < length &&
            this@decodeProfileUriComponent[index + 1].digitToIntOrNull(16) != null &&
            this@decodeProfileUriComponent[index + 2].digitToIntOrNull(16) != null
        ) {
            val bytes = mutableListOf<Byte>()
            while (index + 2 < length && this@decodeProfileUriComponent[index] == '%') {
                val high = this@decodeProfileUriComponent[index + 1].digitToIntOrNull(16) ?: break
                val low = this@decodeProfileUriComponent[index + 2].digitToIntOrNull(16) ?: break
                bytes += ((high shl 4) or low).toByte()
                index += 3
            }
            append(bytes.toByteArray().decodeToString())
        } else {
            append(this@decodeProfileUriComponent[index])
            index++
        }
    }
}
