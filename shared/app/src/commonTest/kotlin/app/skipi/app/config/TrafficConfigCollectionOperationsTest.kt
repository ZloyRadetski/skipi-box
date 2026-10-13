// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import app.skipi.app.model.TrafficConfigRecord
import features.config.SkipiPerAppModeGlobal
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
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

    @Test
    fun createAppendsWithCallerIdAndKeepsCallerProvidedResourceDefaults() {
        val existing = TrafficConfigState(4, "Existing", "[General]\n")
        val resourceSettings = features.config.TrafficConfigResourceSettings(
            userAgent = "profile-agent",
            autoUpdate = false,
            updateInterval = "36",
        )

        val created = createTrafficConfigProfile(
            profiles = listOf(existing),
            nextId = 12,
            name = "Imported",
            rawDocument = "[General]\nloglevel = warning\n",
            resourceSettings = resourceSettings,
            sourceUrl = "https://example.test/profile",
            lastUpdatedAtMillis = 123L,
        )

        assertEquals(listOf(4, 12), created.profiles.map(TrafficConfigState::id))
        assertSame(existing, created.profiles.first())
        assertEquals(13, created.nextId)
        assertEquals(12, created.affectedProfileId)
        assertEquals("Imported", created.profiles.last().name)
        assertEquals("https://example.test/profile", created.profiles.last().sourceUrl)
        assertEquals(123L, created.profiles.last().lastUpdatedAtMillis)
        assertEquals(resourceSettings, created.profiles.last().resourceSettings)
    }

    @Test
    fun duplicateAppendsFreshIdCopiesPortableProfileAndResetsRefreshTimestamp() {
        val source = TrafficConfigState(
            id = 7,
            name = "Remote",
            rawConfig = "[General]\nloglevel = warning\n\n[Vendor]\noption = keep\n",
            sourceUrl = "https://example.test/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 987L,
            autoUpdate = true,
            updateInterval = "48",
            resourceSettings = features.config.TrafficConfigResourceSettings(
                userAgent = "profile-agent",
                autoUpdate = false,
                updateInterval = "72",
            ),
        ).withSkipiSettingsInRawConfig()
        val sibling = TrafficConfigState(3, "Sibling", "[General]\n")

        val duplicated = duplicateTrafficConfigProfile(
            profiles = listOf(source, sibling),
            nextId = 15,
            source = source,
            name = "Remote copy",
        )
        val copy = duplicated.profiles.last()

        assertEquals(listOf(7, 3, 15), duplicated.profiles.map(TrafficConfigState::id))
        assertSame(source, duplicated.profiles[0])
        assertSame(sibling, duplicated.profiles[1])
        assertEquals(16, duplicated.nextId)
        assertEquals(15, duplicated.affectedProfileId)
        assertEquals("Remote copy", copy.name)
        assertEquals(source.sourceUrl, copy.sourceUrl)
        assertEquals(source.updateLocked, copy.updateLocked)
        assertEquals(source.autoUpdate, copy.autoUpdate)
        assertEquals(source.updateInterval, copy.updateInterval)
        assertEquals(source.resourceSettings, copy.resourceSettings)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.rawConfig.contains("option = keep"))
        assertTrue(copy.rawConfig.contains("profile-name = Remote copy"))
    }

    @Test
    fun duplicateUsesCurrentTypedProfileEnvelopeWhenSerializedBasicsAreStale() {
        val source = TrafficConfigState(
            id = 23,
            name = "Current profile name",
            rawConfig = """[General]
loglevel = info

[SKIPI]
profile-name = Old serialized name
profile-update-url = https://old.example/profile
profile-update-locked = false
profile-auto-update = false
resource-user-agent = profile-agent
""".trimIndent() + "\n",
            sourceUrl = "https://current.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 9_876L,
            resourceSettings = features.config.TrafficConfigResourceSettings(userAgent = "profile-agent"),
        )

        val duplicated = duplicateTrafficConfigProfile(
            profiles = listOf(source),
            nextId = 24,
            source = source,
            name = "Current profile name copy",
        )
        val copy = duplicated.profiles.last()

        assertEquals("Current profile name copy", copy.name)
        assertEquals("https://current.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.rawConfig.contains("profile-name = Current profile name copy"))
        assertTrue(copy.rawConfig.contains("profile-update-url = https://current.example/profile"))
        assertTrue(copy.rawConfig.contains("profile-update-locked = true"))
        assertTrue(copy.rawConfig.contains("resource-user-agent = profile-agent"))
        assertSame(source, duplicated.profiles.first())
    }

    @Test
    fun duplicateUsesClickedSnapshotWhenCurrentListHasNewerRecordWithSameId() {
        val clicked = TrafficConfigState(
            id = 23,
            name = "Clicked profile",
            rawConfig = "[General]\nloglevel = warning\n\n[Vendor]\nrevision = clicked\n",
            sourceUrl = "https://clicked.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 10L,
        )
        val newerStored = TrafficConfigState(
            id = 23,
            name = "Newer stored profile",
            rawConfig = "[General]\nloglevel = info\n\n[Vendor]\nrevision = newer\n",
            sourceUrl = "https://newer.example/profile",
            updateLocked = false,
            lastUpdatedAtMillis = 20L,
        )

        val duplicated = duplicateTrafficConfigProfile(
            profiles = listOf(newerStored),
            nextId = 24,
            source = clicked,
            name = "Clicked profile copy",
        )
        val copy = duplicated.profiles.last()

        assertSame(newerStored, duplicated.profiles.first())
        assertEquals(23, duplicated.profiles.first().id)
        assertEquals("Clicked profile copy", copy.name)
        assertEquals("https://clicked.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.rawConfig.contains("revision = clicked"))
        assertTrue(copy.rawConfig.contains("profile-update-url = https://clicked.example/profile"))
    }

    @Test
    fun duplicateStillAppendsClickedSnapshotAfterSourceWasRemoved() {
        val clicked = TrafficConfigState(
            id = 23,
            name = "Clicked profile",
            rawConfig = "[General]\nloglevel = warning\n\n[Vendor]\nrevision = clicked\n",
            sourceUrl = "https://clicked.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 10L,
        )
        val remaining = TrafficConfigState(7, "Remaining", "[General]\n")

        val duplicated = duplicateTrafficConfigProfile(
            profiles = listOf(remaining),
            nextId = 24,
            source = clicked,
            name = "Clicked profile copy",
        )
        val copy = duplicated.profiles.last()

        assertEquals(listOf(7, 24), duplicated.profiles.map(TrafficConfigState::id))
        assertEquals(25, duplicated.nextId)
        assertEquals("https://clicked.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertTrue(copy.rawConfig.contains("revision = clicked"))
    }

    @Test
    fun deleteProtectsLastProfileAndRepairsActiveSelectionInOriginalOrder() {
        val first = TrafficConfigState(8, "First", "[General]\n")
        val active = TrafficConfigState(3, "Active", "[General]\n")
        val last = TrafficConfigState(12, "Last", "[General]\n")
        val profiles = listOf(first, active, last)

        val onlyProfile = listOf(first)
        val lastDelete = deleteTrafficConfigProfile(onlyProfile, activeProfileId = 8, profileId = 8)
        assertSame(onlyProfile, lastDelete.profiles)
        assertEquals(8, lastDelete.activeProfileId)

        val missingDelete = deleteTrafficConfigProfile(profiles, activeProfileId = 3, profileId = 99)
        assertSame(profiles, missingDelete.profiles)
        assertEquals(3, missingDelete.activeProfileId)

        val activeDelete = deleteTrafficConfigProfile(profiles, activeProfileId = 3, profileId = 3)
        assertEquals(listOf(8, 12), activeDelete.profiles.map(TrafficConfigState::id))
        assertEquals(8, activeDelete.activeProfileId)

        val inactiveDelete = deleteTrafficConfigProfile(profiles, activeProfileId = 3, profileId = 12)
        assertEquals(listOf(8, 3), inactiveDelete.profiles.map(TrafficConfigState::id))
        assertEquals(3, inactiveDelete.activeProfileId)
    }
}
