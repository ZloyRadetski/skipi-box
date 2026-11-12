// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.state.ToggleableState

/**
 * What the member picker holds: servers picked one by one, and subscriptions picked whole. A whole
 * subscription covers its servers now and the ones a later refresh brings in.
 */
data class StrategyMemberSelection(
    val memberIds: List<Int> = emptyList(),
    val wholeGroupIds: Set<Int> = emptySet(),
)

/** Pure ordered selection operations shared by strategy-member selector hosts. */
object StrategyMemberSelectionState {
    fun isSelected(selection: StrategyMemberSelection, groupId: Int?, serverId: Int): Boolean =
        (groupId != null && groupId in selection.wholeGroupIds) || serverId in selection.memberIds

    /** Clearing a server of a whole subscription turns the subscription into plain members, minus that server. */
    fun toggleMember(
        selection: StrategyMemberSelection,
        groupId: Int?,
        groupServerIds: List<Int>,
        serverId: Int,
    ): StrategyMemberSelection {
        if (groupId != null && groupId in selection.wholeGroupIds) {
            return StrategyMemberSelection(
                memberIds = (selection.memberIds + groupServerIds).distinct() - serverId,
                wholeGroupIds = selection.wholeGroupIds - groupId,
            )
        }
        return selection.copy(memberIds = toggleMember(selection.memberIds, serverId))
    }

    /** A group that belongs to a subscription is selected whole (and stays so); any other is a plain toggle. */
    fun toggleGroup(selection: StrategyMemberSelection, groupId: Int?, groupServerIds: List<Int>): StrategyMemberSelection {
        if (groupId == null) return selection.copy(memberIds = toggleGroup(selection.memberIds, groupServerIds))
        val ids = groupServerIds.distinct()
        val selected = selection.memberIds.toSet()
        val isOn = groupId in selection.wholeGroupIds || (ids.isNotEmpty() && ids.all(selected::contains))
        return if (isOn) {
            StrategyMemberSelection(
                memberIds = selection.memberIds.filterNot(ids.toSet()::contains),
                wholeGroupIds = selection.wholeGroupIds - groupId,
            )
        } else {
            StrategyMemberSelection(
                memberIds = selection.memberIds.distinct() + ids.filterNot(selected::contains),
                wholeGroupIds = selection.wholeGroupIds + groupId,
            )
        }
    }

    fun groupToggleState(selection: StrategyMemberSelection, groupId: Int?, groupServerIds: Iterable<Int>): ToggleableState =
        if (groupId != null && groupId in selection.wholeGroupIds) ToggleableState.On
        else groupToggleState(selection.memberIds, groupServerIds)

    /** The members to store: those a whole subscription already covers are left out, so they do not go stale. */
    fun explicitMemberIds(selection: StrategyMemberSelection, serverIdsByGroup: Map<Int, List<Int>>): List<Int> {
        val covered = selection.wholeGroupIds.flatMap { id -> serverIdsByGroup[id].orEmpty() }.toSet()
        return selection.memberIds.distinct().filterNot(covered::contains)
    }

    fun toggleMember(selectedIds: List<Int>, serverId: Int): List<Int> {
        val current = selectedIds.distinct()
        return if (serverId in current) current - serverId else current + serverId
    }

    fun toggleGroup(selectedIds: List<Int>, groupServerIds: Iterable<Int>): List<Int> {
        val current = selectedIds.distinct()
        val group = groupServerIds.distinct()
        if (group.isEmpty()) return current
        val selected = current.toSet()
        return if (group.all(selected::contains)) current.filterNot(group.toSet()::contains)
        else current + group.filterNot(selected::contains)
    }

    fun groupToggleState(selectedIds: List<Int>, groupServerIds: Iterable<Int>): ToggleableState {
        val selected = selectedIds.toSet()
        val group = groupServerIds.toList()
        return when {
            group.isEmpty() -> ToggleableState.Off
            group.all(selected::contains) -> ToggleableState.On
            group.any(selected::contains) -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        }
    }
}
