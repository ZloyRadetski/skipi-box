// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupMapperTest {

    @Test
    fun backupAndRestore_preservesEnableLocalProxyAuth() {
        val disabledState = AppState(enableLocalProxyAuth = false)
        val backupFile = disabledState.toAppBackupFile(
            createdAtMillis = 0L,
            appVersionName = "1.0",
            appVersionCode = 1,
        )
        assertFalse(backupFile.data.settings.enableLocalProxyAuth)

        val restoredState = backupFile.toRestorePreview().restoredState
        assertFalse(restoredState.enableLocalProxyAuth)

        val enabledState = AppState(enableLocalProxyAuth = true)
        val enabledBackupFile = enabledState.toAppBackupFile(
            createdAtMillis = 0L,
            appVersionName = "1.0",
            appVersionCode = 1,
        )
        assertTrue(enabledBackupFile.data.settings.enableLocalProxyAuth)

        val restoredEnabledState = enabledBackupFile.toRestorePreview().restoredState
        assertTrue(restoredEnabledState.enableLocalProxyAuth)
    }
}
