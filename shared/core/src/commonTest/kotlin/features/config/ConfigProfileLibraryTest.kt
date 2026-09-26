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
    fun removeRepairsSelectionAndNormalizeRepairsMissingSelection() {
        val library = ConfigProfileLibrary(
            selectedConfigId = 9,
            configs = listOf(ConfigProfile(1, "One", "a"), ConfigProfile(2, "Two", "b")),
        )

        assertEquals(1, ConfigProfileLibraries.normalize(library).selectedConfigId)
        assertEquals(2, ConfigProfileLibraries.remove(library.copy(selectedConfigId = 1), 1).selectedConfigId)
        assertEquals(null, ConfigProfileLibraries.remove(ConfigProfileLibrary(configs = listOf(ConfigProfile(1, "One", "a"))), 1).selectedConfigId)
    }

    @Test
    fun rejectsEmptyContentAndUnknownSelection() {
        assertFailsWith<IllegalArgumentException> {
            ConfigProfileLibraries.put(ConfigProfileLibrary(), "Empty", "  ")
        }
        assertFailsWith<IllegalArgumentException> {
            ConfigProfileLibraries.select(ConfigProfileLibrary(), 42)
        }
    }
}
