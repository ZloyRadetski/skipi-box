// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

// Draft intended for app/src/test/kotlin/features/proxy/server/list/AndroidVisibleServerReorderBaselineTest.kt
// This baseline exercises the existing Android list helper before Desktop behavior changes.
package features.proxy.server.list

import app.ProxyServerState
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidVisibleServerReorderBaselineTest {
    @Test
    fun visibleNeighborMovesByIdInTheWholeCatalog() {
        val firstInGroupA = server(id = 1, groupId = 10, remarks = "A first")
        val onlyVisibleGroupB = server(id = 2, groupId = 20, remarks = "B")
        val secondInGroupA = server(id = 3, groupId = 10, remarks = "A second")
        val latestCatalog = listOf(firstInGroupA, onlyVisibleGroupB, secondInGroupA)

        // The UI displays the selected group A rows in this order, so moving 1 down targets ID 3.
        val orderedVisibleIds = listOf(1, 3)
        val sourceIndex = orderedVisibleIds.indexOf(1)
        val targetId = orderedVisibleIds[sourceIndex + 1]

        val reordered = latestCatalog.reorderVisibleServerById(fromServerId = 1, toServerId = targetId)

        assertEquals(listOf(2, 3, 1), reordered.map(ProxyServerState::id))
        assertEquals(listOf(onlyVisibleGroupB, secondInGroupA, firstInGroupA), reordered)
    }

    private fun server(id: Int, groupId: Int, remarks: String) = ProxyServerState(
        id = id,
        groupId = groupId,
        server = VLESS(
            remarks = remarks,
            id = "4219d973-8792-462f-8747-df766f70f137",
            server = "example.com",
            port = "443",
        ),
    )
}
