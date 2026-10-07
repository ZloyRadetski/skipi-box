// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import app.skipi.app.model.ProxyServerRecord

/**
 * Builds the temporary catalog row used when exporting a server draft. A new draft uses Android's
 * temporary ID; an existing draft keeps its captured ID and source group.
 */
fun ProxyServerEditResult.toProxyServerCopyTarget(defaultGroupId: Int): ProxyServerRecord =
    ProxyServerRecord(
        id = serverId ?: TemporaryCopyServerId,
        server = server,
        sourceSubscriptionId = groupId?.takeUnless { groupId -> groupId == defaultGroupId },
    )

/**
 * Replaces a matching row in place or appends a missing copy target without changing this list.
 * Hosts keep their own row representation and use this helper only for an ephemeral export copy.
 */
fun <T> List<T>.withProxyServerCopyTarget(
    target: T,
    idOf: (T) -> Int,
): List<T> {
    val targetId = idOf(target)
    val targetIndex = indexOfFirst { row -> idOf(row) == targetId }
    return if (targetIndex < 0) {
        this + target
    } else {
        toMutableList().also { rows -> rows[targetIndex] = target }
    }
}

private const val TemporaryCopyServerId = -1
