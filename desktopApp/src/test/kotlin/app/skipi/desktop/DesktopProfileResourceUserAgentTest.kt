// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopProfileResourceUserAgentTest {
    @Test
    fun desktopProfileUserAgentUsesTheDesktopLabelAndProvidedVersion() {
        assertEquals(
            "Skipi/1.2.3/Desktop",
            versionedDesktopProfileResourceUserAgent("1.2.3"),
        )
    }

    @Test
    fun editorSaveUsesProvidedDesktopUserAgentWhenRawKeyIsMissing() {
        val fallbackUserAgent = "Skipi/9.8.7/Desktop"
        val result = applyDesktopConfigEditorSave(
            library = ConfigProfileLibrary(),
            id = null,
            lastUpdatedAtMillis = 0L,
            name = "New profile",
            content = "[General]\nloglevel = info\n",
            sourceUrl = "",
            updateLocked = false,
            resourceUserAgentFallback = fallbackUserAgent,
        )
        val saved = assertIs<DesktopConfigEditorSaveResult.Saved>(result).library.configs.single()
        val state = saved.toTrafficConfigState()

        assertEquals(fallbackUserAgent, state.resourceSettings.userAgent)
        assertTrue(saved.content.contains("resource-user-agent = $fallbackUserAgent"))
    }

    @Test
    fun editorSaveKeepsAnExplicitRawUserAgentOverTheDesktopFallback() {
        val fallbackUserAgent = "Skipi/9.8.7/Desktop"
        val existing = ConfigProfile(
            id = 23,
            name = "Existing profile",
            content = "[General]\nloglevel = info\n",
            lastUpdatedAtMillis = 23L,
        )
        val result = applyDesktopConfigEditorSave(
            library = ConfigProfileLibrary(configs = listOf(existing)),
            id = existing.id,
            lastUpdatedAtMillis = existing.lastUpdatedAtMillis,
            name = existing.name,
            content = "[General]\nloglevel = info\n\n[SKIPI]\nresource-user-agent = Imported Custom Agent\n",
            sourceUrl = existing.sourceUrl,
            updateLocked = existing.updateLocked,
            resourceUserAgentFallback = fallbackUserAgent,
        )
        val saved = assertIs<DesktopConfigEditorSaveResult.Saved>(result).library.configs.single()

        assertEquals("Imported Custom Agent", saved.toTrafficConfigState().resourceSettings.userAgent)
        assertTrue(saved.content.contains("resource-user-agent = Imported Custom Agent"))
    }

    @Test
    fun editorSaveKeepsAnExplicitEmptyRawUserAgentEmpty() {
        val fallbackUserAgent = "Skipi/9.8.7/Desktop"
        val existing = ConfigProfile(
            id = 24,
            name = "Existing profile",
            content = "[General]\nloglevel = info\n",
            lastUpdatedAtMillis = 24L,
        )
        val result = applyDesktopConfigEditorSave(
            library = ConfigProfileLibrary(configs = listOf(existing)),
            id = existing.id,
            lastUpdatedAtMillis = existing.lastUpdatedAtMillis,
            name = existing.name,
            content = "[General]\nloglevel = info\n\n[SKIPI]\nresource-user-agent = \n",
            sourceUrl = existing.sourceUrl,
            updateLocked = existing.updateLocked,
            resourceUserAgentFallback = fallbackUserAgent,
        )
        val saved = assertIs<DesktopConfigEditorSaveResult.Saved>(result).library.configs.single()

        assertEquals("", saved.toTrafficConfigState().resourceSettings.userAgent)
        assertTrue(saved.content.contains("resource-user-agent = \n"))
    }

    @Test
    fun defaultEditorSaveUserAgentUsesGeneratedProjectVersion() {
        val result = applyDesktopConfigEditorSave(
            library = ConfigProfileLibrary(),
            id = null,
            lastUpdatedAtMillis = 0L,
            name = "New profile",
            content = "[General]\nloglevel = info\n",
            sourceUrl = "",
            updateLocked = false,
        )
        val saved = assertIs<DesktopConfigEditorSaveResult.Saved>(result).library.configs.single()

        assertEquals(
            "Skipi/${ProjectInfo.VERSION_NAME}/Desktop",
            saved.toTrafficConfigState().resourceSettings.userAgent,
        )
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
