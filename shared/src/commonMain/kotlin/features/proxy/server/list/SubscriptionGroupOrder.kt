// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import features.subscription.DefaultSubscriptionGroupId

/** Moves one real subscription while retaining the fixed manual-server group. */
fun AppState.withMovedSubscriptionGroup(groupId: Int, offset: Int): AppState {
    if (offset == 0 || groupId == DefaultSubscriptionGroupId) return this
    val movable = subscriptionGroups.filter { group ->
        group.id != DefaultSubscriptionGroupId && !group.builtIn
    }
    val sourceIndex = movable.indexOfFirst { group -> group.id == groupId }
    if (sourceIndex < 0) return this
    val targetIndex = (sourceIndex + offset).coerceIn(0, movable.lastIndex)
    if (sourceIndex == targetIndex) return this

    val reordered = movable.toMutableList().apply {
        add(targetIndex, removeAt(sourceIndex))
    }
    val movableIds = reordered.mapTo(mutableSetOf()) { group -> group.id }
    var nextIndex = 0
    return copy(
        subscriptionGroups = subscriptionGroups.map { group ->
            if (group.id in movableIds) reordered[nextIndex++] else group
        },
    )
}
