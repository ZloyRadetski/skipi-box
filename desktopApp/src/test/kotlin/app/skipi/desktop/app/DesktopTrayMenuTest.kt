// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopTrayMenuTest {
    private val entries = listOf(
        DesktopTrayMenuEntry.Action("Connect") {},
        DesktopTrayMenuEntry.Separator,
        DesktopTrayMenuEntry.Action("Exit") {},
    )

    @Test
    fun theMenuIsAsTallAsItsRowsAndSeparatorsAndTheWindowAddsRoomForTheShadow() {
        val menu = desktopTrayMenuSize(entries)
        assertEquals(DesktopTrayMenuSize(width = 220, height = 12 + 36 + 9 + 36), menu)

        val window = desktopTrayMenuWindowSize(entries)
        assertEquals(DesktopTrayMenuSize(width = 220 + 2 * 12, height = menu.height + 2 * 12), window)
    }

    @Test
    fun theActiveServerIsAnInfoLineWithItsOwnSeparatorAboveTheActions() {
        val withServer = desktopTrayMenuEntries("Frankfurt 1", "Connect", "Exit", {}, {})

        assertEquals(5, withServer.size)
        assertEquals("Frankfurt 1", (withServer[0] as DesktopTrayMenuEntry.Info).label)
        assertEquals(DesktopTrayMenuEntry.Separator, withServer[1])
        assertEquals(DesktopTrayMenuSize(width = 220, height = 12 + 36 + 9 + 36 + 9 + 36), desktopTrayMenuSize(withServer))
    }

    @Test
    fun withoutAServerTheMenuIsJustTheActions() {
        assertEquals(3, desktopTrayMenuEntries(null, "Connect", "Exit", {}, {}).size)
        assertEquals(3, desktopTrayMenuEntries("  ", "Connect", "Exit", {}, {}).size)
    }

    @Test
    fun theTooltipNamesTheServerWhenThereIsOne() {
        assertEquals("SKIPI — Connected · Frankfurt 1", desktopTrayTooltip("Connected", "Frankfurt 1"))
        assertEquals("SKIPI — Connected", desktopTrayTooltip("Connected", null))
    }

    private val screen = DesktopTrayScreenArea(x = 0, y = 0, width = 1920, height = 1040)
    private val window = DesktopTrayMenuSize(width = 244, height = 117)

    @Test
    fun aClickOnTheTaskbarAtTheBottomRightOpensTheMenuUpAndToTheLeft() {
        val origin = desktopTrayMenuOrigin(clickX = 1900, clickY = 1040, window = window, screen = screen)

        assertEquals(1900 - 244, origin.x)
        assertEquals(1040 - 117, origin.y)
    }

    @Test
    fun aClickNearTheTopLeftOpensTheMenuDownAndToTheRight() {
        val origin = desktopTrayMenuOrigin(clickX = 10, clickY = 8, window = window, screen = screen)

        assertEquals(10, origin.x)
        assertEquals(8, origin.y)
    }

    @Test
    fun theMenuNeverLeavesTheUsableScreenArea() {
        val tooLeft = desktopTrayMenuOrigin(clickX = -50, clickY = 500, window = window, screen = screen)
        val tooLow = desktopTrayMenuOrigin(clickX = 500, clickY = 5000, window = window, screen = screen)

        assertEquals(0, tooLeft.x)
        assertEquals(1040 - 117, tooLow.y)
    }

    @Test
    fun theScreenHoldingTheClickDecidesTheArea() {
        val left = DesktopTrayScreenArea(0, 0, 1920, 1040)
        val right = DesktopTrayScreenArea(1920, 0, 1280, 1024)

        assertEquals(left, desktopTrayScreenAt(listOf(left, right), x = 100, y = 100))
        assertEquals(right, desktopTrayScreenAt(listOf(left, right), x = 2500, y = 900))
        assertNull(desktopTrayScreenAt(listOf(left, right), x = 9000, y = 9000))
        assertNotNull(desktopTrayScreenAt(listOf(left), x = 1919, y = 1039))
    }
}
