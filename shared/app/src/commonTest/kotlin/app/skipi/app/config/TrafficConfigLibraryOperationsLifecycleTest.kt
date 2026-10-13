// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficConfigLibraryOperationsLifecycleTest {
    @Test
    fun duplicateUsesAndroidTypedProfileWriterAndNeverUpsertsMatchingUrl() {
        val active = ConfigProfile(
            id = 7,
            name = "Active",
            content = "[General]\nloglevel = warning\n",
        )
        val source = ConfigProfile(
            id = 23,
            name = "Remote",
            content = """[General]
loglevel = info

[SKIPI]
profile-name = Remote
profile-update-url = https://example.test/remote
profile-update-locked = true
profile-auto-update = false
profile-update-interval = 48
resource-user-agent = source-agent
resource-auto-update = false
resource-update-interval = 72
future-skipi-option = ignored-by-typed-writer

[Vendor]
vendor-option = keep
""",
            sourceUrl = "https://example.test/remote",
            updateLocked = true,
            lastUpdatedAtMillis = 9_876L,
        )
        val library = ConfigProfileLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, source),
        )

        val duplicated = TrafficConfigLibraryOperations.duplicate(
            library = library,
            sourceProfileId = source.id,
            name = "Remote — копия",
        )
        val copy = duplicated.configs.last()

        assertEquals(listOf(7, 23, 24), duplicated.configs.map(ConfigProfile::id))
        assertEquals(active.id, duplicated.selectedConfigId)
        assertSame(active, duplicated.configs[0])
        assertSame(source, duplicated.configs[1])
        assertEquals("Remote — копия", copy.name)
        assertEquals("https://example.test/remote", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertEquals(25, duplicated.nextConfigId)
        assertTrue(copy.content.contains("profile-name = Remote — копия"))
        assertTrue(copy.content.contains("profile-auto-update = false"))
        assertTrue(copy.content.contains("profile-update-interval = 48"))
        assertTrue(copy.content.contains("resource-user-agent = source-agent"))
        assertTrue(copy.content.contains("resource-auto-update = false"))
        assertTrue(copy.content.contains("resource-update-interval = 72"))
        assertTrue(copy.content.contains("vendor-option = keep"))
    }

    @Test
    fun duplicateUsesStoredEnvelopeWhenSerializedBasicsAreStale() {
        val source = ConfigProfile(
            id = 23,
            name = "Current profile name",
            content = """[General]
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
        )
        val library = ConfigProfileLibrary(
            selectedConfigId = source.id,
            configs = listOf(source),
        )

        val duplicated = TrafficConfigLibraryOperations.duplicate(
            library = library,
            sourceProfileId = source.id,
            name = "Current profile name copy",
        )
        val copy = duplicated.configs.last()

        assertEquals("Current profile name copy", copy.name)
        assertEquals("https://current.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.content.contains("profile-name = Current profile name copy"))
        assertTrue(copy.content.contains("profile-update-url = https://current.example/profile"))
        assertTrue(copy.content.contains("profile-update-locked = true"))
        assertTrue(copy.content.contains("resource-user-agent = profile-agent"))
        assertSame(source, duplicated.configs.first())
    }

    @Test
    fun duplicateUsesClickedSnapshotWhenSameIdHasNewerStoredRecord() {
        val clicked = ConfigProfile(
            id = 23,
            name = "Clicked profile",
            content = "[General]\nloglevel = warning\n\n[Vendor]\nrevision = clicked\n",
            sourceUrl = "https://clicked.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 10L,
        )
        val newerStored = ConfigProfile(
            id = 23,
            name = "Newer stored profile",
            content = "[General]\nloglevel = info\n\n[Vendor]\nrevision = newer\n",
            sourceUrl = "https://newer.example/profile",
            updateLocked = false,
            lastUpdatedAtMillis = 20L,
        )
        val library = ConfigProfileLibrary(
            selectedConfigId = 7,
            configs = listOf(
                ConfigProfile(7, "Active", "[General]\n"),
                newerStored,
            ),
            nextConfigId = 24,
        )

        val duplicated = TrafficConfigLibraryOperations.duplicate(
            library = library,
            sourceProfile = clicked,
            name = "Clicked profile copy",
        )
        val copy = duplicated.configs.last()

        assertEquals(listOf(7, 23, 24), duplicated.configs.map(ConfigProfile::id))
        assertEquals(7, duplicated.selectedConfigId)
        assertSame(newerStored, duplicated.configs[1])
        assertEquals("Clicked profile copy", copy.name)
        assertEquals("https://clicked.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.content.contains("revision = clicked"))
        assertTrue(copy.content.contains("profile-update-url = https://clicked.example/profile"))
    }

    @Test
    fun duplicateUsesClickedSnapshotEvenIfSourceWasRemovedFromLibrary() {
        val clicked = ConfigProfile(
            id = 23,
            name = "Clicked profile",
            content = "[General]\nloglevel = warning\n\n[Vendor]\nrevision = clicked\n",
            sourceUrl = "https://clicked.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 10L,
        )
        val remaining = ConfigProfile(7, "Remaining", "[General]\n")
        val library = ConfigProfileLibrary(
            selectedConfigId = remaining.id,
            configs = listOf(remaining),
            nextConfigId = 24,
        )

        val duplicated = TrafficConfigLibraryOperations.duplicate(
            library = library,
            sourceProfile = clicked,
            name = "Clicked profile copy",
        )
        val copy = duplicated.configs.last()

        assertEquals(listOf(7, 24), duplicated.configs.map(ConfigProfile::id))
        assertEquals(7, duplicated.selectedConfigId)
        assertEquals(25, duplicated.nextConfigId)
        assertEquals("https://clicked.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.content.contains("revision = clicked"))
    }
}
