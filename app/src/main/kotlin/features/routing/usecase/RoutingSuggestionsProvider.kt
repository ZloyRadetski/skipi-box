// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import android.content.Context
import app.AppState
import app.CustomResourceFileState
import app.ResourceFileKind
import app.sanitizeCustomResourceFileName
import features.config.TrafficConfigState
import features.resources.runtime.xrayResourceFilesDir
import java.io.File

data class GeoSuggestionItem(
    val tag: String,
    val fullRule: String,
    val groupName: String,
    val isCustom: Boolean = false,
)

object RoutingSuggestionsProvider {

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

    val DomainPrefixes = listOf(
        "geosite:",
        "domain:",
        "full:",
        "keyword:",
        "regexp:",
    )

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

    val ShadowrocketNetworkOptions = listOf(
        "tcp",
        "udp",
        "tcp,udp",
        "udp,tcp",
    )

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

    fun resolveRuleSetSuggestions(
        context: Context,
        appState: AppState,
        trafficConfigId: Int? = null,
    ): List<GeoSuggestionItem> {
        val geoSite = resolveGeoSiteSuggestions(context, appState, trafficConfigId)
        val geoIp = resolveGeoIpSuggestions(context, appState, trafficConfigId, forShadowrocket = false)
        return (geoSite + geoIp).distinctBy { "${it.groupName}:${it.fullRule}" }
    }

    fun resolveGeoSiteSuggestions(
        context: Context,
        appState: AppState,
        trafficConfigId: Int? = null,
    ): List<GeoSuggestionItem> {
        val result = mutableListOf<GeoSuggestionItem>()
        val dataDir = context.xrayResourceFilesDir()

        // 1. Standard geosite.dat
        val standardGeoSiteFile = File(dataDir, ResourceFileKind.GeoSite.fileName)
        val standardTags = if (standardGeoSiteFile.isFile && standardGeoSiteFile.length() > 0) {
            GeoDatParser.parseTags(standardGeoSiteFile).ifEmpty { GeoDatParser.DefaultGeoSiteTags }
        } else {
            GeoDatParser.DefaultGeoSiteTags
        }

        standardTags.sorted().forEach { tag ->
            val cleanTag = tag.lowercase()
            result.add(
                GeoSuggestionItem(
                    tag = cleanTag,
                    fullRule = "geosite:$cleanTag",
                    groupName = "GeoSite",
                    isCustom = false,
                ),
            )
        }

        // 2. Custom files from the target profile and global AppState
        val targetConfig = findTargetTrafficConfig(appState, trafficConfigId)
        val customFiles = collectCustomFiles(targetConfig, appState)

        customFiles.forEach { customFile ->
            val fileName = sanitizeCustomResourceFileName(
                value = customFile.name,
                fallback = "custom-resource-${customFile.id}.dat",
            )
            val file = File(dataDir, fileName)
            val parsedTags = if (file.isFile && file.length() > 0) {
                GeoDatParser.parseTags(file)
            } else {
                emptyList()
            }

            if (parsedTags.isNotEmpty()) {
                parsedTags.sorted().forEach { tag ->
                    result.add(
                        GeoSuggestionItem(
                            tag = tag,
                            fullRule = "ext:$fileName:$tag",
                            groupName = customFile.name.ifBlank { fileName },
                            isCustom = true,
                        ),
                    )
                }
            } else {
                val fallbackTag = customFile.name.substringBeforeLast('.').ifBlank { "all" }
                result.add(
                    GeoSuggestionItem(
                        tag = fallbackTag,
                        fullRule = "ext:$fileName:$fallbackTag",
                        groupName = customFile.name.ifBlank { fileName },
                        isCustom = true,
                    ),
                )
            }
        }

        return result
    }

    fun resolveGeoIpSuggestions(
        context: Context,
        appState: AppState,
        trafficConfigId: Int? = null,
        forShadowrocket: Boolean = false,
    ): List<GeoSuggestionItem> {
        val result = mutableListOf<GeoSuggestionItem>()
        val dataDir = context.xrayResourceFilesDir()

        // 1. Standard geoip.dat
        val standardGeoIpFile = File(dataDir, ResourceFileKind.GeoIp.fileName)
        val standardTags = if (standardGeoIpFile.isFile && standardGeoIpFile.length() > 0) {
            GeoDatParser.parseTags(standardGeoIpFile).ifEmpty { GeoDatParser.DefaultGeoIpTags }
        } else {
            GeoDatParser.DefaultGeoIpTags
        }

        standardTags.sorted().forEach { tag ->
            val rule = if (forShadowrocket) tag.uppercase() else "geoip:${tag.lowercase()}"
            result.add(
                GeoSuggestionItem(
                    tag = tag.uppercase(),
                    fullRule = rule,
                    groupName = "GeoIP",
                    isCustom = false,
                ),
            )
        }

        // 2. Custom files
        val targetConfig = findTargetTrafficConfig(appState, trafficConfigId)
        val customFiles = collectCustomFiles(targetConfig, appState)

        customFiles.forEach { customFile ->
            val fileName = sanitizeCustomResourceFileName(
                value = customFile.name,
                fallback = "custom-resource-${customFile.id}.dat",
            )
            val file = File(dataDir, fileName)
            val parsedTags = if (file.isFile && file.length() > 0) {
                GeoDatParser.parseTags(file)
            } else {
                emptyList()
            }

            if (parsedTags.isNotEmpty()) {
                parsedTags.sorted().forEach { tag ->
                    val rule = if (forShadowrocket) tag.uppercase() else "ext:$fileName:$tag"
                    result.add(
                        GeoSuggestionItem(
                            tag = tag,
                            fullRule = rule,
                            groupName = customFile.name.ifBlank { fileName },
                            isCustom = true,
                        ),
                    )
                }
            } else {
                val fallbackTag = customFile.name.substringBeforeLast('.').ifBlank { "all" }
                val rule = if (forShadowrocket) fallbackTag.uppercase() else "ext:$fileName:$fallbackTag"
                result.add(
                    GeoSuggestionItem(
                        tag = fallbackTag,
                        fullRule = rule,
                        groupName = customFile.name.ifBlank { fileName },
                        isCustom = true,
                    ),
                )
            }
        }

        return result
    }

    private fun findTargetTrafficConfig(
        appState: AppState,
        trafficConfigId: Int?,
    ): TrafficConfigState? {
        if (trafficConfigId != null) {
            return appState.trafficConfigs.firstOrNull { it.id == trafficConfigId }
        }
        return appState.trafficConfigs.firstOrNull { it.id == appState.activeTrafficConfigId }
            ?: appState.trafficConfigs.firstOrNull()
    }

    private fun collectCustomFiles(
        targetConfig: TrafficConfigState?,
        appState: AppState,
    ): List<CustomResourceFileState> {
        val list = mutableListOf<CustomResourceFileState>()
        val seenNames = mutableSetOf<String>()

        targetConfig?.resourceSettings?.customFiles?.forEach { customFile ->
            if (customFile.name.isNotBlank() && seenNames.add(customFile.name.trim().lowercase())) {
                list.add(customFile)
            }
        }

        appState.customResourceFiles.forEach { customFile ->
            if (customFile.name.isNotBlank() && seenNames.add(customFile.name.trim().lowercase())) {
                list.add(customFile)
            }
        }

        return list
    }
}
