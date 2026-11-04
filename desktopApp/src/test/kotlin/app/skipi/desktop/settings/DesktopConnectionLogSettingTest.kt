// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.settings.AppearanceSettingsCapabilities
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopConnectionLogSettingTest {
    @Test
    fun theLogIsOnByDefaultAlsoForSettingsSavedBeforeItExisted() {
        assertTrue(DesktopAppSettings().showConnectionLog)

        val directory = Files.createTempDirectory("skipi-connection-log-setting")
        try {
            val path = directory.resolve("settings.json")
            Files.writeString(path, "{}")
            assertTrue(DesktopSettingsLibraries.load(path).getOrThrow().showConnectionLog)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun theAppearanceSwitchReadsAndWritesTheSetting() {
        val settings = DesktopAppSettings(showConnectionLog = true)

        val state = settings.toAppearanceSettingsState()
        assertTrue(state.showConnectionLogOnHome)

        val updated = settings.withAppearanceState(state.copy(showConnectionLogOnHome = false))
        assertFalse(updated.showConnectionLog)
    }

    @Test
    fun theSwitchIsOfferedOnlyWhereALogExists() {
        assertTrue(desktopAppearanceSettingsCapabilities().connectionLog)
        assertFalse(AppearanceSettingsCapabilities().connectionLog)
    }

    @Test
    fun switchingTheLogNeverReconnectsTheTunnel() {
        val settings = DesktopAppSettings(showConnectionLog = true)

        assertFalse(desktopSettingsNeedReconnect(settings, settings.copy(showConnectionLog = false)))
    }
}
