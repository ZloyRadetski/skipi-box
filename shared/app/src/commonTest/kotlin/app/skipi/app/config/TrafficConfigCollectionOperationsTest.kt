// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TrafficConfigCollectionOperationsTest {
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
