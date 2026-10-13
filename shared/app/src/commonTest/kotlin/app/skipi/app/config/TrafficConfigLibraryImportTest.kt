// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrafficConfigLibraryImportTest {
    @Test
    fun documentImportUsesFirstMatchingRowAndPreservesItsPersistedEnvelope() {
        val active = ConfigProfile(7, "Active", "[General]\nloglevel = warning\n")
        val nameMatch = ConfigProfile(
            id = 20,
            name = "Incoming",
            content = """[General]
loglevel = warning

[SKIPI]
profile-name = Stale serialized name
profile-update-url = https://stale.example/profile
profile-update-locked = false
resource-user-agent = existing-profile-agent

[Vendor]
# preserve this source comment
vendor-option = keep
""",
            sourceUrl = "https://outer.example/old-profile",
            updateLocked = true,
            lastUpdatedAtMillis = 111L,
        )
        val laterUrlMatch = ConfigProfile(
            id = 30,
            name = "URL target",
            content = "[General]\nloglevel = info\n",
            sourceUrl = "https://example.test/feed",
            lastUpdatedAtMillis = 222L,
        )
        val library = ConfigProfileLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, nameMatch, laterUrlMatch),
            nextConfigId = 41,
        )

        val result = TrafficConfigLibraryOperations.importDocument(
            library = library,
            content = """# name: Incoming
[General]
loglevel = debug

[Vendor]
# imported vendor comment
vendor-option = imported
""",
            activate = true,
            fallbackName = "Imported",
            sourceUrl = " https://EXAMPLE.test/feed ",
            resourceUserAgentFallback = "Skipi/test/Desktop",
        )

        assertEquals(listOf(7, 20, 30), result.configs.map(ConfigProfile::id))
        assertEquals(active, result.configs[0])
        val updated = result.configs[1]
        assertEquals("Incoming", updated.name)
        assertEquals("https://EXAMPLE.test/feed", updated.sourceUrl)
        assertEquals(true, updated.updateLocked)
        assertEquals(111L, updated.lastUpdatedAtMillis)
        assertTrue(updated.content.contains("loglevel = debug"))
        assertTrue(updated.content.contains("# imported vendor comment"))
        assertTrue(updated.content.contains("vendor-option = imported"))
        assertTrue(updated.content.contains("resource-user-agent = existing-profile-agent"))
        assertEquals(laterUrlMatch, result.configs[2])
        assertEquals(active.id, result.selectedConfigId)
        assertEquals(41, result.nextConfigId)
        assertEquals(listOf(active, nameMatch, laterUrlMatch), library.configs)
    }

    @Test
    fun documentImportRejectsMalformedContentThroughTheSharedAndroidImporter() {
        val library = ConfigProfileLibrary(
            selectedConfigId = 7,
            configs = listOf(ConfigProfile(7, "Active", "[General]\nloglevel = warning\n")),
            nextConfigId = 8,
        )

        assertFailsWith<IllegalArgumentException> {
            TrafficConfigLibraryOperations.importDocument(
                library = library,
                content = "[Rule]\nFINAL,DIRECT\nFINAL,PROXY\n",
                activate = true,
            )
        }
        assertEquals(listOf(7), library.configs.map(ConfigProfile::id))
        assertEquals(8, library.nextConfigId)
    }
}
