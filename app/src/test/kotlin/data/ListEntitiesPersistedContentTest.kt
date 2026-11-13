// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.ProxyServerState
import features.proxy.server.model.VLESS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListEntitiesPersistedContentTest {
    private fun server(id: Int, host: String = "node$id.example", group: Int = 0) = ProxyServerState(
        id = id,
        server = VLESS(remarks = "n$id", id = "uuid$id", server = host, port = "443"),
        groupId = group,
    )

    @Test
    fun sameLists_areSame() {
        assertTrue(listOf(server(1), server(2)).hasSamePersistedContent(listOf(server(1), server(2))))
    }

    @Test
    fun changedServer_isDifferent() {
        assertFalse(listOf(server(1)).hasSamePersistedContent(listOf(server(1, host = "other.example"))))
    }

    @Test
    fun changedGroup_isDifferent() {
        assertFalse(listOf(server(1)).hasSamePersistedContent(listOf(server(1, group = 3))))
    }

    @Test
    fun reorder_isDifferent() {
        assertFalse(listOf(server(1), server(2)).hasSamePersistedContent(listOf(server(2), server(1))))
    }

    @Test
    fun latencyOnlyChange_isSame() {
        assertTrue(listOf(server(1)).hasSamePersistedContent(listOf(server(1).copy(latency = "42"))))
    }
}
