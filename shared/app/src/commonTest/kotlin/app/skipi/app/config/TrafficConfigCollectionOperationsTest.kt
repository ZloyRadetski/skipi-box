// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import app.skipi.app.model.TrafficConfigRecord
import features.config.SkipiPerAppModeGlobal
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficConfigCollectionOperationsTest {
    @Test
    fun readingAndProjectingProfileLeavesUntouchedRawDocumentAlone() {
        val rawDocument = """# external comment
[General]
loglevel = warning

[SKIPI]
# keep this until a profile edit is committed
profile-name = Document name
profile-update-url = https://example.test/profile
profile-update-locked = yes
future-skipi-option = opaque-value

[Vendor]
# external section comment
vendor-option = keep
""".trimIndent() + "\n"
        val stored = TrafficConfigState(
            id = 17,
            name = "Envelope name",
            rawConfig = rawDocument,
        )

        val read = stored.withSkipiSettingsReadFromRawConfig()
        val projected = TrafficConfigRecord(
            id = read.id,
            name = read.name,
            rawDocument = read.rawConfig,
            sourceUrl = read.sourceUrl,
            locked = read.updateLocked,
        )
        val perAppPolicy = projected.perAppSettings

        assertEquals("Document name", read.name)
        assertEquals("https://example.test/profile", read.sourceUrl)
        assertEquals(true, read.updateLocked)
        assertEquals(rawDocument, read.rawConfig)
        assertEquals(rawDocument, projected.rawDocument)
        assertEquals(SkipiPerAppModeGlobal, perAppPolicy.mode)
        assertEquals(emptyList<String>(), perAppPolicy.selectedApps)
    }

    @Test
    fun updatePersistsSettingsIntoDocumentWithoutChangingProfileIdentity() {
        val profile = TrafficConfigState(
            id = 17,
            name = "Before",
            rawConfig = "[General]\nloglevel = warning\n",
            sourceUrl = "https://example.test/config",
        )

        val updated = updateTrafficConfigProfile(listOf(profile), profileId = 17) {
            it.copy(name = "After", updateLocked = true)
        }.single()

        assertEquals(17, updated.id)
        assertEquals(profile.sourceUrl, updated.sourceUrl)
        assertEquals("After", updated.name)
        assertEquals(true, updated.updateLocked)
        val restored = updated.withSkipiSettingsReadFromRawConfig()
        assertEquals(updated.name, restored.name)
        assertEquals(updated.updateLocked, restored.updateLocked)
    }

    @Test
    fun updateReadsSettingsFromChangedDocumentAndKeepsOtherProfileOrder() {
        val first = TrafficConfigState(4, "First", "[General]\n")
        val second = TrafficConfigState(9, "Second", "[General]\n")

        val updated = updateTrafficConfigProfile(listOf(first, second), profileId = 9) {
            it.copy(rawConfig = "[General]\n\n[SKIPI]\nprofile-name = Imported\nprofile-update-locked = true\n")
        }

        assertEquals(listOf(4, 9), updated.map(TrafficConfigState::id))
        assertEquals("Imported", updated[1].name)
        assertEquals(true, updated[1].updateLocked)
    }

    @Test
    fun committedRawEditRewritesKnownSkipiValuesAndKeepsExternalSectionsAndNeighbors() {
        val sibling = TrafficConfigState(4, "Sibling", "[General]\nloglevel = info\n")
        val originalRaw = """[General]
# external general comment
loglevel = warning

[Rule]
# external rule comment
DOMAIN-SUFFIX,example.net,DIRECT

[SKIPI]
# this inner comment is not part of the typed settings
profile-name = Before
profile-update-url = https://before.example/profile
profile-update-locked = false
fake-dns = false
future-skipi-option = discard-me

[Vendor]
# external vendor comment
vendor-option = preserve-me
""".trimIndent() + "\n"
        val profile = TrafficConfigState(
            id = 17,
            name = "Before",
            rawConfig = originalRaw,
            sourceUrl = "https://before.example/profile",
            lastUpdatedAtMillis = 1234L,
        ).withSkipiSettingsReadFromRawConfig()
        val editedRaw = """[General]
# external general comment
loglevel = debug

[Rule]
# external rule comment
DOMAIN-SUFFIX,example.net,DIRECT

[SKIPI]
# this newly edited inner comment is not retained
profile-name = After raw edit
profile-update-url = https://after.example/profile
profile-update-locked = yes
fake-dns = true
future-skipi-option = discard-me-too

[Vendor]
# external vendor comment
vendor-option = preserve-me
""".trimIndent() + "\n"

        val updated = updateTrafficConfigProfile(listOf(sibling, profile), profileId = profile.id) {
            it.copy(rawConfig = editedRaw)
        }
        val saved = updated.last()
        val skipiBody = saved.rawConfig.substringAfter("[SKIPI]\n").substringBefore("\n[Vendor]")

        assertEquals(listOf(4, 17), updated.map(TrafficConfigState::id))
        assertEquals(sibling, updated.first())
        assertEquals(1234L, saved.lastUpdatedAtMillis)
        assertEquals("After raw edit", saved.name)
        assertEquals("https://after.example/profile", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertTrue(saved.androidSettings.enableFakeDns)
        assertTrue(skipiBody.contains("fake-dns = true"))
        assertFalse(skipiBody.contains("future-skipi-option"))
        assertFalse(skipiBody.contains("newly edited inner comment"))
        assertTrue(saved.rawConfig.contains("# external general comment"))
        assertTrue(saved.rawConfig.contains("loglevel = debug"))
        assertTrue(saved.rawConfig.contains("# external rule comment"))
        assertTrue(saved.rawConfig.contains("# external vendor comment"))
        assertTrue(saved.rawConfig.contains("vendor-option = preserve-me"))
    }

    @Test
    fun unchangedOrUnknownUpdatePreservesOriginalCollection() {
        val profiles = listOf(TrafficConfigState(1, "One", "[General]\n"))

        assertSame(profiles, updateTrafficConfigProfile(profiles, 1) { it })
        assertSame(profiles, updateTrafficConfigProfile(profiles, 99) { it.copy(name = "Missing") })
    }

    @Test
    fun selectionAcceptsExistingIdAndIgnoresUnknownId() {
        val profiles = listOf(
            TrafficConfigState(2, "Two", "[General]\n"),
            TrafficConfigState(5, "Five", "[General]\n"),
        )

        assertEquals(5, selectTrafficConfigProfile(profiles, activeProfileId = 2, requestedProfileId = 5))
        assertEquals(2, selectTrafficConfigProfile(profiles, activeProfileId = 2, requestedProfileId = 99))
    }
}
