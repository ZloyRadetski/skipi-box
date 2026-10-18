// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.skipi.app.config.withDnsOptions
import app.skipi.app.config.withGeneralOptions
import app.skipi.app.config.withProfileBasics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-derived oracle for the existing Android profile editor update seam.
 * This exercises the actual AppState row transform and the same shared operations
 * called by TrafficConfigEditorPages; it does not claim Compose/rendered UI coverage.
 */
class TrafficConfigEditorAndroidOracleTest {
    @Test
    fun visualEditTransformsTheCurrentProfileByIdAndPreservesOtherRowsAndState() {
        val first = profile(7, "Before")
        val other = profile(8, "Other")
        val latestFirst = first.copy(
            name = "Changed while editor is open",
            androidSettings = first.androidSettings.copy(enableFakeDns = true),
            rawConfig = first.rawConfig.replace("example.net", "latest.example.net"),
        ).withSkipiSettingsInRawConfig()
        val before = AppState(
            trafficConfigs = listOf(latestFirst, other),
            activeTrafficConfigId = first.id,
            nextTrafficConfigId = 9,
        )

        val withBasics = before.withUpdatedTrafficConfigProfile(first.id) { current ->
            current.withProfileBasics(
                name = "Visual edit",
                sourceUrl = "https://updated.example/profile.conf",
                updateLocked = true,
                autoUpdate = current.autoUpdate,
                updateInterval = current.updateInterval,
                resourceAutoUpdate = current.resourceSettings.autoUpdate,
                resourceUpdateInterval = current.resourceSettings.updateInterval,
            )
        }
        val after = withBasics.withUpdatedTrafficConfigProfile(first.id) { current ->
            current.withGeneralOptions(ipv6 = false, preferIpv6 = true)
        }

        val saved = after.trafficConfigs.first()
        val analysis = saved.rawConfig.analyzeShadowrocketConfig()
        assertEquals(listOf(first.id, other.id), after.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(other, after.trafficConfigs.last())
        assertEquals(first.id, after.activeTrafficConfigId)
        assertEquals(9, after.nextTrafficConfigId)
        assertEquals("Visual edit", saved.name)
        assertEquals("https://updated.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertTrue(saved.androidSettings.enableFakeDns)
        assertTrue(saved.rawConfig.contains("latest.example.net"))
        assertEquals("false", analysis.general["ipv6"])
        assertEquals("true", analysis.general["prefer-ipv6"])
        assertEquals("warning", analysis.general["loglevel"])
        assertTrue(saved.rawConfig.contains("vendor-option = preserve-me"))
    }

    @Test
    fun missingAndroidProfileUpdateDoesNotCreateARow() {
        val state = AppState(trafficConfigs = listOf(profile(7, "Kept")))

        val unchanged = state.withUpdatedTrafficConfigProfile(91) { current ->
            current.withGeneralOptions(ipv6 = false, preferIpv6 = false)
        }

        assertEquals(state, unchanged)
        assertEquals(listOf(7), unchanged.trafficConfigs.map(TrafficConfigState::id))
        assertEquals("Kept", unchanged.trafficConfigs.single().name)
    }

    @Test
    fun rawSaveUsesAndroidCollectionUpdaterToReadThenRewriteKnownSkipiSettings() {
        val edited = profile(12, "Envelope before").copy(
            sourceUrl = "https://old.example/profile.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 456L,
        )
        val other = profile(13, "Selected neighbor")
        val before = AppState(
            trafficConfigs = listOf(edited, other),
            activeTrafficConfigId = other.id,
            nextTrafficConfigId = 18,
        )
        val rawDraft = """# edited in Android raw editor
[General]
loglevel = debug

[Proxy]
server = raw.example:443

[SKIPI]
profile-name = Raw editor name
profile-update-url = https://raw.example/profile.conf
profile-update-locked = true
profile-auto-update = false
profile-update-interval = 48
resource-auto-update = false
resource-update-interval = 96

[Vendor]
vendor-option = keep-on-raw-save"""

        // This is the actual updater used by Android's AppState path when Raw Edit saves.
        val after = before.withUpdatedTrafficConfigProfile(edited.id) { current ->
            current.copy(rawConfig = rawDraft.trimEnd() + "\n")
        }
        val saved = after.trafficConfigs.single { it.id == edited.id }
        val skipi = saved.rawConfig.skipiSectionValues()

        assertEquals(listOf(edited.id, other.id), after.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(other, after.trafficConfigs.single { it.id == other.id })
        assertEquals(other.id, after.activeTrafficConfigId)
        assertEquals(18, after.nextTrafficConfigId)
        assertEquals("Raw editor name", saved.name)
        assertEquals("https://raw.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertFalse(saved.autoUpdate)
        assertEquals("48", saved.updateInterval)
        assertFalse(saved.resourceSettings.autoUpdate)
        assertEquals("96", saved.resourceSettings.updateInterval)
        assertEquals(456L, saved.lastUpdatedAtMillis)
        assertEquals("Raw editor name", skipi[SkipiProfileName]?.last())
        assertEquals("https://raw.example/profile.conf", skipi[SkipiProfileUpdateUrl]?.last())
        assertEquals("false", skipi[SkipiProfileAutoUpdate]?.last())
        assertEquals("false", skipi[SkipiResourceAutoUpdate]?.last())
        assertTrue(saved.rawConfig.contains("vendor-option = keep-on-raw-save"))
        assertTrue(saved.rawConfig.endsWith("\n"))
    }

    @Test
    fun dnsEditorTransformUsesAndroidTypedSettingsAndRetainsProfileSections() {
        val initial = profile(12, "DNS")
        val updated = initial.withDnsOptions(
            initial.androidSettings.copy(
                tunVpnDns = " 10.0.0.1 ",
                proxyDns = listOf(" https://1.1.1.1/dns-query ", "https://8.8.8.8/dns-query"),
                directDns = listOf(" 77.88.8.8 ", "77.88.8.1"),
            ),
        )

        assertEquals(listOf("77.88.8.8", "77.88.8.1"), updated.androidSettings.directDns)
        assertEquals("10.0.0.1", updated.androidSettings.tunVpnDns)
        assertEquals("warning", updated.rawConfig.analyzeShadowrocketConfig().general["loglevel"])
        assertTrue(updated.rawConfig.contains("server = proxy.example:443"))
        assertTrue(updated.rawConfig.contains("vendor-option = preserve-me"))
    }

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        sourceUrl = "https://old.example/profile.conf",
        updateLocked = false,
        autoUpdate = true,
        updateInterval = "24",
        rawConfig = """# external comment
[General]
loglevel = warning

[Proxy]
server = proxy.example:443

[Rule]
DOMAIN-SUFFIX,example.net,DIRECT

[SKIPI]
profile-name = $name
profile-update-url = https://old.example/profile.conf
profile-update-locked = false
profile-auto-update = true
profile-update-interval = 24
resource-auto-update = true
resource-update-interval = 72

[Vendor]
vendor-option = preserve-me
""".trimIndent(),
    ).withSkipiSettingsReadFromRawConfig()
}
