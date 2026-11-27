// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackActionsTest {
    @Test
    fun theNewestRegisteredActionRunsAndOnlyThatOne() {
        val backs = BackActions()
        val ran = mutableListOf<String>()
        backs.push { ran += "screen" }
        backs.push { ran += "dialog" }

        assertTrue(backs.invokeNewest())

        assertEquals(listOf("dialog"), ran)
    }

    @Test
    fun whenTheOverlayIsGoneBackReachesTheScreenAgain() {
        val backs = BackActions()
        val ran = mutableListOf<String>()
        val dialog: () -> Unit = { ran += "dialog" }
        backs.push { ran += "screen" }
        backs.push(dialog)
        backs.remove(dialog)

        backs.invokeNewest()

        assertEquals(listOf("screen"), ran)
    }

    @Test
    fun withNothingRegisteredBackIsNotUsed() {
        assertFalse(BackActions().invokeNewest())
    }
}
