// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLocalProxyRawPortPersistenceTest {
    @Test
    fun rawPortRepresentationSurvivesSettingsJsonSaveReloadAndUnrelatedUpdate() = runBlocking {
        val directory = Files.createTempDirectory("skipi-desktop-port")
        val settingsPath = directory.resolve("settings.json")
        try {
            DesktopSettingsLibraries.save(
                settingsPath,
                DesktopAppSettings(
                    localProxyPort = 1_080,
                    localProxyPortRaw = "01080",
                ),
            ).getOrThrow()

            var settings = DesktopSettingsLibraries.load(settingsPath).getOrThrow()
            assertEquals(1_080, settings.localProxyPort)
            assertEquals("01080", settings.localProxyPortRaw)

            val repository = settingsRepository(settingsPath) { settings = it }
            assertEquals("01080", repository.state.value.localProxy?.port)

            repository.update { current ->
                current.copy(
                    localProxy = checkNotNull(current.localProxy).copy(port = "01081"),
                )
            }

            settings = DesktopSettingsLibraries.load(settingsPath).getOrThrow()
            assertEquals(1_081, settings.localProxyPort)
            assertEquals("01081", settings.localProxyPortRaw)
            assertEquals("01081", repository.state.value.localProxy?.port)

            repository.update { current ->
                current.copy(home = current.home.copy(showServerSearch = true))
            }

            settings = DesktopSettingsLibraries.load(settingsPath).getOrThrow()
            assertEquals(1_081, settings.localProxyPort)
            assertEquals("01081", settings.localProxyPortRaw)
            assertEquals("01081", repository.state.value.localProxy?.port)
        } finally {
            Files.deleteIfExists(settingsPath)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun legacyNumericSettingsKeepTheirExistingPortAndUseItsStringProjection() {
        val directory = Files.createTempDirectory("skipi-desktop-legacy-port")
        val settingsPath = directory.resolve("settings.json")
        try {
            Files.writeString(settingsPath, """{"localProxyPort":10808,"localHttpProxyPort":10809}""")

            val settings = DesktopSettingsLibraries.load(settingsPath).getOrThrow()
            val repository = settingsRepository(settingsPath) { }

            assertEquals(10_808, settings.localProxyPort)
            assertNull(settings.localProxyPortRaw)
            assertEquals("10808", repository.state.value.localProxy?.port)
        } finally {
            Files.deleteIfExists(settingsPath)
            Files.deleteIfExists(directory)
        }
    }

    private fun settingsRepository(
        settingsPath: java.nio.file.Path,
        publish: (DesktopAppSettings) -> Unit,
    ): DesktopSettingsRepository {
        var currentSettings = DesktopSettingsLibraries.load(settingsPath).getOrThrow()
        var servers = DesktopServerLibrary()
        var configs = DesktopConfigLibrary()
        return DesktopSettingsRepository(
            readSettings = { currentSettings },
            saveSettings = { updated -> DesktopSettingsLibraries.save(settingsPath, updated) },
            publishSettings = { updated -> currentSettings = updated; publish(updated) },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )
    }
}
