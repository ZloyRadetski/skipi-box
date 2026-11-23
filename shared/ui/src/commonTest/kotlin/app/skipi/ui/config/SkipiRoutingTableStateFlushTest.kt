// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals

class SkipiRoutingTableStateFlushTest {
    @Test
    fun anEditWaitingOutItsPauseIsSavedBeforeAnotherRowOrANewRuleOpens() {
        val state = SkipiRoutingTableState()
        var flushes = 0
        state.attachFlusher { flushes++ }
        state.startNew()
        state.change { it.copy(policy = "DIRECT") }

        state.startNew()

        assertEquals(1, flushes)
    }

    @Test
    fun closingWithoutAPendingEditSavesNothing() {
        val state = SkipiRoutingTableState()
        var flushes = 0
        state.attachFlusher { flushes++ }
        state.startNew()

        state.close()

        assertEquals(0, flushes)
    }

    @Test
    fun aDiscardedEditIsNotSaved() {
        val state = SkipiRoutingTableState()
        var flushes = 0
        state.attachFlusher { flushes++ }
        state.startNew()
        state.change { it.copy(policy = "DIRECT") }

        state.discardPending()
        state.close()

        assertEquals(0, flushes)
    }
}
