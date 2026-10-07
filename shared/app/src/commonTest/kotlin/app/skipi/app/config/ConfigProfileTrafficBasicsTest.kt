// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ConfigProfile
import features.config.ConfigProfileLibraries
import features.config.ConfigProfileLibrary
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigProfileTrafficConfigBasicsTest {
    @Test
    fun profileBasicsSaveRetainsTypedDocumentValuesAndExternalRawSections() {
        val originalContent = """# external file comment
[General]
# external general comment
loglevel = warning

[SKIPI]
# this section comment is dropped when profile basics are committed
profile-name = Name from raw
profile-update-url = https://raw.example/profile
profile-update-locked = false
profile-auto-update = true
profile-update-interval = 18
fake-dns = true
per-app-mode = whitelist
per-app-package = com.example.allowed
resource-auto-update = false
resource-update-interval = 36
future-skipi-option = discard-me

[Vendor]
# external vendor comment
vendor-option = preserve-me
""".trimIndent() + "\n"
        val profile = ConfigProfile(
            id = 41,
            name = "Envelope name",
            content = originalContent,
            sourceUrl = "https://envelope.example/profile",
            updateLocked = false,
            lastUpdatedAtMillis = 1234L,
        )
        val neighbor = ConfigProfile(
            id = 88,
            name = "Neighbor",
            content = "[General]\nloglevel = error\n",
        )
        val library = ConfigProfileLibrary(
            selectedConfigId = neighbor.id,
            configs = listOf(profile, neighbor),
        )

        val updated = profile.withTrafficConfigBasics(
            name = "Edited name",
            sourceUrl = "https://edited.example/profile",
            updateLocked = true,
        )
        val updatedLibrary = ConfigProfileLibraries.update(
            library = library,
            id = updated.id,
            name = updated.name,
            content = updated.content,
            sourceUrl = updated.sourceUrl,
            updateLocked = updated.updateLocked,
            lastUpdatedAtMillis = updated.lastUpdatedAtMillis,
        )
        val state = TrafficConfigState(
            id = updated.id,
            name = updated.name,
            rawConfig = updated.content,
            sourceUrl = updated.sourceUrl,
            updateLocked = updated.updateLocked,
            lastUpdatedAtMillis = updated.lastUpdatedAtMillis,
        ).withSkipiSettingsReadFromRawConfig()
        val skipiBody = updated.content.substringAfter("[SKIPI]\n").substringBefore("\n[Vendor]")

        assertEquals(41, updated.id)
        assertEquals(1234L, updated.lastUpdatedAtMillis)
        assertEquals("Edited name", updated.name)
        assertEquals("https://edited.example/profile", updated.sourceUrl)
        assertTrue(updated.updateLocked)
        assertTrue(state.autoUpdate)
        assertEquals("18", state.updateInterval)
        assertTrue(state.androidSettings.enableFakeDns)
        assertEquals(listOf("com.example.allowed"), state.proxyAppListSelectedApps)
        assertFalse(state.resourceSettings.autoUpdate)
        assertEquals("36", state.resourceSettings.updateInterval)
        assertTrue(skipiBody.contains("profile-name = Edited name"))
        assertTrue(skipiBody.contains("profile-update-url = https://edited.example/profile"))
        assertTrue(skipiBody.contains("profile-update-locked = true"))
        assertTrue(skipiBody.contains("fake-dns = true"))
        assertFalse(skipiBody.contains("future-skipi-option"))
        assertFalse(skipiBody.contains("section comment is dropped"))
        assertTrue(updated.content.contains("# external file comment"))
        assertTrue(updated.content.contains("# external general comment"))
        assertTrue(updated.content.contains("loglevel = warning"))
        assertTrue(updated.content.contains("# external vendor comment"))
        assertTrue(updated.content.contains("vendor-option = preserve-me"))
        assertEquals(originalContent, profile.content)
        assertEquals(neighbor.id, updatedLibrary.selectedConfigId)
        assertEquals(listOf(profile.id, neighbor.id), updatedLibrary.configs.map(ConfigProfile::id))
        assertEquals(neighbor, updatedLibrary.configs.last())
    }

    @Test
    fun seedSuppliesOnlyMissingTypedDefaultsAndNeverReplacesEnvelopeIdentityOrRaw() {
        val originalContent = """[General]
# external comment
loglevel = info

[SKIPI]
# inner comment should be dropped
fake-dns = false
resource-auto-update = false
future-skipi-option = discard-me

[Vendor]
# vendor comment should stay
vendor-option = preserve-me
""".trimIndent() + "\n"
        val profile = ConfigProfile(
            id = 42,
            name = "Envelope old name",
            content = originalContent,
            sourceUrl = "https://envelope.example/old",
            updateLocked = true,
            lastUpdatedAtMillis = 2345L,
        )
        val seed = TrafficConfigState(
            id = 999,
            name = "Foreign seed name",
            rawConfig = "[SeedOnly]\nseed-only = must-not-copy\n",
            sourceUrl = "https://seed.invalid/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 9999L,
            autoUpdate = true,
            updateInterval = "48",
            androidSettings = TrafficConfigAndroidSettings(
                enableSniffing = false,
                enableFakeDns = true,
            ),
            resourceSettings = TrafficConfigResourceSettings(
                userAgent = "Seed UA",
                autoUpdate = true,
                updateInterval = "36",
            ),
        )

        val updated = profile.withTrafficConfigBasics(
            name = "Envelope new name",
            sourceUrl = "https://envelope.example/new",
            updateLocked = false,
            seed = seed,
        )
        val state = TrafficConfigState(
            id = updated.id,
            name = updated.name,
            rawConfig = updated.content,
            sourceUrl = updated.sourceUrl,
            updateLocked = updated.updateLocked,
            lastUpdatedAtMillis = updated.lastUpdatedAtMillis,
            autoUpdate = seed.autoUpdate,
            updateInterval = seed.updateInterval,
            androidSettings = seed.androidSettings,
            resourceSettings = seed.resourceSettings,
        ).withSkipiSettingsReadFromRawConfig()
        val skipiBody = updated.content.substringAfter("[SKIPI]\n").substringBefore("\n[Vendor]")

        assertEquals(42, updated.id)
        assertEquals(2345L, updated.lastUpdatedAtMillis)
        assertEquals("Envelope new name", updated.name)
        assertEquals("https://envelope.example/new", updated.sourceUrl)
        assertFalse(updated.updateLocked)
        assertEquals("Envelope old name", profile.name)
        assertEquals("https://envelope.example/old", profile.sourceUrl)
        assertTrue(state.autoUpdate)
        assertEquals("48", state.updateInterval)
        assertFalse(state.androidSettings.enableFakeDns)
        assertFalse(state.androidSettings.enableSniffing)
        assertFalse(state.resourceSettings.autoUpdate)
        assertEquals("36", state.resourceSettings.updateInterval)
        assertEquals("Seed UA", state.resourceSettings.userAgent)
        assertTrue(skipiBody.contains("profile-auto-update = true"))
        assertTrue(skipiBody.contains("profile-update-interval = 48"))
        assertTrue(skipiBody.contains("sniffing = false"))
        assertTrue(skipiBody.contains("fake-dns = false"))
        assertTrue(skipiBody.contains("resource-auto-update = false"))
        assertTrue(skipiBody.contains("resource-update-interval = 36"))
        assertTrue(skipiBody.contains("resource-user-agent = Seed UA"))
        assertFalse(updated.content.contains("Foreign seed name"))
        assertFalse(updated.content.contains("seed.invalid"))
        assertFalse(updated.content.contains("SeedOnly"))
        assertFalse(skipiBody.contains("future-skipi-option"))
        assertFalse(skipiBody.contains("inner comment should be dropped"))
        assertTrue(updated.content.contains("# external comment"))
        assertTrue(updated.content.contains("# vendor comment should stay"))
        assertTrue(updated.content.contains("vendor-option = preserve-me"))
        assertEquals(originalContent, profile.content)
        assertEquals(999, seed.id)
        assertEquals("Foreign seed name", seed.name)
        assertEquals("[SeedOnly]\nseed-only = must-not-copy\n", seed.rawConfig)
    }
}
