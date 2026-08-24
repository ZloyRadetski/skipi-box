// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.feedback

import app.AppState
import data.backup.toAppBackupFile
import data.backup.toRestorePreview
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppHapticFeedbackTest {

    @Test
    fun testDefaultAppStateEnableHapticsIsTrue() {
        val state = AppState()
        assertTrue(state.enableHaptics)
    }

    @Test
    fun testAppStateEnableHapticsMutation() {
        val state = AppState()
        val disabled = state.copy(enableHaptics = false)
        assertFalse(disabled.enableHaptics)
        val reEnabled = disabled.copy(enableHaptics = true)
        assertTrue(reEnabled.enableHaptics)
    }

    @Test
    fun testBackupRestorePreservesEnableHapticsTrue() {
        val state = AppState(enableHaptics = true)
        val backup = state.toAppBackupFile(
            createdAtMillis = 1000L,
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )
        val restored = backup.toRestorePreview().restoredState
        assertTrue(restored.enableHaptics)
    }

    @Test
    fun testBackupRestorePreservesEnableHapticsFalse() {
        val state = AppState(enableHaptics = false)
        val backup = state.toAppBackupFile(
            createdAtMillis = 1000L,
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )
        val restored = backup.toRestorePreview().restoredState
        assertFalse(restored.enableHaptics)
    }
}
