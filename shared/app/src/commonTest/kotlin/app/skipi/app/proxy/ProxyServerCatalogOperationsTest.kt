// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import features.proxy.server.model.HTTP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ProxyServerCatalogOperationsTest {
    @Test
    fun createPrependsAndPreservesAnExistingSelection() {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 7, server = HTTP(server = "selected.example")),
                ProxyServerRecord(id = 8, server = HTTP(server = "other.example")),
            ),
            nextServerId = 10,
            selectedServerId = 7,
        )

        val created = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "new.example"),
            sourceSubscriptionId = 42,
        )

        assertEquals(listOf(10, 7, 8), created.servers.map(ProxyServerRecord::id))
        assertEquals(listOf(42, null, null), created.servers.map(ProxyServerRecord::sourceSubscriptionId))
        assertEquals(11, created.nextServerId)
        assertEquals(7, created.selectedServerId)
    }

    @Test
    fun createSelectsTheFirstAvailableServerWhenTheOldSelectionIsMissing() {
        val catalog = ProxyServerCatalog(nextServerId = 10, selectedServerId = 1)

        val created = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "first.example"),
            sourceSubscriptionId = null,
        )

        assertEquals(listOf(10), created.servers.map(ProxyServerRecord::id))
        assertEquals(10, created.selectedServerId)
    }

    @Test
    fun editReplacesTheServerAtItsCurrentPositionAndPreservesIdentityAndMetadata() {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 12, server = HTTP(server = "twelve.example")),
                ProxyServerRecord(
                    id = 8,
                    server = HTTP(server = "old.example"),
                    sourceSubscriptionId = 55,
                    enabled = false,
                ),
                ProxyServerRecord(id = 3, server = HTTP(server = "three.example")),
            ),
            nextServerId = 40,
            selectedServerId = 3,
        )

        val edited = editProxyServerRecord(
            catalog = catalog,
            serverId = 8,
            server = HTTP(server = "edited.example"),
        )

        assertEquals(listOf(12, 8, 3), edited.servers.map(ProxyServerRecord::id))
        assertEquals(HTTP(server = "edited.example"), edited.servers[1].server)
        assertEquals(55, edited.servers[1].sourceSubscriptionId)
        assertEquals(false, edited.servers[1].enabled)
        assertEquals(40, edited.nextServerId)
        assertEquals(3, edited.selectedServerId)
    }

    @Test
    fun createAfterAnImportTakesTheDraftIdUsesTheCurrentCatalogAndKeepsBothServers() {
        // The editor opened while 10 was the suggested ID. An import then persisted 10
        // and advanced the current counter to 11 before the editor was saved.
        val catalogAfterImport = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 10, server = HTTP(server = "imported.example")),
                ProxyServerRecord(id = 4, server = HTTP(server = "existing.example")),
            ),
            nextServerId = 11,
            selectedServerId = 4,
        )

        val created = createProxyServerRecord(
            catalog = catalogAfterImport,
            server = HTTP(server = "manual.example"),
            sourceSubscriptionId = null,
        )

        assertEquals(listOf(11, 10, 4), created.servers.map(ProxyServerRecord::id))
        assertEquals("manual.example", (created.servers[0].server as HTTP).server)
        assertEquals("imported.example", (created.servers[1].server as HTTP).server)
        assertEquals(12, created.nextServerId)
        assertEquals(4, created.selectedServerId)
    }

    @Test
    fun successiveCreatesUseTheUpdatedCatalogAndAdvanceIds() {
        val catalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 7, server = HTTP(server = "existing.example"))),
            nextServerId = 10,
            selectedServerId = 7,
        )

        val first = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "first.example"),
            sourceSubscriptionId = null,
        )
        val second = createProxyServerRecord(
            catalog = first,
            server = HTTP(server = "second.example"),
            sourceSubscriptionId = 42,
        )

        assertEquals(listOf(11, 10, 7), second.servers.map(ProxyServerRecord::id))
        assertEquals(12, second.nextServerId)
        assertEquals(7, second.selectedServerId)
    }

    @Test
    fun createRespectsTheCatalogHighWaterMarkEvenWhenNoRecordUsesIt() {
        val catalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 20, server = HTTP(server = "twenty.example"))),
            nextServerId = 25,
            selectedServerId = 20,
        )

        val created = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "new.example"),
            sourceSubscriptionId = null,
        )

        assertEquals(listOf(25, 20), created.servers.map(ProxyServerRecord::id))
        assertEquals(26, created.nextServerId)
    }

    @Test
    fun createDoesNotReuseIdsBelowTheHighestPersistedServerWhenCounterIsStale() {
        val catalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 20, server = HTTP(server = "twenty.example"))),
            nextServerId = 10,
            selectedServerId = 20,
        )

        val created = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "new.example"),
            sourceSubscriptionId = null,
        )

        assertEquals(listOf(21, 20), created.servers.map(ProxyServerRecord::id))
        assertEquals(22, created.nextServerId)
        assertEquals(20, created.selectedServerId)
    }

    @Test
    fun createPreservesASelectedRetainedServerId() {
        val catalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 42, server = HTTP(server = "visible.example"))),
            nextServerId = 100,
            selectedServerId = 99,
            retainedServerIds = setOf(99),
        )

        val created = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "new.example"),
            sourceSubscriptionId = null,
        )

        assertEquals(listOf(100, 42), created.servers.map(ProxyServerRecord::id))
        assertEquals(99, created.selectedServerId)
        assertEquals(setOf(99), created.retainedServerIds)
    }

    @Test
    fun createAllocatesAboveRetainedIdsWhenTheCatalogCounterIsStale() {
        val catalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 42, server = HTTP(server = "visible.example"))),
            nextServerId = 50,
            selectedServerId = 42,
            retainedServerIds = setOf(99),
        )

        val created = createProxyServerRecord(
            catalog = catalog,
            server = HTTP(server = "new.example"),
            sourceSubscriptionId = null,
        )

        assertEquals(listOf(100, 42), created.servers.map(ProxyServerRecord::id))
        assertEquals(101, created.nextServerId)
        assertEquals(42, created.selectedServerId)
        assertEquals(setOf(99), created.retainedServerIds)
    }

    @Test
    fun editPreservesASelectedRetainedServerIdAndItsMetadata() {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(
                    id = 42,
                    server = HTTP(server = "old.example"),
                    sourceSubscriptionId = 7,
                ),
            ),
            nextServerId = 100,
            selectedServerId = 99,
            retainedServerIds = setOf(99),
        )

        val edited = editProxyServerRecord(
            catalog = catalog,
            serverId = 42,
            server = HTTP(server = "edited.example"),
        )

        assertEquals(listOf(42), edited.servers.map(ProxyServerRecord::id))
        assertEquals(7, edited.servers.single().sourceSubscriptionId)
        assertEquals(99, edited.selectedServerId)
        assertEquals(100, edited.nextServerId)
        assertEquals(setOf(99), edited.retainedServerIds)
    }

    @Test
    fun editingARecordRemovedWhileTheEditorWasOpenRestoresItsCapturedIdAndGroup() {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 12, server = HTTP(server = "twelve.example")),
                ProxyServerRecord(id = 3, server = HTTP(server = "selected.example")),
            ),
            nextServerId = 50,
            selectedServerId = 3,
        )

        val restored = editProxyServerRecord(
            catalog = catalog,
            serverId = 8,
            server = HTTP(server = "restored.example"),
            sourceSubscriptionId = 77,
        )

        assertEquals(listOf(8, 12, 3), restored.servers.map(ProxyServerRecord::id))
        assertEquals(HTTP(server = "restored.example"), restored.servers.first().server)
        assertEquals(77, restored.servers.first().sourceSubscriptionId)
        assertEquals(50, restored.nextServerId)
        assertEquals(3, restored.selectedServerId)
    }

    @Test
    fun createRejectsNonPositiveCatalogCountersInsteadOfProducingInvalidIds() {
        listOf(0, -1).forEach { invalidNextServerId ->
            val catalog = ProxyServerCatalog(nextServerId = invalidNextServerId)

            assertFails {
                createProxyServerRecord(
                    catalog = catalog,
                    server = HTTP(server = "must-not-be-saved.example"),
                    sourceSubscriptionId = null,
                )
            }
            assertEquals(emptyList(), catalog.servers)
            assertEquals(invalidNextServerId, catalog.nextServerId)
        }
    }

    @Test
    fun createAtTheIntMaxBoundaryFailsWithoutChangingTheCatalog() {
        val catalogsAtExhaustion = listOf(
            ProxyServerCatalog(
                servers = mutableListOf(ProxyServerRecord(id = Int.MAX_VALUE - 1, server = HTTP(server = "last.example"))),
                nextServerId = Int.MAX_VALUE,
                selectedServerId = Int.MAX_VALUE - 1,
            ),
            ProxyServerCatalog(
                servers = mutableListOf(ProxyServerRecord(id = Int.MAX_VALUE, server = HTTP(server = "last.example"))),
                nextServerId = 1,
                selectedServerId = Int.MAX_VALUE,
            ),
        )

        catalogsAtExhaustion.forEach { catalog ->
            val original = catalog.copy(servers = catalog.servers.toList())
            assertFails {
                createProxyServerRecord(
                    catalog = catalog,
                    server = HTTP(server = "must-not-be-saved.example"),
                    sourceSubscriptionId = null,
                )
            }
            assertEquals(original, catalog)
        }
    }
}
