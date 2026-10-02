// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.skipi.app.proxy.moveSubscriptionGroup
import features.subscription.DefaultSubscriptionGroupId

/** Moves one real subscription while retaining the fixed manual-server group. */
internal fun AppState.withMovedSubscriptionGroup(groupId: Int, offset: Int): AppState {
    val reordered = moveSubscriptionGroup(
        groups = subscriptionGroups,
        groupId = groupId,
        offset = offset,
        idOf = { it.id },
        isBuiltIn = { it.builtIn },
        fixedGroupId = DefaultSubscriptionGroupId,
    )
    return if (reordered == subscriptionGroups) this else copy(subscriptionGroups = reordered)
}
