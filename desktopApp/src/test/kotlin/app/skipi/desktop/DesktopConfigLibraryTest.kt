package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopConfigLibraryTest {
    @Test fun stores_selects_and_removes_configs() {
        val path = Files.createTempDirectory("skipi-configs").resolve("configs.json")
        val first = DesktopConfigLibraries.put(
            DesktopConfigLibrary(),
            "Default",
            "[General]",
            sourceUrl = "https://example.com/default.conf",
            lastUpdatedAtMillis = 123L,
        )
        val second = DesktopConfigLibraries.put(first, "Work", "[Rule]")
        DesktopConfigLibraries.save(path, second).getOrThrow()
        val persistedJson = Files.readString(path)
        assertTrue(persistedJson.contains("\"selectedConfigId\""))
        assertTrue(persistedJson.contains("\"configs\""))
        assertTrue(persistedJson.contains("\"lastUpdatedAtMillis\""))
        val restored = DesktopConfigLibraries.load(path).getOrThrow()
        assertEquals(2, restored.configs.size)
        assertEquals("Default", DesktopConfigLibraries.select(restored, 1).configs.first().name)
        assertEquals("https://example.com/default.conf", restored.configs.first().sourceUrl)
        val updated = DesktopConfigLibraries.update(
            restored,
            id = 1,
            name = "Home",
            content = "[General]\nskipi-show-on-home = true",
            sourceUrl = "",
            updateLocked = true,
            lastUpdatedAtMillis = 456L,
        )
        assertEquals("Home", updated.configs.first().name)
        assertEquals(true, updated.configs.first().updateLocked)
        assertEquals(456L, updated.configs.first().lastUpdatedAtMillis)
        assertEquals(1, DesktopConfigLibraries.remove(restored, 2).configs.size)
    }

    @Test
    fun updatingOrImportingDoesNotSwitchActiveConfigAndRemovingItSelectsRemaining() {
        val first = DesktopConfigLibraries.put(DesktopConfigLibrary(), "Active", "[General]")
        val second = DesktopConfigLibraries.put(first, "Remote", "[Rule]", sourceUrl = "https://example.com/config")

        assertEquals(first.selectedConfigId, second.selectedConfigId)

        val refreshed = DesktopConfigLibraries.update(
            library = second,
            id = requireNotNull(second.configs.firstOrNull { it.name == "Remote" }?.id),
            name = "Remote",
            content = "[Rule]\nFINAL,PROXY",
            sourceUrl = "https://example.com/config",
            updateLocked = false,
            lastUpdatedAtMillis = 1L,
        )
        assertEquals(first.selectedConfigId, refreshed.selectedConfigId)

        val remaining = DesktopConfigLibraries.remove(refreshed, requireNotNull(first.selectedConfigId))
        assertEquals(1, remaining.configs.size)
        assertEquals(remaining.configs.single().id, remaining.selectedConfigId)
    }

    @Test
    fun legacyConfigsJsonKeepsIdsOrderSelectionAndOptionalFieldsAcrossRoundTrip() {
        val path = Files.createTempDirectory("skipi-legacy-configs").resolve("configs.json")
        val fixture = requireNotNull(javaClass.getResourceAsStream("/fixtures/desktop-legacy/configs.json"))
        fixture.use { Files.copy(it, path) }

        val loaded = DesktopConfigLibraries.load(path).getOrThrow()
        assertEquals(41, loaded.selectedConfigId)
        assertEquals(listOf(30, 41), loaded.configs.map { it.id })
        assertEquals("Legacy default", loaded.configs[0].name)
        assertEquals("https://example.com/work.conf", loaded.configs[1].sourceUrl)
        assertTrue(loaded.configs[1].updateLocked)
        assertEquals(1_700_000_000_123L, loaded.configs[1].lastUpdatedAtMillis)

        DesktopConfigLibraries.save(path, loaded).getOrThrow()
        val restored = DesktopConfigLibraries.load(path).getOrThrow()
        assertEquals(loaded, restored)
        val savedJson = Files.readString(path)
        assertTrue(savedJson.contains("\"selectedConfigId\""))
        assertTrue(savedJson.contains("\"configs\""))
        assertFalse(savedJson.contains("futureConfigField"))
    }

    @Test
    fun clearingSelectionAndRemovingSelectedConfigUseSharedLibraryOperations() {
        val first = DesktopConfigLibraries.put(DesktopConfigLibrary(), "First", "[General]")
        val second = DesktopConfigLibraries.put(first, "Second", "[Rule]")
        val selected = DesktopConfigLibraries.select(second, 2)

        assertEquals(2, selected.selectedConfigId)
        assertEquals(null, DesktopConfigLibraries.select(selected, null).selectedConfigId)
        val afterDelete = DesktopConfigLibraries.remove(selected, 2)
        assertEquals(listOf(1), afterDelete.configs.map { it.id })
        assertEquals(1, afterDelete.selectedConfigId)
    }
}
