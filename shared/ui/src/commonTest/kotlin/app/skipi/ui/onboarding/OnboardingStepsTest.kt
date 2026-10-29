// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStepsTest {
    @Test
    fun androidFlowKeepsAllSixStepsInOrder() {
        assertEquals(
            listOf(
                OnboardingStep.Welcome,
                OnboardingStep.Telegram,
                OnboardingStep.Permissions,
                OnboardingStep.Appearance,
                OnboardingStep.Import,
                OnboardingStep.Complete,
            ),
            onboardingSteps(SkipiOnboardingState()),
        )
    }

    @Test
    fun hostsWithoutRuntimePermissionsSkipThePermissionsStep() {
        val steps = onboardingSteps(SkipiOnboardingState(showPermissionsStep = false))
        assertEquals(5, steps.size)
        assertEquals(false, OnboardingStep.Permissions in steps)
    }
}
