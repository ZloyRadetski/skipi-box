// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import android.content.Context
import android.content.ContextWrapper
import app.AppState
import app.CustomResourceFileState
import app.customResourceFileNameOrNull
import app.sanitizeCustomResourceFileName
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Baseline against the existing Android file/scope adapter before suggestion extraction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoutingSuggestionsProviderAndroidOracleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun explicitProfileUsesItsScopedFilesAndKeepsProfileThenGlobalCustomOrder() {
        val context = FilesContext(temporaryFolder.root)
        val active = profile(11)
        val selected = profile(
            22,
            customFiles = listOf(
                customFile(1, "folder/profile.dat"),
                customFile(2, "Shared.dat"),
            ),
        )
        val appState = AppState(
            trafficConfigs = listOf(active, selected),
            activeTrafficConfigId = active.id,
            customResourceFiles = listOf(
                customFile(3, " shared.DAT "),
                customFile(4, "global.list"),
            ),
        )
        writeGeoDat(scopedDirectory(context, selected.id), "geosite.dat", "z-site", "a-site")
        writeGeoDat(scopedDirectory(context, selected.id), "geoip.dat", "ru")
        writeGeoDat(scopedDirectory(context, selected.id), "profile.dat", "profile-tag")
        writeGeoDat(scopedDirectory(context, selected.id), "Shared.dat", "shared-tag")
        writeGeoDat(scopedDirectory(context, active.id), "geosite.dat", "active-only")

        val actual = RoutingSuggestionsProvider.resolveGeoSiteSuggestions(
            context = context,
            appState = appState,
            trafficConfigId = selected.id,
        )

        assertEquals(
            listOf(
                "geosite:a-site",
                "geosite:z-site",
                "ext:profile.dat:profile-tag",
                "ext:Shared.dat:shared-tag",
                "ext:global.list:global",
            ),
            actual.map(GeoSuggestionItem::fullRule),
        )
        assertEquals(
            listOf("GeoSite", "GeoSite", "folder/profile.dat", "Shared.dat", "global.list"),
            actual.map(GeoSuggestionItem::groupName),
        )
    }

    @Test
    fun omittedIdUsesActiveProfileAndUnknownExplicitIdUsesGlobalDirectory() {
        val context = FilesContext(temporaryFolder.root)
        val active = profile(31)
        val first = profile(30)
        val appState = AppState(
            trafficConfigs = listOf(first, active),
            activeTrafficConfigId = active.id,
        )
        writeGeoDat(scopedDirectory(context, active.id), "geosite.dat", "active-tag")
        writeGeoDat(scopedDirectory(context, first.id), "geosite.dat", "first-tag")
        writeGeoDat(globalDirectory(context), "geosite.dat", "global-tag")

        assertEquals(
            listOf("geosite:active-tag"),
            RoutingSuggestionsProvider.resolveGeoSiteSuggestions(context, appState).map(GeoSuggestionItem::fullRule),
        )
        assertEquals(
            listOf("geosite:global-tag"),
            RoutingSuggestionsProvider.resolveGeoSiteSuggestions(context, appState, trafficConfigId = 404)
                .map(GeoSuggestionItem::fullRule),
        )
    }

    @Test
    fun shadowrocketGeoIpAndRuleSetCallersUseTheirDistinctRuleSpellings() {
        val context = FilesContext(temporaryFolder.root)
        val selected = profile(42, customFiles = listOf(customFile(1, "custom.dat")))
        val appState = AppState(trafficConfigs = listOf(selected), activeTrafficConfigId = selected.id)
        writeGeoDat(scopedDirectory(context, selected.id), "geosite.dat", "google")
        writeGeoDat(scopedDirectory(context, selected.id), "geoip.dat", "cn")
        writeGeoDat(scopedDirectory(context, selected.id), "custom.dat", "custom-tag")

        assertEquals(
            listOf("CN", "CUSTOM-TAG"),
            RoutingSuggestionsProvider.resolveGeoIpSuggestions(context, appState, selected.id, forShadowrocket = true)
                .map(GeoSuggestionItem::fullRule),
        )
        assertEquals(
            listOf("geosite:google", "ext:custom.dat:custom-tag", "geoip:cn"),
            RoutingSuggestionsProvider.resolveRuleSetSuggestions(context, appState, selected.id)
                .map(GeoSuggestionItem::fullRule),
        )
    }

    @Test
    fun androidFilenameSanitizerRetainsItsLiteralSafeSetAndSeparateValidatorRestrictions() {
        assertEquals(
            "file name:*?.dat",
            sanitizeCustomResourceFileName("parent\\folder/file name:*?.dat", "fallback.dat"),
        )
        assertEquals("a_b.dat", sanitizeCustomResourceFileName("a\u0001b.dat", "fallback.dat"))
        assertEquals("odd*?.dat", customResourceFileNameOrNull("odd*?.dat"))
        assertEquals(null, customResourceFileNameOrNull("has space.dat"))
        assertEquals(null, customResourceFileNameOrNull("has:colon.dat"))
    }

    private fun profile(
        id: Int,
        customFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(customFiles = customFiles),
    )

    private fun customFile(id: Int, name: String) = CustomResourceFileState(
        id = id,
        name = name,
        url = "https://resources.example/$id.dat",
    )

    private fun scopedDirectory(context: Context, id: Int) = File(
        File(File(context.filesDir, "xray"), "profiles"),
        id.toString(),
    )

    private fun globalDirectory(context: Context) = File(context.filesDir, "xray")

    private fun writeGeoDat(directory: File, fileName: String, vararg tags: String) {
        directory.mkdirs()
        File(directory, fileName).writeBytes(encodeGeoDat(*tags))
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

    private class FilesContext(
        private val root: File,
    ) : ContextWrapper(null) {
        override fun getFilesDir(): File = root
    }
}
