// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigState
import features.config.sanitizeCustomResourceFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingSuggestionsTest {
    @Test
    fun explicitProfileSelectionDoesNotFallBackButImplicitSelectionUsesActiveThenFirst() {
        val first = profile(11)
        val active = profile(22)
        val configs = listOf(first, active)

        assertEquals(active, selectTrafficConfigForRoutingSuggestions(configs, 11, 22))
        assertNull(selectTrafficConfigForRoutingSuggestions(configs, 22, 404))
        assertEquals(active, selectTrafficConfigForRoutingSuggestions(configs, 22, null))
        assertEquals(first, selectTrafficConfigForRoutingSuggestions(configs, 404, null))
        assertNull(selectTrafficConfigForRoutingSuggestions(emptyList(), 404, null))
    }

    @Test
    fun geoSiteSuggestionsSortStandardTagsMergeProfileThenGlobalAndKeepMissingCustomFallbacks() {
        val profile = profile(
            7,
            customFiles = listOf(
                customFile(1, "folder/rules.dat"),
                customFile(2, "Shared.dat"),
                customFile(3, "   "),
            ),
        )
        val globalFiles = listOf(
            customFile(4, " shared.DAT "),
            customFile(5, "Global.list"),
            customFile(6, "plain"),
        )
        val fileTags = recordingTags(
            "geosite.dat" to listOf("zeta", "beta"),
            "rules.dat" to listOf("z-custom", "a-custom"),
            "Shared.dat" to listOf("shared-tag"),
            "Global.list" to emptyList(),
            "plain" to emptyList(),
        )

        val suggestions = resolveGeoSiteSuggestions(profile, globalFiles, fileTags)

        assertEquals(
            listOf(
                item("beta", "geosite:beta", "GeoSite", custom = false),
                item("zeta", "geosite:zeta", "GeoSite", custom = false),
                item("a-custom", "ext:rules.dat:a-custom", "folder/rules.dat"),
                item("z-custom", "ext:rules.dat:z-custom", "folder/rules.dat"),
                item("shared-tag", "ext:Shared.dat:shared-tag", "Shared.dat"),
                item("Global", "ext:Global.list:Global", "Global.list"),
                item("plain", "ext:plain:plain", "plain"),
            ),
            suggestions,
        )
        assertEquals(
            listOf("geosite.dat", "rules.dat", "Shared.dat", "Global.list", "plain"),
            fileTags.requestedFiles,
        )
    }

    @Test
    fun emptyStandardGeoSiteUsesTheExistingDefaultCatalog() {
        val suggestions = resolveGeoSiteSuggestions(
            profile = null,
            globalCustomFiles = emptyList(),
            fileTags = { emptyList() },
        )

        assertEquals(
            GeoDatTagParser.DefaultGeoSiteTags.sorted().map { tag ->
                item(tag.lowercase(), "geosite:${tag.lowercase()}", "GeoSite", custom = false)
            },
            suggestions,
        )
    }

    @Test
    fun geoIpUsesStandardAndCustomSpellingsForNormalAndShadowrocketEditors() {
        val profile = profile(3, customFiles = listOf(customFile(1, "custom.dat")))
        val fileTags = recordingTags(
            "geoip.dat" to listOf("us", "cn"),
            "custom.dat" to listOf("custom-tag"),
        )

        assertEquals(
            listOf(
                item("CN", "geoip:cn", "GeoIP", custom = false),
                item("US", "geoip:us", "GeoIP", custom = false),
                item("custom-tag", "ext:custom.dat:custom-tag", "custom.dat"),
            ),
            resolveGeoIpSuggestions(profile, emptyList(), fileTags),
        )
        assertEquals(
            listOf(
                item("CN", "CN", "GeoIP", custom = false),
                item("US", "US", "GeoIP", custom = false),
                item("custom-tag", "CUSTOM-TAG", "custom.dat"),
            ),
            resolveGeoIpSuggestions(profile, emptyList(), fileTags, forShadowrocket = true),
        )
    }

    @Test
    fun missingCustomGeoIpUsesNameStemAndShadowrocketBareUppercaseRule() {
        val profile = profile(4, customFiles = listOf(customFile(1, "MyGeo.list")))
        val fileTags = recordingTags("geoip.dat" to emptyList(), "MyGeo.list" to emptyList())

        assertEquals(
            item("MyGeo", "ext:MyGeo.list:MyGeo", "MyGeo.list"),
            resolveGeoIpSuggestions(profile, emptyList(), fileTags).last(),
        )
        assertEquals(
            item("MyGeo", "MYGEO", "MyGeo.list"),
            resolveGeoIpSuggestions(profile, emptyList(), fileTags, forShadowrocket = true).last(),
        )
    }

    @Test
    fun ruleSetSuggestionsMergeStandardAndCustomEntriesWithoutDuplicatingCustomRule() {
        val profile = profile(8, customFiles = listOf(customFile(1, "same.dat")))
        val fileTags = recordingTags(
            "geosite.dat" to listOf("google"),
            "geoip.dat" to listOf("cn"),
            "same.dat" to listOf("private"),
        )

        assertEquals(
            listOf(
                item("google", "geosite:google", "GeoSite", custom = false),
                item("private", "ext:same.dat:private", "same.dat"),
                item("CN", "geoip:cn", "GeoIP", custom = false),
            ),
            resolveRuleSetSuggestions(profile, emptyList(), fileTags),
        )
    }

    @Test
    fun customResourceFilenameSanitizerKeepsAndroidLiteralCharactersAndOnlyStripsPathSeparators() {
        assertEquals(
            "file name:*?.dat",
            sanitizeCustomResourceFileName("parent\\folder/file name:*?.dat", "fallback.dat"),
        )
        assertEquals("a_b.dat", sanitizeCustomResourceFileName("a\u0001b.dat", "fallback.dat"))
        assertEquals("fallback.dat", sanitizeCustomResourceFileName("..", "fallback.dat"))
        assertEquals("fallback.dat", sanitizeCustomResourceFileName("  ", "fallback.dat"))
    }

    @Test
    fun portablePresetCatalogMatchesTheCurrentAndroidLists() {
        assertEquals(listOf("tcp", "udp", "tcp,udp"), RoutingSuggestionPresets.NetworkOptions)
        assertEquals(listOf("", "tcp", "udp", "tcp,udp"), RoutingSuggestionPresets.NetworkOptionsWithAny)
        assertEquals(
            listOf("http", "tls", "bittorrent", "quic", "stun", "dns", "dtls", "ssh", "wireguard"),
            RoutingSuggestionPresets.ProtocolOptions,
        )
        assertEquals(
            listOf("80", "443", "80,443", "53", "22", "8080", "8443", "1-65535"),
            RoutingSuggestionPresets.PortPresets,
        )
        assertEquals(
            listOf("geosite:", "domain:", "full:", "keyword:", "regexp:"),
            RoutingSuggestionPresets.DomainPrefixes,
        )
        assertEquals(
            listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "::1/128", "fc00::/7", "fe80::/10"),
            RoutingSuggestionPresets.PrivateIpPresets,
        )
        assertEquals(
            listOf("*Telegram*", "*Twitter*", "*Instagram*", "*WhatsApp*", "*Apple*", "*MicroMessenger*", "*YouTube*", "*Google*", "*Mozilla*", "*Netflix*", "*Spotify*"),
            RoutingSuggestionPresets.ShadowrocketUserAgentPresets,
        )
        assertEquals(listOf("tcp", "udp", "tcp,udp", "udp,tcp"), RoutingSuggestionPresets.ShadowrocketNetworkOptions)
        assertEquals(
            listOf(
                "geosite:google", "geosite:cn", "geosite:geolocation-!cn", "geosite:category-ads-all",
                "geosite:telegram", "geosite:youtube", "geosite:netflix", "geosite:openai", "geosite:github",
                "geosite:twitter", "geoip:cn", "geoip:telegram", "geoip:private",
                "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/gfw.txt",
                "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/direct.txt",
                "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/reject.txt",
                "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/proxy.txt",
            ),
            RoutingSuggestionPresets.RuleSetPresets,
        )
        assertEquals(
            listOf(
                "geosite:google", "geosite:cn", "geosite:geolocation-!cn", "geosite:category-ads-all",
                "geosite:telegram", "geosite:youtube", "geosite:netflix", "geosite:openai", "geosite:github",
                "geosite:twitter", "geosite:apple", "geosite:facebook",
            ),
            RoutingSuggestionPresets.DomainSetPresets,
        )
    }

    private fun profile(
        id: Int,
        customFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = features.config.TrafficConfigResourceSettings(customFiles = customFiles),
    )

    private fun customFile(id: Int, name: String) = TrafficConfigCustomResourceFile(
        id = id,
        name = name,
        url = "https://resources.example/$id.dat",
    )

    private fun item(tag: String, fullRule: String, groupName: String, custom: Boolean = true) =
        GeoSuggestionItem(tag, fullRule, groupName, custom)

    private fun recordingTags(vararg values: Pair<String, List<String>>): RecordingTagReader {
        val entries = values.toMap()
        return RecordingTagReader { fileName -> entries[fileName].orEmpty() }
    }

    private class RecordingTagReader(
        private val read: (String) -> List<String>,
    ) : (String) -> List<String> {
        val requestedFiles = mutableListOf<String>()

        override fun invoke(fileName: String): List<String> {
            requestedFiles += fileName
            return read(fileName)
        }
    }
}
