// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigProfileLibraryTest {
    @Test
    fun putReplacesByCaseInsensitiveSourceUrlAndPreservesMetadataDefaults() {
        val initial = ConfigProfileLibrary(configs = listOf(
            ConfigProfile(4, "Remote", "old", "https://example.com/config", updateLocked = true, lastUpdatedAtMillis = 20),
        ))

        val updated = ConfigProfileLibraries.put(
            library = initial,
            name = " New name ",
            content = "[Rule]",
            sourceUrl = " HTTPS://EXAMPLE.COM/CONFIG ",
        )

        assertEquals(4, updated.configs.single().id)
        assertEquals("New name", updated.configs.single().name)
        assertEquals("HTTPS://EXAMPLE.COM/CONFIG", updated.configs.single().sourceUrl)
        assertEquals(true, updated.configs.single().updateLocked)
        assertEquals(20, updated.configs.single().lastUpdatedAtMillis)
        assertEquals(4, updated.selectedConfigId)
    }

    @Test
    fun removeRepairsSelectionAndProtectsLastProfile() {
        val library = ConfigProfileLibrary(
            selectedConfigId = 9,
            configs = listOf(ConfigProfile(1, "One", "a"), ConfigProfile(2, "Two", "b")),
        )

        assertEquals(1, ConfigProfileLibraries.normalize(library).selectedConfigId)
        assertEquals(2, ConfigProfileLibraries.remove(library.copy(selectedConfigId = 1), 1).selectedConfigId)
        // Android oracle: deleteTrafficConfigProfile is a no-op when only one profile remains.
        val onlyProfile = ConfigProfileLibrary(
            selectedConfigId = 1,
            configs = listOf(ConfigProfile(1, "One", "a")),
        )
        assertEquals(onlyProfile, ConfigProfileLibraries.remove(onlyProfile, 1))
    }

    @Test
    fun rejectsEmptyContentAndIgnoresUnknownSelection() {
        assertFailsWith<IllegalArgumentException> {
            ConfigProfileLibraries.put(ConfigProfileLibrary(), "Empty", "  ")
        }
        // Android oracle: selectTrafficConfigProfile preserves the current ID for an unknown request.
        val library = ConfigProfileLibrary(
            selectedConfigId = 7,
            configs = listOf(ConfigProfile(7, "Current", "[General]")),
        )
        assertEquals(library, ConfigProfileLibraries.select(library, 42))
    }

    @Test
    fun legacyLibraryDerivesAndRetainsNextIdHighWaterAcrossDeletion() {
        val legacy = ConfigProfileLibrary(
            selectedConfigId = 41,
            configs = listOf(
                ConfigProfile(30, "Legacy default", "[General]"),
                ConfigProfile(41, "Work", "[Proxy]"),
            ),
        )

        val normalized = ConfigProfileLibraries.normalize(legacy)
        assertEquals(42, normalized.nextConfigId)

        val firstNew = ConfigProfileLibraries.put(normalized, "New", "[General]")
        assertEquals(42, firstNew.configs.last().id)
        assertEquals(43, firstNew.nextConfigId)

        val afterDelete = ConfigProfileLibraries.remove(firstNew, 42)
        assertEquals(43, afterDelete.nextConfigId)
        val secondNew = ConfigProfileLibraries.put(afterDelete, "Another", "[Rule]")
        assertEquals(43, secondNew.configs.last().id)
        assertEquals(44, secondNew.nextConfigId)
    }

}
