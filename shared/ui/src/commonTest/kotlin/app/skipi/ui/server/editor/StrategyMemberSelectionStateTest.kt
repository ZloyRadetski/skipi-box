// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.state.ToggleableState
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyMemberSelectionStateTest {
    @Test
    fun togglingOneMemberPreservesOrderAndRemovesDuplicateInput() {
        assertEquals(listOf(8, 3, 5), StrategyMemberSelectionState.toggleMember(listOf(8, 3, 8), 5))
        assertEquals(listOf(8), StrategyMemberSelectionState.toggleMember(listOf(8, 3, 8), 3))
    }

    @Test
    fun togglingAGroupAddsInSourceOrderAndRemovesAllWhenAlreadySelected() {
        assertEquals(listOf(9, 4, 7, 2), StrategyMemberSelectionState.toggleGroup(listOf(9, 4), listOf(7, 2, 7)))
        assertEquals(listOf(9), StrategyMemberSelectionState.toggleGroup(listOf(9, 4, 2, 7), listOf(2, 4, 7)))
    }

    @Test
    fun groupToggleStateDistinguishesEmptyPartialAndComplete() {
        assertEquals(ToggleableState.Off, StrategyMemberSelectionState.groupToggleState(emptyList(), emptyList()))
        assertEquals(ToggleableState.Indeterminate, StrategyMemberSelectionState.groupToggleState(listOf(4), listOf(4, 7)))
        assertEquals(ToggleableState.On, StrategyMemberSelectionState.groupToggleState(listOf(4, 7), listOf(4, 7)))
    }
}
