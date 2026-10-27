// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.moveProxyHomeItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeServerDragReorderTest {
    private data class Server(val id: String, val remarks: String)

    @Test
    fun searchVisibleSourceAndTargetIdsReorderTheWholeCatalog() {
        val source = Server(id = "source", remarks = "matching first")
        val hidden = Server(id = "hidden", remarks = "not visible in search")
        val target = Server(id = "target", remarks = "matching second")
        val catalog = listOf(source, hidden, target)
        val visiblePage = catalog.filter { it.remarks.contains("matching") }

        val reordered = moveProxyHomeItem(
            items = catalog,
            fromId = visiblePage.first().id,
            toId = visiblePage.last().id,
            idOf = Server::id,
        )

        assertEquals(listOf("hidden", "target", "source"), reordered.map(Server::id))
    }
}
