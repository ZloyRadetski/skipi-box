// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileSourceV2FlyGithub
import features.routing.usecase.GeoDatTagParser
import features.routing.usecase.GeoSuggestionItem
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopRoutingSuggestionsProviderTest {
    @Test
    fun geoSiteSuggestionsUseTheSuppliedProfileFilesInsteadOfGlobalOrSiblingFiles() = withResources { root, resourceFiles ->
        val selected = profile(
            id = 17,
            source = ResourceFileSourceV2FlyGithub,
            customFiles = listOf(customFile(1, "folder/Custom.dat")),
        )
        val sibling = profile(
            id = 18,
            source = ResourceFileSourceLoyalsoldierGithub,
            customFiles = listOf(customFile(2, "folder/Custom.dat")),
        )

        writeGeoDat(resourceFiles.snapshot(selected).file(ResourceFileKind.GeoSite), "z-site", "a-site")
        writeGeoDat(resourceFiles.snapshot(selected).file(selected.resourceSettings.customFiles.single()), "Zed", "alpha")
        writeGeoDat(resourceFiles.snapshot(sibling).file(ResourceFileKind.GeoSite), "sibling-site")
        writeGeoDat(resourceFiles.snapshot(sibling).file(sibling.resourceSettings.customFiles.single()), "sibling-custom")
        writeGeoDat(File(root, ResourceFileKind.GeoSite.fileName), "global-site")

        val suggestions = DesktopRoutingSuggestionsProvider(resourceFiles).resolveGeoSiteSuggestions(selected)

        assertEquals(
            listOf(
                item("a-site", "geosite:a-site", "GeoSite"),
                item("z-site", "geosite:z-site", "GeoSite"),
                item("Zed", "ext:Custom.dat:Zed", "folder/Custom.dat", custom = true),
                item("alpha", "ext:Custom.dat:alpha", "folder/Custom.dat", custom = true),
            ),
            suggestions,
        )
    }

    @Test
    fun geoIpSuggestionsKeepAndroidNormalAndShadowrocketSpellings() = withResources { _, resourceFiles ->
        val profile = profile(
            id = 23,
            source = ResourceFileSourceV2FlyGithub,
            customFiles = listOf(customFile(3, "Extra.dat")),
        )
        val snapshot = resourceFiles.snapshot(profile)
        writeGeoDat(snapshot.file(ResourceFileKind.GeoIp), "us", "cn")
        writeGeoDat(snapshot.file(profile.resourceSettings.customFiles.single()), "zTag", "aTag")
        val provider = DesktopRoutingSuggestionsProvider(resourceFiles)

        assertEquals(
            listOf(
                item("CN", "geoip:cn", "GeoIP"),
                item("US", "geoip:us", "GeoIP"),
                item("aTag", "ext:Extra.dat:aTag", "Extra.dat", custom = true),
                item("zTag", "ext:Extra.dat:zTag", "Extra.dat", custom = true),
            ),
            provider.resolveGeoIpSuggestions(profile),
        )
        assertEquals(
            listOf(
                item("CN", "CN", "GeoIP"),
                item("US", "US", "GeoIP"),
                item("aTag", "ATAG", "Extra.dat", custom = true),
                item("zTag", "ZTAG", "Extra.dat", custom = true),
            ),
            provider.resolveGeoIpSuggestions(profile, forShadowrocket = true),
        )
    }

    @Test
    fun ruleSetSuggestionsCombineGeoSiteGeoIpAndProfileCustomFiles() = withResources { _, resourceFiles ->
        val profile = profile(
            id = 29,
            source = ResourceFileSourceLoyalsoldierGithub,
            customFiles = listOf(customFile(4, "custom-rules.dat")),
        )
        val snapshot = resourceFiles.snapshot(profile)
        writeGeoDat(snapshot.file(ResourceFileKind.GeoSite), "z-site", "a-site")
        writeGeoDat(snapshot.file(ResourceFileKind.GeoIp), "us", "cn")
        writeGeoDat(snapshot.file(profile.resourceSettings.customFiles.single()), "custom-tag")

        val suggestions = DesktopRoutingSuggestionsProvider(resourceFiles).resolveRuleSetSuggestions(profile)

        assertEquals(
            listOf(
                "geosite:a-site",
                "geosite:z-site",
                "ext:custom-rules.dat:custom-tag",
                "geoip:cn",
                "geoip:us",
            ),
            suggestions.map(GeoSuggestionItem::fullRule),
        )
    }

    @Test
    fun emptyOrMissingDatFilesUseCommonDefaultsAndMissingCustomMetadataStillHasFallback() = withResources { _, resourceFiles ->
        val missingCustom = customFile(5, "missing.dat")
        val profile = profile(
            id = 31,
            source = ResourceFileSourceV2FlyGithub,
            customFiles = listOf(missingCustom),
        )
        val emptyGeoSite = resourceFiles.snapshot(profile).file(ResourceFileKind.GeoSite).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf())
        }
        val provider = DesktopRoutingSuggestionsProvider(resourceFiles)

        val expectedGeoSite = GeoDatTagParser.DefaultGeoSiteTags.sorted().map { tag ->
            item(tag.lowercase(), "geosite:${tag.lowercase()}", "GeoSite")
        } + item("missing", "ext:missing.dat:missing", "missing.dat", custom = true)
        assertEquals(expectedGeoSite, provider.resolveGeoSiteSuggestions(profile))

        val expectedGeoIp = GeoDatTagParser.DefaultGeoIpTags.sorted().map { tag ->
            item(tag.uppercase(), "geoip:${tag.lowercase()}", "GeoIP")
        } + item("missing", "ext:missing.dat:missing", "missing.dat", custom = true)
        assertEquals(expectedGeoIp, provider.resolveGeoIpSuggestions(profile))
        assertEquals(0L, emptyGeoSite.length())
    }

    private fun profile(
        id: Int,
        source: Int,
        customFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(
            source = source,
            customFiles = customFiles,
        ),
    )

    private fun customFile(id: Int, name: String) = TrafficConfigCustomResourceFile(
        id = id,
        name = name,
        url = "https://example.invalid/$id.dat",
    )

    private fun item(
        tag: String,
        fullRule: String,
        groupName: String,
        custom: Boolean = false,
    ) = GeoSuggestionItem(
        tag = tag,
        fullRule = fullRule,
        groupName = groupName,
        isCustom = custom,
    )

    private fun writeGeoDat(file: File, vararg tags: String) {
        file.parentFile?.mkdirs()
        file.writeBytes(encodeGeoDat(*tags))
    }

    private fun encodeGeoDat(vararg tags: String): ByteArray = buildList {
        tags.forEach { tag ->
            val tagBytes = tag.encodeToByteArray()
            val entry = byteArrayOf(0x0a, tagBytes.size.toByte()) + tagBytes
            add(0x0a.toByte())
            add(entry.size.toByte())
            addAll(entry.asList())
        }
    }.toByteArray()

    private inline fun withResources(block: (File, DesktopProfileResourceFiles) -> Unit) {
        val root = Files.createTempDirectory("skipi-desktop-routing-suggestions-").toFile()
        try {
            block(root, DesktopProfileResourceFiles(root))
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
