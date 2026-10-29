// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxyHomeUiState

/**
 * The few Home facts a server row depends on. Rows take this instead of the whole UI state, so a
 * latency update (which changes the state on every batch) no longer recomposes rows whose own
 * summary and context are unchanged.
 */
internal data class ServerRowContext(
    val sortMode: ProxyHomeSortMode,
    val searchQuery: String,
    val availableActions: Set<ProxyHomeActionId>,
    val busyActions: Set<ProxyHomeActionId>,
    val availableCopyFormats: Set<ProxyHomeCopyFormat>,
    val displayOptions: ProxyHomeDisplayOptions,
) {
    fun canUse(actionId: ProxyHomeActionId): Boolean =
        canDispatchHomeAction(actionId, availableActions, busyActions)
}

internal fun ProxyHomeUiState.toServerRowContext() = ServerRowContext(
    sortMode = sortMode,
    searchQuery = searchQuery,
    availableActions = availableActions,
    busyActions = busyActions,
    availableCopyFormats = availableCopyFormats,
    displayOptions = displayOptions,
)

/**
 * Ids only: no resources are read here, so a row that never opens its menu pays nothing for it.
 * Titles are resolved while the menu is on screen.
 */
internal fun serverMenuActionIds(
    server: ProxyServerSummary,
    context: ServerRowContext,
    serverIndex: Int,
    totalServers: Int,
): List<String> {
    val canMove = context.sortMode == ProxyHomeSortMode.Default && context.searchQuery.isBlank() &&
        context.canUse(ProxyHomeActionId.MoveServer)
    return buildList {
        if (context.canUse(ProxyHomeActionId.CopyServer)) {
            server.availableCopyFormats.intersect(context.availableCopyFormats).forEach { format ->
                add("copy:" + format.name)
            }
        }
        if (
            context.canUse(ProxyHomeActionId.ShowServerQr) &&
            ProxyHomeCopyFormat.QrCode in server.availableCopyFormats &&
            ProxyHomeCopyFormat.QrCode in context.availableCopyFormats
        ) {
            add("show-qr")
        }
        if (context.canUse(ProxyHomeActionId.EditServer)) add("edit")
        if (context.canUse(ProxyHomeActionId.DeleteServer)) add("delete")
        if (server.isStrategyGroup && context.canUse(ProxyHomeActionId.OpenStrategyMemberPicker)) {
            add("strategy-members")
        }
        if (canMove && serverIndex > 0) add("move-up")
        if (canMove && serverIndex in 0 until (totalServers - 1)) add("move-down")
    }
}
