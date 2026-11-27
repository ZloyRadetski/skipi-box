// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.skipi.app.home.ProxyHomeButtonPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingToolbarPlacementTest {
    // 300 x 740 of room to move in.
    private val area = IntSize(400, 800)
    private val toolbar = IntSize(100, 60)
    private val snap = 40f

    private fun offset(x: Float, y: Float, direction: LayoutDirection = LayoutDirection.Ltr) =
        floatingToolbarOffset(ProxyHomeButtonPosition(x, y), area, toolbar, direction)

    private fun settle(dropped: IntOffset, direction: LayoutDirection = LayoutDirection.Ltr) =
        settledFloatingToolbarPosition(dropped, area, toolbar, direction, snap)

    @Test
    fun aPositionIsAShareOfTheRoomTheButtonCanMoveIn() {
        assertEquals(IntOffset(0, 0), offset(0f, 0f))
        assertEquals(IntOffset(300, 740), offset(1f, 1f))
        assertEquals(IntOffset(150, 370), offset(0.5f, 0.5f))
        assertEquals(IntOffset(75, 185), offset(0.25f, 0.25f))
    }

    @Test
    fun rightToLeftMirrorsStartAndEnd() {
        assertEquals(IntOffset(300, 0), offset(0f, 0f, LayoutDirection.Rtl))
        assertEquals(IntOffset(0, 740), offset(1f, 1f, LayoutDirection.Rtl))
        assertEquals(ProxyHomeButtonPosition(0.25f, 0.25f), settle(IntOffset(225, 185), LayoutDirection.Rtl))
    }

    @Test
    fun droppedAwayFromTheZonesTheButtonStaysWhereItIs() {
        assertEquals(ProxyHomeButtonPosition(0.25f, 0.25f), settle(IntOffset(75, 185)))
        // The very centre is an ordinary place.
        assertEquals(ProxyHomeButtonPosition(0.5f, 0.5f), settle(IntOffset(150, 370)))
    }

    @Test
    fun droppedNearAZoneItSticksToItExactly() {
        LayoutDirection.entries.forEach { direction ->
            ProxyHomeButtonPosition.StickyZones.forEach { zone ->
                val near = floatingToolbarOffset(zone, area, toolbar, direction) + IntOffset(20, -15)
                val dropped = clampFloatingToolbarOffset(near, area, toolbar)

                assertEquals(zone, settle(dropped, direction), "$zone $direction")
            }
        }
    }

    @Test
    fun theZoneReachesAsFarAsTheSnapRadiusAndNoFurther() {
        assertEquals(ProxyHomeButtonPosition(0f, 0f), settle(IntOffset(40, 0)))
        assertEquals(ProxyHomeButtonPosition(41f / 300f, 0f), settle(IntOffset(41, 0)))
        // 30 right and 30 down is further than 40 as the crow flies.
        assertEquals(ProxyHomeButtonPosition(30f / 300f, 30f / 740f), settle(IntOffset(30, 30)))
    }

    @Test
    fun theButtonCannotBeDraggedOffTheScreen() {
        assertEquals(IntOffset(0, 0), clampFloatingToolbarOffset(IntOffset(-50, -1), area, toolbar))
        assertEquals(IntOffset(300, 740), clampFloatingToolbarOffset(IntOffset(999, 999), area, toolbar))
    }

    @Test
    fun anAreaSmallerThanTheButtonPinsItToTheOrigin() {
        val tiny = IntSize(50, 40)

        assertEquals(IntOffset(0, 0), floatingToolbarOffset(ProxyHomeButtonPosition.BottomEnd, tiny, toolbar, LayoutDirection.Ltr))
        assertEquals(IntOffset(0, 0), clampFloatingToolbarOffset(IntOffset(10, 10), tiny, toolbar))
        // There is no room to have moved it, so the default is kept instead of dividing by zero.
        assertEquals(
            ProxyHomeButtonPosition.BottomEnd,
            settledFloatingToolbarPosition(IntOffset(0, 0), tiny, toolbar, LayoutDirection.Ltr, snap),
        )
    }
}
