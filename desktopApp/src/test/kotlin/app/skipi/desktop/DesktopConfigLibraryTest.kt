package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopConfigLibraryTest {
    @Test fun stores_selects_and_removes_configs() {
        val path = Files.createTempDirectory("skipi-configs").resolve("configs.json")
        val first = DesktopConfigLibraries.put(DesktopConfigLibrary(), "Default", "[General]")
        val second = DesktopConfigLibraries.put(first, "Work", "[Rule]")
        DesktopConfigLibraries.save(path, second).getOrThrow()
        val restored = DesktopConfigLibraries.load(path).getOrThrow()
        assertEquals(2, restored.configs.size)
        assertEquals("Default", DesktopConfigLibraries.select(restored, 1).configs.first().name)
        assertEquals(1, DesktopConfigLibraries.remove(restored, 2).configs.size)
    }
}
