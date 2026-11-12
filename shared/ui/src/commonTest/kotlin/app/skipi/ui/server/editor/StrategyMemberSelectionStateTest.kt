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

    @Test
    fun selectingAWholeSubscriptionKeepsItSelectedForServersThatAppearLater() {
        val picked = StrategyMemberSelectionState.toggleGroup(StrategyMemberSelection(), groupId = 3, groupServerIds = listOf(7, 8))

        assertEquals(setOf(3), picked.wholeGroupIds)
        assertEquals(ToggleableState.On, StrategyMemberSelectionState.groupToggleState(picked, 3, listOf(7, 8, 9)))
        assertEquals(true, StrategyMemberSelectionState.isSelected(picked, groupId = 3, serverId = 9))
    }

    @Test
    fun anEmptySubscriptionCanBeSelectedWhole() {
        val picked = StrategyMemberSelectionState.toggleGroup(StrategyMemberSelection(), groupId = 3, groupServerIds = emptyList())

        assertEquals(setOf(3), picked.wholeGroupIds)
        assertEquals(ToggleableState.On, StrategyMemberSelectionState.groupToggleState(picked, 3, emptyList()))
    }

    @Test
    fun clearingOneServerOfAWholeSubscriptionKeepsTheOthersAsPlainMembers() {
        val whole = StrategyMemberSelectionState.toggleGroup(StrategyMemberSelection(), groupId = 3, groupServerIds = listOf(7, 8, 9))

        val next = StrategyMemberSelectionState.toggleMember(whole, groupId = 3, groupServerIds = listOf(7, 8, 9), serverId = 8)

        assertEquals(emptySet(), next.wholeGroupIds)
        assertEquals(listOf(7, 9), next.memberIds)
    }

    @Test
    fun togglingAWholeSubscriptionOffClearsItsMembersToo() {
        val whole = StrategyMemberSelectionState.toggleGroup(StrategyMemberSelection(listOf(1)), groupId = 3, groupServerIds = listOf(7, 8))

        val off = StrategyMemberSelectionState.toggleGroup(whole, groupId = 3, groupServerIds = listOf(7, 8))

        assertEquals(StrategyMemberSelection(listOf(1)), off)
    }

    @Test
    fun aGroupWithoutASubscriptionKeepsThePlainToggle() {
        val picked = StrategyMemberSelectionState.toggleGroup(StrategyMemberSelection(), groupId = null, groupServerIds = listOf(5, 6))

        assertEquals(StrategyMemberSelection(listOf(5, 6)), picked)
    }

    @Test
    fun savedMembersLeaveOutTheServersAWholeSubscriptionAlreadyCovers() {
        val selection = StrategyMemberSelection(memberIds = listOf(1, 7, 8), wholeGroupIds = setOf(3))

        assertEquals(listOf(1), StrategyMemberSelectionState.explicitMemberIds(selection, mapOf(3 to listOf(7, 8))))
    }
}
