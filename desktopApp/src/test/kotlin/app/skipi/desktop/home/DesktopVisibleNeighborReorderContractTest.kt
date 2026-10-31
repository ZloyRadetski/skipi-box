// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/** Android's visible neighbor determines the target; the move applies to the full catalog. */
class DesktopVisibleNeighborReorderContractTest {
    @Test
    fun movingToNextVisibleGroupRowMatchesAndroidWholeCatalogMove() {
        val groupAFirst = DesktopStoredProxyServer(id = 1, serverJson = "a1", subscriptionId = 10)
        val groupBOnly = DesktopStoredProxyServer(id = 2, serverJson = "b2", subscriptionId = 20)
        val groupASecond = DesktopStoredProxyServer(id = 3, serverJson = "a3", subscriptionId = 10)
        val latestCatalog = listOf(groupAFirst, groupBOnly, groupASecond)

        // The selected group displays [1, 3], so moving row 1 down targets row ID 3.
        val orderedVisibleIds = listOf(1, 3)
        val sourceIndex = orderedVisibleIds.indexOf(1)
        val offset = 1
        val targetId = orderedVisibleIds[sourceIndex + offset]
        assertEquals(3, targetId)

        val reordered = reorderServerInLibrary(latestCatalog, fromServerId = 1, toServerId = targetId)

        // Android moves ID 1 to the position of visible neighbor ID 3 in the whole catalog.
        assertEquals(listOf(2, 3, 1), reordered?.map(DesktopStoredProxyServer::id))
    }
}
