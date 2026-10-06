// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.state.ToggleableState

/** Pure ordered selection operations shared by strategy-member selector hosts. */
object StrategyMemberSelectionState {
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
