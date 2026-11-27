// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyHomeButtonPositionTest {
    @Test
    fun theButtonStaysBottomEndUntilTheUserMovesIt() {
        assertEquals(ProxyHomeButtonPosition(1f, 1f), ProxyHomeButtonPosition.BottomEnd)
        assertEquals(ProxyHomeButtonPosition.BottomEnd, ProxyHomeDisplayOptions().floatingButtonPosition)
    }

    @Test
    fun itSticksToTheFourCornersAndTheFourSideMiddles() {
        val zones = ProxyHomeButtonPosition.StickyZones

        assertEquals(8, zones.toSet().size)
        assertTrue(zones.all { it.x in listOf(0f, 0.5f, 1f) && it.y in listOf(0f, 0.5f, 1f) })
        assertTrue(ProxyHomeButtonPosition(0.5f, 0.5f) !in zones)
    }

    @Test
    fun anyStoredPlaceOnTheScreenIsKeptAndNonsenseFallsBackPerAxis() {
        assertEquals(ProxyHomeButtonPosition(0.3f, 0.65f), proxyHomeButtonPositionFromSettings(0.3f, 0.65f))
        assertEquals(ProxyHomeButtonPosition(1f, 0f), proxyHomeButtonPositionFromSettings(-0.1f, 0f))
        assertEquals(ProxyHomeButtonPosition(0f, 1f), proxyHomeButtonPositionFromSettings(0f, 1.5f))
        assertEquals(ProxyHomeButtonPosition.BottomEnd, proxyHomeButtonPositionFromSettings(Float.NaN, Float.POSITIVE_INFINITY))
    }
}
