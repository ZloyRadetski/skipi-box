// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyServerSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerRowMenuTest {
    private val server = ProxyServerSummary(
        id = "7",
        title = "Berlin",
        address = "a.example:443",
        protocol = "vless",
        availableCopyFormats = setOf(ProxyHomeCopyFormat.Url, ProxyHomeCopyFormat.QrCode),
    )

    private fun context(
        actions: Set<ProxyHomeActionId> = ProxyHomeActionId.entries.toSet(),
        busy: Set<ProxyHomeActionId> = emptySet(),
        sort: ProxyHomeSortMode = ProxyHomeSortMode.Default,
        search: String = "",
    ) = ServerRowContext(
        sortMode = sort,
        searchQuery = search,
        availableActions = actions,
        busyActions = busy,
        availableCopyFormats = setOf(ProxyHomeCopyFormat.Url, ProxyHomeCopyFormat.QrCode),
        displayOptions = ProxyHomeDisplayOptions(),
    )

    @Test
    fun middleRowOffersCopyQrEditDeleteAndBothMoves() {
        assertEquals(
            listOf("copy:Url", "copy:QrCode", "show-qr", "edit", "delete", "move-up", "move-down"),
            serverMenuActionIds(server, context(), serverIndex = 1, totalServers = 3),
        )
    }

    @Test
    fun edgeRowsOfferOnlyTheValidMoveAndSearchOrSortingDisablesMoving() {
        assertEquals(false, "move-up" in serverMenuActionIds(server, context(), 0, 3))
        assertEquals(false, "move-down" in serverMenuActionIds(server, context(), 2, 3))
        val sorted = serverMenuActionIds(server, context(sort = ProxyHomeSortMode.Name), 1, 3)
        assertEquals(false, "move-up" in sorted || "move-down" in sorted)
        val searching = serverMenuActionIds(server, context(search = "ber"), 1, 3)
        assertEquals(false, "move-up" in searching || "move-down" in searching)
    }

    @Test
    fun unavailableOrBusyActionsDisappearFromTheMenu() {
        val none = serverMenuActionIds(server, context(actions = emptySet()), 1, 3)
        assertEquals(emptyList(), none)
        val busyDelete = serverMenuActionIds(server, context(busy = setOf(ProxyHomeActionId.DeleteServer)), 1, 3)
        assertEquals(false, "delete" in busyDelete)
        assertEquals(true, "edit" in busyDelete)
    }
}
