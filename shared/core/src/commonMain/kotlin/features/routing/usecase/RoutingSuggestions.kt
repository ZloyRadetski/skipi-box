// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigState
import features.config.sanitizeCustomResourceFileName
import features.resources.ResourceFileGeoIpName
import features.resources.ResourceFileGeoSiteName

data class GeoSuggestionItem(
    val tag: String,
    val fullRule: String,
    val groupName: String,
    val isCustom: Boolean = false,
)

/** Portable catalogs used by the Android routing forms and shared rule editors. */
object RoutingSuggestionPresets {
    val NetworkOptions = listOf("tcp", "udp", "tcp,udp")
    val NetworkOptionsWithAny = listOf("", "tcp", "udp", "tcp,udp")

    val ProtocolOptions = listOf(
        "http",
        "tls",
        "bittorrent",
        "quic",
        "stun",
        "dns",
        "dtls",
        "ssh",
        "wireguard",
    )

    val PortPresets = listOf(
        "80",
        "443",
        "80,443",
        "53",
        "22",
        "8080",
        "8443",
        "1-65535",
    )

    val DomainPrefixes = listOf("geosite:", "domain:", "full:", "keyword:", "regexp:")

    val PrivateIpPresets = listOf(
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "127.0.0.0/8",
        "::1/128",
        "fc00::/7",
        "fe80::/10",
    )

    val ShadowrocketUserAgentPresets = listOf(
        "*Telegram*",
        "*Twitter*",
        "*Instagram*",
        "*WhatsApp*",
        "*Apple*",
        "*MicroMessenger*",
        "*YouTube*",
        "*Google*",
        "*Mozilla*",
        "*Netflix*",
        "*Spotify*",
    )

    val ShadowrocketNetworkOptions = listOf("tcp", "udp", "tcp,udp", "udp,tcp")

    val RuleSetPresets = listOf(
        "geosite:google",
        "geosite:cn",
        "geosite:geolocation-!cn",
        "geosite:category-ads-all",
        "geosite:telegram",
        "geosite:youtube",
        "geosite:netflix",
        "geosite:openai",
        "geosite:github",
        "geosite:twitter",
        "geoip:cn",
        "geoip:telegram",
        "geoip:private",
        "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/gfw.txt",
        "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/direct.txt",
        "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/reject.txt",
        "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/proxy.txt",
    )

    val DomainSetPresets = listOf(
        "geosite:google",
        "geosite:cn",
        "geosite:geolocation-!cn",
        "geosite:category-ads-all",
        "geosite:telegram",
        "geosite:youtube",
        "geosite:netflix",
        "geosite:openai",
        "geosite:github",
        "geosite:twitter",
        "geosite:apple",
        "geosite:facebook",
    )
}

/** Explicit profile IDs do not fall back; omitted IDs use the active profile, then the first profile. */
fun selectTrafficConfigForRoutingSuggestions(
    configs: List<TrafficConfigState>,
    activeTrafficConfigId: Int,
    requestedTrafficConfigId: Int?,
): TrafficConfigState? {
    if (requestedTrafficConfigId != null) {
        return configs.firstOrNull { it.id == requestedTrafficConfigId }
    }
    return configs.firstOrNull { it.id == activeTrafficConfigId } ?: configs.firstOrNull()
}

