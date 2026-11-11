// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/** What a request to move a server on Home comes to, decided from the order the user sees. */
sealed interface ProxyHomeServerMove {
    /** Put [fromId] where [toId] is. */
    data class Swap(val fromId: Int, val toId: Int) : ProxyHomeServerMove

    /** The servers are sorted by name or latency, so their shown order is not the stored one and cannot be edited. */
    data object NotDefaultSort : ProxyHomeServerMove

    /** A step that would leave the group, or that is no step at all, or that starts from a server that is not shown. */
    data object OutOfRange : ProxyHomeServerMove

    /** A drag that starts and ends on the same server. */
    data object SameServer : ProxyHomeServerMove

    /** A drag that involves a server that is no longer shown. */
    data object NotVisible : ProxyHomeServerMove
}

/** Moves [serverId] [offset] places along [visibleIds], the shown order of the group. */
fun resolveProxyHomeServerStep(
    sortMode: ProxyHomeSortMode,
    visibleIds: List<Int>,
    serverId: Int,
    offset: Int,
): ProxyHomeServerMove {
    if (sortMode != ProxyHomeSortMode.Default) return ProxyHomeServerMove.NotDefaultSort
    val fromIndex = visibleIds.indexOf(serverId)
    val toId = visibleIds.getOrNull(fromIndex + offset)
    if (fromIndex == -1 || toId == null || offset == 0) return ProxyHomeServerMove.OutOfRange
    return ProxyHomeServerMove.Swap(fromId = serverId, toId = toId)
}

/** Drops [fromId] onto [toId], both among [visibleIds], the shown order of the group. */
fun resolveProxyHomeServerDrag(
    sortMode: ProxyHomeSortMode,
    visibleIds: List<Int>,
    fromId: Int,
    toId: Int,
): ProxyHomeServerMove = when {
    sortMode != ProxyHomeSortMode.Default -> ProxyHomeServerMove.NotDefaultSort
    fromId == toId -> ProxyHomeServerMove.SameServer
    fromId !in visibleIds || toId !in visibleIds -> ProxyHomeServerMove.NotVisible
    else -> ProxyHomeServerMove.Swap(fromId = fromId, toId = toId)
}
