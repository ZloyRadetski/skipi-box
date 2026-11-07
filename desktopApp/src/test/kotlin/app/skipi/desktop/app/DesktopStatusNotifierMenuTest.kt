// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStatusNotifierMenuTest {
    private fun entries(calls: MutableList<String>, server: String? = null, toggle: String = "Connect") =
        desktopTrayMenuEntries(server, toggle, "Exit", { calls += "toggle" }, { calls += "exit" })

    @Test
    fun clickingAnActionRunsItAndAnythingElseDoesNothing() {
        val calls = mutableListOf<String>()
        val menu = DesktopStatusNotifierMenu(entries(calls, server = "Frankfurt 1"))

        menu.click(1) // the server line
        menu.click(2) // a separator
        menu.click(99)
        menu.click(3)
        menu.click(5)

        assertEquals(listOf("toggle", "exit"), calls)
    }

    @Test
    fun newLabelsBumpTheRevisionAndSameLabelsDoNot() {
        val calls = mutableListOf<String>()
        val menu = DesktopStatusNotifierMenu(entries(calls))
        val before = menu.revision

        assertFalse(menu.set(entries(calls)))
        assertEquals(before, menu.revision)

        assertTrue(menu.set(entries(calls, toggle = "Disconnect")))
        assertEquals(before + 1, menu.revision)

        assertTrue(menu.set(entries(calls, server = "Frankfurt 1", toggle = "Disconnect")))
        assertEquals(before + 2, menu.revision)
    }

    @Test
    fun aClickRunsTheNewestAction() {
        val calls = mutableListOf<String>()
        val menu = DesktopStatusNotifierMenu(entries(calls))
        val newer = mutableListOf<String>()

        menu.set(entries(newer))
        menu.click(1)

        assertEquals(emptyList(), calls)
        assertEquals(listOf("toggle"), newer)
    }
}
