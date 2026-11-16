// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewYearSceneTest {
    @Test
    fun snowflakesAreTheSameForTheSameSeed() {
        assertEquals(newYearSnowflakes(count = 40, seed = 7), newYearSnowflakes(count = 40, seed = 7))
    }

    @Test
    fun snowflakesStayInsideTheUnitSquareWithPositiveSizeAndSpeed() {
        val flakes = newYearSnowflakes(count = 80, seed = 1)
        assertEquals(80, flakes.size)
        for (flake in flakes) {
            assertTrue(flake.x in 0f..1f, "x ${flake.x}")
            assertTrue(flake.phase in 0f..1f, "phase ${flake.phase}")
            assertTrue(flake.radius > 0f && flake.fallMillis > 0L)
        }
    }

    @Test
    fun fallProgressWrapsAndAdvancesWithTime() {
        val flake = SnowflakeSpec(x = 0.5f, phase = 0.25f, radius = 2f, fallMillis = 10_000L, swayAmplitude = 0.02f)
        assertEquals(0.25f, snowfallProgress(flake, 0L), 1e-4f)
        assertEquals(0.75f, snowfallProgress(flake, 5_000L), 1e-4f)
        assertEquals(0.25f, snowfallProgress(flake, 10_000L), 1e-4f)
        assertTrue(snowfallProgress(flake, 123_456L) in 0f..1f)
    }

    @Test
    fun garlandBulbsFillTheWidthAndHangOnTheArc() {
        val bulbs = garlandBulbs(width = 1000f, spacing = 50f, sag = 40f)
        assertEquals(20, bulbs.size)
        for (bulb in bulbs) {
            assertTrue(bulb.x in 0f..1000f, "x ${bulb.x}")
            assertTrue(bulb.y in 0f..40f, "y ${bulb.y}")
        }
    }

    @Test
    fun garlandSagsDeepestBetweenTheArcEndsOfEverySpan() {
        val bulbs = garlandBulbs(width = 1000f, spacing = 50f, sag = 40f)
        val spanSag = bulbs.map { it.y }.max()
        assertTrue(spanSag > 30f)
        assertEquals(0f, garlandY(x = 0f, spanWidth = 250f, sag = 40f), 1e-4f)
        assertEquals(40f, garlandY(x = 125f, spanWidth = 250f, sag = 40f), 1e-4f)
        assertEquals(0f, garlandY(x = 250f, spanWidth = 250f, sag = 40f), 1e-4f)
    }

    @Test
    fun bulbColorsCycleThroughThePalette() {
        val bulbs = garlandBulbs(width = 1000f, spacing = 50f, sag = 40f)
        assertEquals(NewYearBulbColors.size, bulbs.map { it.colorIndex }.distinct().size)
    }

    @Test
    fun twinkleStaysWithinVisibleRangeAndMovesWithTime() {
        val values = (0 until 40).map { twinkleAlpha(bulbIndex = 3, elapsedMillis = it * 137L) }
        assertTrue(values.all { it in 0.45f..1f })
        assertTrue(values.max() - values.min() > 0.2f)
    }
}
