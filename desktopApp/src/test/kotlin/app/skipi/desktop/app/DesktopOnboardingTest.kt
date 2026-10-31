// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.onboarding.OnboardingAppearance
import app.skipi.ui.onboarding.OnboardingBottomBarSize
import app.skipi.ui.onboarding.OnboardingLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOnboardingTest {
    @Test
    fun stateReflectsPersistedSettingsAndHidesAndroidOnlyOptions() {
        val settings = DesktopAppSettings(
            themeMode = DesktopThemeMode.Amoled,
            languageTag = "ru",
            bottomBarSize = 0,
        )

        val state = settings.toOnboardingState(serverCount = 3, subscriptionCount = 2)

        assertEquals(OnboardingAppearance.Amoled, state.appearance)
        assertEquals(OnboardingLanguage.Russian, state.language)
        assertEquals(OnboardingBottomBarSize.Small, state.bottomBarSize)
        assertEquals(3, state.proxyServerCount)
        assertEquals(2, state.subscriptionGroupCount)
        assertFalse(state.showPermissionsStep)
        assertFalse(state.qrScanAvailable)
        assertFalse(state.showSystemAppearance)
        assertFalse(state.showAccentColors)
    }

    @Test
    fun customDesktopThemesAreReportedAsOther() {
        assertEquals(
            OnboardingAppearance.Other,
            DesktopAppSettings(themeMode = DesktopThemeMode.Sunset).toOnboardingState(0, 0).appearance,
        )
    }

    @Test
    fun choicesAreWrittenBackToDesktopSettings() {
        val base = DesktopAppSettings()
        assertEquals(DesktopThemeMode.Light, base.withOnboardingAppearance(OnboardingAppearance.Light).themeMode)
        assertEquals(DesktopThemeMode.Amoled, base.withOnboardingAppearance(OnboardingAppearance.Amoled).themeMode)
        assertEquals("zh", base.withOnboardingLanguage(OnboardingLanguage.Chinese).languageTag)
        assertEquals(null, base.copy(languageTag = "ru").withOnboardingLanguage(OnboardingLanguage.FollowSystem).languageTag)
        assertEquals(2, base.withOnboardingBottomBarSize(OnboardingBottomBarSize.Large).bottomBarSize)
    }

    @Test
    fun finishingMarksOnboardingCompleted() {
        assertFalse(DesktopAppSettings().onboardingCompleted)
        assertTrue(DesktopAppSettings().withOnboardingCompleted().onboardingCompleted)
    }
}