fun resolveGeoSiteSuggestions(
    profile: TrafficConfigState?,
    globalCustomFiles: List<TrafficConfigCustomResourceFile>,
    fileTags: (String) -> List<String>,
): List<GeoSuggestionItem> {
    val result = mutableListOf<GeoSuggestionItem>()
    val standardTags = fileTags(ResourceFileGeoSiteName).ifEmpty { GeoDatTagParser.DefaultGeoSiteTags }
    standardTags.sorted().forEach { tag ->
        val cleanTag = tag.lowercase()
        result += GeoSuggestionItem(
            tag = cleanTag,
            fullRule = "geosite:$cleanTag",
            groupName = "GeoSite",
            isCustom = false,
        )
    }

    collectCustomFiles(profile, globalCustomFiles).forEach { customFile ->
        val fileName = sanitizeCustomResourceFileName(
            value = customFile.name,
            fallback = "custom-resource-${customFile.id}.dat",
        )
        val parsedTags = fileTags(fileName)
        if (parsedTags.isNotEmpty()) {
            parsedTags.sorted().forEach { tag ->
                result += GeoSuggestionItem(
                    tag = tag,
                    fullRule = "ext:$fileName:$tag",
                    groupName = customFile.name.ifBlank { fileName },
                    isCustom = true,
                )
            }
        } else {
            val fallbackTag = customFile.name.substringBeforeLast('.').ifBlank { "all" }
            result += GeoSuggestionItem(
                tag = fallbackTag,
                fullRule = "ext:$fileName:$fallbackTag",
                groupName = customFile.name.ifBlank { fileName },
                isCustom = true,
            )
        }
    }
    return result
}

fun resolveGeoIpSuggestions(
    profile: TrafficConfigState?,
    globalCustomFiles: List<TrafficConfigCustomResourceFile>,
    fileTags: (String) -> List<String>,
    forShadowrocket: Boolean = false,
): List<GeoSuggestionItem> {
    val result = mutableListOf<GeoSuggestionItem>()
    val standardTags = fileTags(ResourceFileGeoIpName).ifEmpty { GeoDatTagParser.DefaultGeoIpTags }
    standardTags.sorted().forEach { tag ->
        val rule = if (forShadowrocket) tag.uppercase() else "geoip:${tag.lowercase()}"
        result += GeoSuggestionItem(
            tag = tag.uppercase(),
            fullRule = rule,
            groupName = "GeoIP",
            isCustom = false,
        )
    }

    collectCustomFiles(profile, globalCustomFiles).forEach { customFile ->
        val fileName = sanitizeCustomResourceFileName(
            value = customFile.name,
            fallback = "custom-resource-${customFile.id}.dat",
        )
        val parsedTags = fileTags(fileName)
        if (parsedTags.isNotEmpty()) {
            parsedTags.sorted().forEach { tag ->
                val rule = if (forShadowrocket) tag.uppercase() else "ext:$fileName:$tag"
                result += GeoSuggestionItem(
                    tag = tag,
                    fullRule = rule,
                    groupName = customFile.name.ifBlank { fileName },
                    isCustom = true,
                )
            }
        } else {
            val fallbackTag = customFile.name.substringBeforeLast('.').ifBlank { "all" }
            val rule = if (forShadowrocket) fallbackTag.uppercase() else "ext:$fileName:$fallbackTag"
            result += GeoSuggestionItem(
                tag = fallbackTag,
                fullRule = rule,
                groupName = customFile.name.ifBlank { fileName },
                isCustom = true,
            )
        }
    }
    return result
}

fun resolveRuleSetSuggestions(
    profile: TrafficConfigState?,
    globalCustomFiles: List<TrafficConfigCustomResourceFile>,
    fileTags: (String) -> List<String>,
): List<GeoSuggestionItem> {
    val geoSite = resolveGeoSiteSuggestions(profile, globalCustomFiles, fileTags)
    val geoIp = resolveGeoIpSuggestions(profile, globalCustomFiles, fileTags, forShadowrocket = false)
    return (geoSite + geoIp).distinctBy { "${it.groupName}:${it.fullRule}" }
}

private fun collectCustomFiles(
    profile: TrafficConfigState?,
    globalCustomFiles: List<TrafficConfigCustomResourceFile>,
): List<TrafficConfigCustomResourceFile> {
    val result = mutableListOf<TrafficConfigCustomResourceFile>()
    val seenNames = mutableSetOf<String>()
    profile?.resourceSettings?.customFiles?.forEach { file ->
        if (file.name.isNotBlank() && seenNames.add(file.name.trim().lowercase())) result += file
    }
    globalCustomFiles.forEach { file ->
        if (file.name.isNotBlank() && seenNames.add(file.name.trim().lowercase())) result += file
    }
    return result
}
