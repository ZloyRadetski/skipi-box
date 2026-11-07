// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DesktopTrayMenuTest {
    @Test
    fun theMenuHasTheToggleASeparatorAndExit() {
        val menu = DesktopTrayMenu("Connect", "Exit", onToggleTunnel = {}, onExit = {})

        assertEquals(
            listOf(1 to "Connect", 2 to null, 3 to "Exit"),
            menu.entries.map { it.id to it.label },
        )
    }

    @Test
    fun clickingAnEntryRunsItsActionAndNothingElse() {
        val calls = mutableListOf<String>()
        val menu = DesktopTrayMenu("Connect", "Exit", onToggleTunnel = { calls += "toggle" }, onExit = { calls += "exit" })

        menu.click(1)
        menu.click(2)
        menu.click(99)
        menu.click(3)

        assertEquals(listOf("toggle", "exit"), calls)
    }

    @Test
    fun newLabelsAreShownAndBumpTheRevision() {
        val menu = DesktopTrayMenu("Connect", "Exit", onToggleTunnel = {}, onExit = {})
        val before = menu.revision

        assertEquals(false, menu.setLabels("Connect", "Exit"))
        assertEquals(before, menu.revision)

        assertEquals(true, menu.setLabels("Disconnect", "Exit"))
        assertEquals("Disconnect", menu.entries.first().label)
        assertEquals(before + 1, menu.revision)
    }

    @Test
    fun aPixmapIsSentAsBigEndianArgbBytes() {
        val bytes = trayPixmapBytes(intArrayOf(0x80FF8040.toInt(), 0x00000000))

        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0x80.toByte(), 0x40, 0, 0, 0, 0),
            bytes,
        )
    }
}
