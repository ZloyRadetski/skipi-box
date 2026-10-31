// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopConfigLibraryImportTest {
    @Test
    fun documentFacadeUsesTheFirstMatchingRowFromTheAndroidImporter() {
        val active = ConfigProfile(7, "Active", "[General]\nloglevel = warning\n")
        val nameMatch = ConfigProfile(
            id = 20,
            name = "Incoming",
            content = "[General]\nloglevel = warning\n",
            sourceUrl = "https://old.example/profile",
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

        val imported = DesktopConfigLibraries.importDocument(
            library = library,
            content = """# name: Incoming
[General]
loglevel = debug
""",
            activate = true,
            fallbackName = "Imported",
            sourceUrl = "https://example.test/feed",
        )

        assertEquals(listOf(7, 20, 30), imported.configs.map(ConfigProfile::id))
        assertEquals("Incoming", imported.configs[1].name)
        assertEquals("https://example.test/feed", imported.configs[1].sourceUrl)
        assertEquals(true, imported.configs[1].updateLocked)
        assertEquals(111L, imported.configs[1].lastUpdatedAtMillis)
        assertEquals(laterUrlMatch, imported.configs[2])
        assertEquals(active.id, imported.selectedConfigId)
        assertEquals(41, imported.nextConfigId)
        assertEquals(listOf(active, nameMatch, laterUrlMatch), library.configs)
    }

    @Test
    fun documentImportUsesDesktopUserAgentFallbackAndPreservesExplicitBlank() {
        val fallback = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME)
        val emptyLibrary = DesktopConfigLibrary(nextConfigId = 25)

        val imported = DesktopConfigLibraries.importDocument(
            library = emptyLibrary,
            content = """# source comment
# name: Legacy named
[General]
loglevel = warning

[Vendor]
vendor-option = keep
""",
            activate = false,
            fallbackName = "Fallback",
            sourceUrl = " https://example.test/legacy ",
        )

        val legacy = imported.configs.single()
        assertEquals(25, legacy.id)
        assertEquals("Legacy named", legacy.name)
        assertEquals("https://example.test/legacy", legacy.sourceUrl)
        assertEquals(null, imported.selectedConfigId)
        assertEquals(26, imported.nextConfigId)
        assertTrue(legacy.content.contains("# source comment"))
        assertTrue(legacy.content.contains("vendor-option = keep"))
        assertTrue(legacy.content.contains("resource-user-agent = $fallback\n"))

        val withExplicitBlank = DesktopConfigLibraries.importDocument(
            library = imported,
            content = """# name: Explicit blank
[General]
loglevel = info

[SKIPI]
resource-user-agent =
""",
            activate = false,
            fallbackName = "Fallback",
        )

        assertEquals(listOf(25, 26), withExplicitBlank.configs.map(ConfigProfile::id))
        assertEquals("Explicit blank", withExplicitBlank.configs.last().name)
        assertTrue(withExplicitBlank.configs.last().content.contains("resource-user-agent = \n"))
        assertEquals(null, withExplicitBlank.selectedConfigId)
        assertEquals(27, withExplicitBlank.nextConfigId)
    }

    @Test
    fun explicitUrlCreateAlwaysAppendsEvenWhenUrlAndNameAlreadyExist() {
        val fallback = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME)
        val url = "https://example.test/same.conf"
        val existing = ConfigProfile(
            id = 5,
            name = "Same name",
            content = "[General]\nloglevel = warning\n",
            sourceUrl = url,
            updateLocked = true,
            lastUpdatedAtMillis = 100L,
        )
        val library = ConfigProfileLibrary(
            selectedConfigId = existing.id,
            configs = listOf(existing),
            nextConfigId = 29,
        )

        val created = DesktopConfigLibraries.create(
            library = library,
            profile = ConfigProfile(
                id = 0,
                name = "Same name",
                content = """# external source comment
[General]
loglevel = debug

[Vendor]
vendor-option = preserve
""",
                sourceUrl = url,
                lastUpdatedAtMillis = 200L,
            ),
        )

        assertEquals(listOf(5, 29), created.configs.map(ConfigProfile::id))
        assertEquals(existing, created.configs[0])
        assertEquals(existing.id, created.selectedConfigId)
        assertEquals(30, created.nextConfigId)
        assertEquals(url, created.configs[1].sourceUrl)
        assertEquals(200L, created.configs[1].lastUpdatedAtMillis)
        assertTrue(created.configs[1].content.contains("# external source comment"))
        assertTrue(created.configs[1].content.contains("vendor-option = preserve"))
        assertTrue(created.configs[1].content.contains("resource-user-agent = $fallback\n"))

        val createdWithExplicitBlank = DesktopConfigLibraries.create(
            library = created,
            profile = ConfigProfile(
                id = 0,
                name = "Blank user agent",
                content = """[General]
loglevel = info

[SKIPI]
resource-user-agent =
""",
                sourceUrl = url,
            ),
        )
        assertEquals(listOf(5, 29, 30), createdWithExplicitBlank.configs.map(ConfigProfile::id))
        assertEquals(existing.id, createdWithExplicitBlank.selectedConfigId)
        assertEquals(31, createdWithExplicitBlank.nextConfigId)
        assertTrue(createdWithExplicitBlank.configs.last().content.contains("resource-user-agent = \n"))
    }
}
