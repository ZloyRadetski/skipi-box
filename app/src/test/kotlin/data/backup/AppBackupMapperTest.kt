// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import org.junit.Assert.assertEquals
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

    @Test
    fun backupAndRestore_preservesTrafficNotificationRefreshInterval() {
        val state = AppState(trafficStatsNotificationRefreshIntervalSeconds = 7)
        val backupFile = state.toAppBackupFile(
            createdAtMillis = 0L,
            appVersionName = "1.0",
            appVersionCode = 1,
        )

        assertEquals(7, backupFile.data.settings.trafficStatsNotificationRefreshIntervalSeconds)
        assertEquals(7, backupFile.toRestorePreview().restoredState.trafficStatsNotificationRefreshIntervalSeconds)

        val invalidBackup = backupFile.copy(
            data = backupFile.data.copy(
                settings = backupFile.data.settings.copy(
                    trafficStatsNotificationRefreshIntervalSeconds = 100,
                ),
            ),
        )
        assertEquals(10, invalidBackup.toRestorePreview().restoredState.trafficStatsNotificationRefreshIntervalSeconds)
    }
}
