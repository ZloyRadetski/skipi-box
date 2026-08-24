// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.feedback

import app.AppState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    @Test
    fun testComposeHapticsAreRoutedToApplicationHaptics() {
        val haptics = RecordingHaptics()
        val bridge = ComposeAppHapticFeedback(haptics)

        bridge.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        bridge.performHapticFeedback(HapticFeedbackType.LongPress)
        bridge.performHapticFeedback(HapticFeedbackType.Confirm)
        bridge.performHapticFeedback(HapticFeedbackType.GestureEnd)

        assertEquals(
            listOf("serverSelected", "actionWarning", "actionSuccess", "actionSuccess"),
            haptics.calls,
        )
    }

    private class RecordingHaptics : AppHaptics {
        val calls = mutableListOf<String>()

        override fun vpnToggle() { calls.add("vpnToggle") }
        override fun vpnConnected() { calls.add("vpnConnected") }
        override fun vpnDisconnected() { calls.add("vpnDisconnected") }
        override fun vpnError() { calls.add("vpnError") }
        override fun serverSelected() { calls.add("serverSelected") }
        override fun groupSwitched() { calls.add("groupSwitched") }
        override fun actionSuccess() { calls.add("actionSuccess") }
        override fun actionWarning() { calls.add("actionWarning") }
    }
}
