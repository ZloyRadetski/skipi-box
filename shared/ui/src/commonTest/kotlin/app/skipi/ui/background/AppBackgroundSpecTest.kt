// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.background

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** Values are the Android `AppBackground` constants that both hosts must keep identical. */
class AppBackgroundSpecTest {
    @Test
    fun runningConnectionUsesGreenCyanPalette() {
        val dark = connectionBackgroundSpec(isRunning = true, isDark = true)
        assertEquals(Color(0xFF10B981), dark.firstColor)
        assertEquals(Color(0xFF06B6D4), dark.secondColor)
        assertEquals(0.58f, dark.primaryAlpha)
        assertEquals(0.32f, dark.secondaryAlpha)

        val light = connectionBackgroundSpec(isRunning = true, isDark = false)
        assertEquals(0.40f, light.primaryAlpha)
        assertEquals(0.20f, light.secondaryAlpha)
    }

    @Test
    fun idleConnectionUsesIndigoVioletPaletteByTheme() {
        val dark = connectionBackgroundSpec(isRunning = false, isDark = true)
        assertEquals(Color(0xFF6366F1), dark.firstColor)
        assertEquals(Color(0xFF8B5CF6), dark.secondColor)
        assertEquals(0.45f, dark.primaryAlpha)
        assertEquals(0.22f, dark.secondaryAlpha)

        val light = connectionBackgroundSpec(isRunning = false, isDark = false)
        assertEquals(Color(0xFF4F46E5), light.firstColor)
        assertEquals(Color(0xFF7C3AED), light.secondColor)
        assertEquals(0.30f, light.primaryAlpha)
        assertEquals(0.14f, light.secondaryAlpha)
    }

    @Test
    fun dimAlphaIsClampedToPercentRange() {
        assertEquals(0f, backgroundDimAlpha(-5))
        assertEquals(0.45f, backgroundDimAlpha(45))
        assertEquals(1f, backgroundDimAlpha(250))
    }

    @Test
    fun missingPhotoFallsBackToThemedGradient() {
        assertEquals(listOf(Color(0xFF312E81), Color(0xFF1E1B4B)), photoFallbackGradient(isDark = true))
        assertEquals(listOf(Color(0xFFC7D2FE), Color(0xFFE0E7FF)), photoFallbackGradient(isDark = false))
    }
}

class AuroraPhasesTest {
    @Test
    fun everyPhaseStartsAtRestAndPeaksAfterItsOwnDuration() {
        val start = auroraPhases(0)
        assertEquals(listOf(0f, 0f, 0f, 0f), listOf(start.t1, start.t2, start.t3, start.t4))
        assertEquals(1f, auroraPhases(17_000).t1)
        assertEquals(1f, auroraPhases(13_000).t2)
        assertEquals(1f, auroraPhases(21_000).t3)
        assertEquals(1f, auroraPhases(11_000).t4)
    }

    @Test
    fun phasesReverseLikeTheOriginalReverseRepeatAnimation() {
        assertEquals(0f, auroraPhases(34_000).t1)
        assertEquals(auroraPhases(8_500).t1, auroraPhases(25_500).t1, 1e-4f)
        assertEquals(auroraPhases(5_000).t4, auroraPhases(17_000).t4, 1e-4f)
    }

    @Test
    fun phasesStayInRangeAndRiseDuringTheFirstHalfCycle() {
        var previous = -1f
        for (elapsed in 0L..17_000L step 500L) {
            val value = auroraPhases(elapsed).t1
            assertEquals(true, value in 0f..1f)
            assertEquals(true, value >= previous)
            previous = value
        }
    }
}
