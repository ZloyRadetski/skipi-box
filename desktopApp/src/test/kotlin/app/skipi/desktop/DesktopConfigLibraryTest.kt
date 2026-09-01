package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
