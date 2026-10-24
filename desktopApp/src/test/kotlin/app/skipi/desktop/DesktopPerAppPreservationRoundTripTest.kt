// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.app.config.toTrafficConfigState
import features.config.ConfigProfile
import features.config.SkipiPerAppModeBlacklist
import features.config.SkipiPerAppModeWhitelist
import features.config.TrafficConfigState
import features.config.skipiSectionValues
import features.config.withSkipiSettingsInRawConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopPerAppPreservationRoundTripTest {
    @Test
    fun profileEditPersistsModeAndOpaqueIdsWithoutChangingSelectedProfile() {
        val tempDirectory = Files.createTempDirectory("desktop-per-app-preservation-")
        try {
            val configsPath = tempDirectory.resolve("configs.json")
            val initial = libraryFixture()
            DesktopConfigLibraries.save(configsPath, initial).getOrThrow()
            val repository = DesktopTrafficConfigRepository(
                initialLibrary = initial,
                readLibrary = { DesktopConfigLibraries.load(configsPath).getOrThrow() },
                saveLibrary = { DesktopConfigLibraries.save(configsPath, it) },
                publishLibrary = {},
            )
            val store = DesktopTrafficConfigEditorProfileStore(
                repository = repository,
                resourceUserAgentFallback = ResourceUserAgentFallback,
            )
            val controller = TrafficConfigEditorController(store)

            assertIs<TrafficConfigEditorResult.Saved>(
                controller.setProxyAppListMode(TargetProfileId, SkipiPerAppModeWhitelist),
            )
            assertIs<TrafficConfigEditorResult.Saved>(
                controller.setProxyAppListSelection(
                    profileId = TargetProfileId,
                    selectionKeys = listOf("0:com.example.added"),
                    isChecked = true,
                ),
            )

            val restored = DesktopConfigLibraries.load(configsPath).getOrThrow()
            val restoredTarget = restored.configs.single { it.id == TargetProfileId }
            val restoredState = restoredTarget.toTrafficConfigState(ResourceUserAgentFallback)
            val expectedIds = listOf(
                "legacy.unknown",
                "0:com.example.present",
                "10:com.example.other-user",
                "0:com.example.added",
            )

            assertEquals(SelectedSiblingId, restored.selectedConfigId)
            assertEquals(initial.nextConfigId, restored.nextConfigId)
            assertEquals(initial.configs.map(ConfigProfile::id), restored.configs.map(ConfigProfile::id))
            assertEquals(initial.configs.single { it.id == SelectedSiblingId }, restored.configs.single { it.id == SelectedSiblingId })
            assertEquals(SkipiPerAppModeWhitelist, restoredState.proxyAppListMode)
            assertEquals(expectedIds, restoredState.proxyAppListSelectedApps)
            assertEquals(listOf("whitelist"), restoredTarget.content.skipiSectionValues()["per-app-mode"])
            assertEquals(expectedIds, restoredTarget.content.skipiSectionValues()["per-app-package"])
            assertEquals("system", restoredTarget.content.substringAfter("dns-server = ").lineSequence().first())
        } finally {
            Files.walk(tempDirectory).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder<Path>()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun libraryFixture(): DesktopConfigLibrary {
        val targetState = TrafficConfigState(
            id = TargetProfileId,
            name = "Target profile",
            rawConfig = """
                [General]
                dns-server = system
            """.trimIndent(),
            proxyAppListMode = SkipiPerAppModeBlacklist,
            proxyAppListSelectedApps = listOf(
                "legacy.unknown",
                "0:com.example.present",
                "10:com.example.other-user",
            ),
        ).withSkipiSettingsInRawConfig()
        val siblingState = TrafficConfigState(
            id = SelectedSiblingId,
            name = "Selected sibling",
            rawConfig = "[General]\ndns-server = system",
        ).withSkipiSettingsInRawConfig()
        return DesktopConfigLibrary(
            selectedConfigId = SelectedSiblingId,
            configs = listOf(
                ConfigProfile(
                    id = TargetProfileId,
                    name = targetState.name,
                    content = targetState.rawConfig,
                    sourceUrl = "https://example.test/target.conf",
                    updateLocked = true,
                    lastUpdatedAtMillis = 42L,
                ),
                ConfigProfile(
                    id = SelectedSiblingId,
                    name = siblingState.name,
                    content = siblingState.rawConfig,
                ),
            ),
            nextConfigId = 12,
        )
    }

    private companion object {
        const val TargetProfileId = 7
        const val SelectedSiblingId = 3
        const val ResourceUserAgentFallback = "Skipi/test/Desktop"
    }
}
