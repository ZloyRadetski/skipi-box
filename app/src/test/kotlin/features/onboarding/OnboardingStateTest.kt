// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.onboarding

import app.AppState
import app.navigation.Route
import data.backup.toAppBackupFile
import data.backup.toRestorePreview
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStateTest {

    @Test
    fun testDefaultAppStateHasCompletedOnboardingIsFalse() {
        val state = AppState()
        assertFalse(state.hasCompletedOnboarding)
    }

    @Test
    fun testOnboardingStateMutation() {
        val state = AppState()
        val updated = state.copy(hasCompletedOnboarding = true)
        assertTrue(updated.hasCompletedOnboarding)
    }

    @Test
    fun testBackupRestoreSetsHasCompletedOnboardingToTrue() {
        val state = AppState(hasCompletedOnboarding = false)
        val backup = state.toAppBackupFile(
            createdAtMillis = 1000L,
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )
        val restored = backup.toRestorePreview().restoredState
        assertTrue(restored.hasCompletedOnboarding)
    }

    @Test
    fun testOnboardingRouteIsDistinct() {
        val onboardingRoute: Route = Route.Onboarding
        val mainRoute: Route = Route.Main
        assertEquals("Onboarding", onboardingRoute.toString())
        assertTrue(onboardingRoute != mainRoute)
    }
}
