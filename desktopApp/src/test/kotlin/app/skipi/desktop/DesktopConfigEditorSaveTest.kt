// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopConfigEditorSaveTest {
    @Test
    fun newEditorSaveNormalizesContentAndKeepsExistingLibrarySelection() {
        val active = ConfigProfile(
            id = 7,
            name = "Active profile",
            content = "[General]\nloglevel = info\n",
            lastUpdatedAtMillis = 700L,
        )
        val originalLibrary = ConfigProfileLibrary(
            selectedConfigId = active.id,
            configs = listOf(active),
        )
        val rawContent = """# external file comment
[General]
# external section comment
loglevel = warning

[SKIPI]
# comment inside SKIPI is removed by the shared writer
profile-auto-update = true
profile-update-interval = 18
fake-dns = true
resource-auto-update = false
future-skipi-option = discard-me

[Vendor]
# external vendor comment
vendor-option = preserve-me
"""

        val result = applyDesktopConfigEditorSave(
            library = originalLibrary,
            id = null,
            lastUpdatedAtMillis = 0L,
            name = "New profile",
            content = rawContent,
            sourceUrl = "https://new.example/profile",
            updateLocked = true,
        )

        val updatedLibrary = assertIs<DesktopConfigEditorSaveResult.Saved>(result).library
        val created = updatedLibrary.configs.single { it.id != active.id }
        val state = created.toTrafficConfigState()

        assertEquals(active.id, updatedLibrary.selectedConfigId)
        assertEquals(listOf(active.id, active.id + 1), updatedLibrary.configs.map(ConfigProfile::id))
        assertEquals(active, updatedLibrary.configs.first())
        assertEquals("New profile", created.name)
        assertEquals("https://new.example/profile", created.sourceUrl)
        assertTrue(created.updateLocked)
        assertEquals(0L, created.lastUpdatedAtMillis)
        assertTrue(state.autoUpdate)
        assertEquals("18", state.updateInterval)
        assertTrue(state.androidSettings.enableFakeDns)
        assertFalse(state.resourceSettings.autoUpdate)
        assertTrue(created.content.contains("# external file comment"))
        assertTrue(created.content.contains("# external section comment"))
        assertTrue(created.content.contains("# external vendor comment"))
        assertTrue(created.content.contains("vendor-option = preserve-me"))
        assertFalse(created.content.contains("future-skipi-option"))
        assertFalse(created.content.contains("comment inside SKIPI"))
    }

    @Test
    fun existingEditorSaveKeepsIdentityTimestampPositionSelectionAndNeighbor() {
        val neighbor = ConfigProfile(
            id = 5,
            name = "Neighbor",
            content = "[General]\nloglevel = error\n",
            lastUpdatedAtMillis = 50L,
        )
        val edited = ConfigProfile(
            id = 23,
            name = "Old profile",
            content = """[General]
loglevel = info

[SKIPI]
profile-auto-update = true
profile-update-interval = 12
fake-dns = true
future-skipi-option = discard-me

[Vendor]
# original vendor comment
vendor-option = original
""",
            sourceUrl = "https://old.example/profile",
            updateLocked = false,
            lastUpdatedAtMillis = 9876L,
        )
        val originalLibrary = ConfigProfileLibrary(
            selectedConfigId = neighbor.id,
            configs = listOf(neighbor, edited),
        )
        val changedContent = """# keep external comment
[General]
loglevel = warning

[SKIPI]
# stale inner comment should be dropped
profile-auto-update = false
profile-update-interval = 36
fake-dns = false
resource-auto-update = false
future-skipi-option = discard-me

[Vendor]
# keep vendor comment
vendor-option = edited
"""

        val result = applyDesktopConfigEditorSave(
            library = originalLibrary,
            id = edited.id,
            lastUpdatedAtMillis = edited.lastUpdatedAtMillis,
            name = "Edited profile",
            content = changedContent,
            sourceUrl = "https://edited.example/profile",
            updateLocked = true,
        )

        val updatedLibrary = assertIs<DesktopConfigEditorSaveResult.Saved>(result).library
        val updated = updatedLibrary.configs[1]
        val state = updated.toTrafficConfigState()

        assertEquals(neighbor.id, updatedLibrary.selectedConfigId)
        assertEquals(listOf(neighbor.id, edited.id), updatedLibrary.configs.map(ConfigProfile::id))
        assertEquals(neighbor, updatedLibrary.configs.first())
        assertEquals(edited.id, updated.id)
        assertEquals(edited.lastUpdatedAtMillis, updated.lastUpdatedAtMillis)
        assertEquals("Edited profile", updated.name)
        assertEquals("https://edited.example/profile", updated.sourceUrl)
        assertTrue(updated.updateLocked)
        assertFalse(state.autoUpdate)
        assertEquals("36", state.updateInterval)
        assertFalse(state.androidSettings.enableFakeDns)
        assertFalse(state.resourceSettings.autoUpdate)
        assertTrue(updated.content.contains("# keep external comment"))
        assertTrue(updated.content.contains("# keep vendor comment"))
        assertTrue(updated.content.contains("vendor-option = edited"))
        assertFalse(updated.content.contains("future-skipi-option"))
        assertFalse(updated.content.contains("stale inner comment"))
        assertEquals(edited, originalLibrary.configs.last())
    }

    @Test
    fun malformedEditorContentIsRejectedWithoutChangingTheLibrary() {
        val original = ConfigProfile(
            id = 10,
            name = "Keep me",
            content = "[Rule]\nFINAL,PROXY\n",
            sourceUrl = "https://keep.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 1010L,
        )
        val library = ConfigProfileLibrary(selectedConfigId = original.id, configs = listOf(original))

        val result = applyDesktopConfigEditorSave(
            library = library,
            id = original.id,
            lastUpdatedAtMillis = original.lastUpdatedAtMillis,
            name = "Should not be committed",
            content = "[Rule]\nFINAL,PROXY\nFINAL,DIRECT\n",
            sourceUrl = "https://changed.example/profile",
            updateLocked = false,
        )

        assertIs<DesktopConfigEditorSaveResult.Rejected>(result)
        assertEquals(original, library.configs.single())
        assertEquals(original.id, library.selectedConfigId)
    }
}

private fun ConfigProfile.toTrafficConfigState(): TrafficConfigState = TrafficConfigState(
    id = id,
    name = name,
    rawConfig = content,
    sourceUrl = sourceUrl,
    updateLocked = updateLocked,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
).withSkipiSettingsReadFromRawConfig()
