// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.backup.AppBackupFile
import features.backup.AppBackupSettings
import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A backup is outside input: a size or mode no screen offers is not applied, as Android never applied it. */
class DesktopBackupRangesTest {
    private fun restore(edit: (AppBackupSettings) -> AppBackupSettings, current: DesktopAppSettings = DesktopAppSettings()): DesktopAppSettings {
        val file: AppBackupFile = DesktopUserData(settings = current)
            .toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1)
        val edited = file.copy(data = file.data.copy(settings = edit(file.data.settings)))
        return decodeAppBackup(encodeAppBackup(edited)).toDesktopRestorePreview(DesktopUserData(settings = current)).userData.settings
    }

    @Test
    fun aBottomBarSizeOutsideTheOptionsIsNotApplied() {
        assertNull(restore({ it.copy(bottomBarSize = 9) }).bottomBarSize)
        assertNull(restore({ it.copy(bottomBarSize = -1) }).bottomBarSize)
        assertEquals(0, restore({ it.copy(bottomBarSize = 0) }).bottomBarSize)
    }

    @Test
    fun aButtonSizeOutsideTheOptionsIsNotApplied() {
        assertNull(restore({ it.copy(classicButtonSize = 7) }).classicButtonSize)
        assertEquals(3, restore({ it.copy(classicButtonSize = 3) }).classicButtonSize)
    }

    @Test
    fun aFontSizeBetweenTheStepsGoesToTheNearestStep() {
        assertEquals(app.modes.normalizeFontSizeMode(37), restore({ it.copy(fontSizeMode = 37) }).fontSizeMode)
        assertEquals(120, restore({ it.copy(fontSizeMode = 120) }).fontSizeMode)
    }
}
