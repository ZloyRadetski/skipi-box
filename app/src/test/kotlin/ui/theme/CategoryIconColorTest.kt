// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.theme

import app.AppState
import data.backup.AppBackupData
import data.backup.AppBackupFile
import data.backup.AppBackupFormat
import data.backup.AppBackupSettings
import data.backup.CurrentAppBackupVersion
import data.backup.toAppBackupFile
import data.backup.toRestorePreview
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryIconColorTest {

    @Test
    fun testBackupRestorePreservesCustomCategoryIconColor() {
        val expectedColor = 0xFF123456L
        val state = AppState(
            enableCustomColors = true,
            customCategoryIconColor = expectedColor,
        )
        val backup = state.toAppBackupFile(
            createdAtMillis = 1000L,
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )
        val restored = backup.toRestorePreview().restoredState
        assertEquals(expectedColor, restored.customCategoryIconColor)
    }

    @Test
    fun testLegacyBackupRestoresToCategoryIconColor() {
        val legacyColor = 0xFFAABBCCL
        val backup = AppBackupFile(
            format = AppBackupFormat,
            version = CurrentAppBackupVersion,
            data = AppBackupData(
                settings = AppBackupSettings(
                    customCategoryAppearanceColor = legacyColor,
                ),
            ),
        )
        val restored = backup.toRestorePreview().restoredState
        assertEquals(legacyColor, restored.customCategoryIconColor)
    }
}
