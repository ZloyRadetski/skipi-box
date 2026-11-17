// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.backup.AppBackupSettings
import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import kotlin.test.Test
import kotlin.test.assertEquals

/** The theme is a setting like any other: a backup carries it, whichever client wrote it. */
class DesktopBackupThemeTest {
    private fun restoreOf(theme: DesktopThemeMode): DesktopAppSettings {
        val backup = encodeAppBackup(
            DesktopUserData(settings = DesktopAppSettings(themeMode = theme))
                .toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1),
        )
        return decodeAppBackup(backup).toDesktopRestorePreview(DesktopUserData(settings = DesktopAppSettings(themeMode = DesktopThemeMode.Dark))).userData.settings
    }

    @Test
    fun everyDesktopThemeComesBackFromItsOwnBackup() {
        for (theme in DesktopThemeMode.entries) {
            assertEquals(theme, restoreOf(theme).themeMode, "theme $theme")
        }
    }

    @Test
    fun theBackupNamesTheThemeAsAColorMode() {
        val file = DesktopUserData(settings = DesktopAppSettings(themeMode = DesktopThemeMode.Sakura))
            .toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1)

        assertEquals(app.modes.ColorModeSakura, file.data.settings.colorMode)
        assertEquals(app.modes.ColorModeAmoled, DesktopUserData(settings = DesktopAppSettings(themeMode = DesktopThemeMode.Amoled))
            .toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1).data.settings.colorMode)
    }

    @Test
    fun aThemeFromAnAndroidBackupIsRestoredOnDesktop() {
        val android = encodeAppBackup(
            DesktopUserData().toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1).let { file ->
                file.copy(data = file.data.copy(settings = file.data.settings.copy(colorMode = app.modes.ColorModeForest)))
            },
        )

        val restored = decodeAppBackup(android).toDesktopRestorePreview(DesktopUserData()).userData.settings

        assertEquals(DesktopThemeMode.Forest, restored.themeMode)
    }

    @Test
    fun theSystemThemeOfAnAndroidBackupKeepsTheDesktopTheme() {
        val android = encodeAppBackup(
            DesktopUserData().toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1).let { file ->
                file.copy(data = file.data.copy(settings = file.data.settings.copy(colorMode = app.modes.ColorModeSystem)))
            },
        )

        val restored = decodeAppBackup(android)
            .toDesktopRestorePreview(DesktopUserData(settings = DesktopAppSettings(themeMode = DesktopThemeMode.Light)))
            .userData.settings

        assertEquals(DesktopThemeMode.Light, restored.themeMode)
    }

    @Test
    fun anOldBackupWithoutAThemeKeepsTheCurrentOne() {
        // The default color mode of the backup format is "system"; Desktop has no system theme.
        assertEquals(app.modes.ColorModeSystem, AppBackupSettings().colorMode)
    }
}
