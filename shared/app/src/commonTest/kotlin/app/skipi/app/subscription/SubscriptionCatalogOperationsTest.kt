// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.subscription.SubscriptionExpiryReminder
import features.subscription.SubscriptionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionCatalogOperationsTest {
    @Test
    fun manualGroupCreationUsesLatestHighWaterCounterAndKeepsBlankUrl() {
        val catalog = SubscriptionCatalog(
            subscriptions = listOf(
                record(id = 3, title = "First", url = "https://first.example/sub"),
                record(id = 8, title = "Second", url = "https://second.example/sub"),
            ),
            nextSubscriptionId = 14,
        )

        val created = SubscriptionCatalogOperations.createEditor(
            catalog = catalog,
            proxyCatalog = ProxyServerCatalog(),
            draft = record(
            // This is the ID displayed when the editor opened. The latest catalog counter wins.
            id = 4,
            title = "Manual servers",
            url = "",
            builtIn = false,
            ),
        )

        assertEquals(14, created.savedSubscription?.id)
        assertEquals(15, created.catalog.nextSubscriptionId)
        assertEquals(listOf(3, 8, 14), created.catalog.subscriptions.map { it.id })
        assertEquals("", created.catalog.subscriptions.last().url)
        assertFalse(created.catalog.subscriptions.last().builtIn)
    }

    @Test
    fun twoDraftsWithTheSameDisplayedIdAllocateDistinctIdsFromLatestCatalog() {
        val initial = SubscriptionCatalog(
            subscriptions = listOf(
                record(id = 3, title = "First", url = "https://first.example/sub"),
                record(id = 8, title = "Second", url = "https://second.example/sub"),
            ),
            nextSubscriptionId = 14,
        )
        val staleDraft = record(id = 4, title = "Provider", url = "https://provider.example/sub")

        val first = SubscriptionCatalogOperations.createEditor(initial, ProxyServerCatalog(), staleDraft)
        val second = SubscriptionCatalogOperations.createEditor(first.catalog, first.proxyCatalog, staleDraft)

        assertEquals(14, first.savedSubscription?.id)
        assertEquals(15, second.savedSubscription?.id)
        assertEquals(listOf(3, 8, 14, 15), second.catalog.subscriptions.map { it.id })
        assertEquals(16, second.catalog.nextSubscriptionId)
    }

    @Test
    fun upsertReplacesInPlaceAndAllowsRefreshToUpdateBuiltinTitle() {
        val originalBuiltin = record(id = 1, title = "Default", url = "", builtIn = true)
        val catalog = SubscriptionCatalog(
            subscriptions = listOf(
                record(id = 3, title = "First", url = "https://first.example/sub"),
                originalBuiltin,
                record(id = 8, title = "Last", url = "https://last.example/sub"),
            ),
            nextSubscriptionId = 9,
        )

        val updated = SubscriptionCatalogOperations.upsert(
            catalog,
            originalBuiltin.copy(title = "Provider title"),
        )

        assertEquals(listOf(3, 1, 8), updated.subscriptions.map { it.id })
        assertEquals("Provider title", updated.subscriptions[1].title)
        assertTrue(updated.subscriptions[1].builtIn)
    }

    @Test
    fun editorSaveCopiesLatestMetadataAndUpdatesOnlyLinkedCustomOverrides() {
        val staleEditorSnapshot = record(
            id = 5,
            title = "Manual folder",
            url = "",
            autoOverrideRules = true,
            metadata = SubscriptionMetadata(profileTitle = "Old fetched title", profileDescription = "Old metadata"),
            lastUpdatedAtMillis = 1234,
        )
        val latestPersisted = staleEditorSnapshot.copy(
            metadata = SubscriptionMetadata(profileTitle = "Latest fetched title", profileDescription = "Latest metadata"),
            lastUpdatedAtMillis = 5678,
        )
        val neighbor = record(id = 9, title = "Neighbor", url = "https://neighbor.example/sub")
        val customLinked = ProxyServerRecord(
            id = 41,
            server = Custom(remarks = "linked", overrideInboundAndDns = true),
            sourceSubscriptionId = 5,
        )
        val ordinaryLinked = ProxyServerRecord(
            id = 42,
            server = HTTP(server = "ordinary.example"),
            sourceSubscriptionId = 5,
        )
        val customNeighbor = ProxyServerRecord(
            id = 43,
            server = Custom(remarks = "other", overrideInboundAndDns = true),
            sourceSubscriptionId = 9,
        )
        val catalog = SubscriptionCatalog(listOf(latestPersisted, neighbor), nextSubscriptionId = 10)
        val proxyCatalog = ProxyServerCatalog(
            servers = listOf(customLinked, ordinaryLinked, customNeighbor),
            nextServerId = 44,
            selectedServerId = 41,
        )

        val result = SubscriptionCatalogOperations.saveEditor(
            catalog = catalog,
            proxyCatalog = proxyCatalog,
            subscription = staleEditorSnapshot.copy(
                title = "Renamed folder",
                url = "",
                userAgent = "Edited agent",
                updateInterval = "12",
                autoOverrideRules = false,
            ),
        )

        assertEquals(5, result.savedSubscription?.id)
        assertEquals(listOf(5, 9), result.catalog.subscriptions.map { it.id })
        assertEquals("Renamed folder", result.catalog.subscriptions[0].title)
        assertEquals("", result.catalog.subscriptions[0].url)
        assertEquals("Edited agent", result.catalog.subscriptions[0].userAgent)
        assertEquals("12", result.catalog.subscriptions[0].updateInterval)
        assertEquals(false, result.catalog.subscriptions[0].autoOverrideRules)
        assertEquals(latestPersisted.metadata, result.catalog.subscriptions[0].metadata)
        assertEquals(5678, result.catalog.subscriptions[0].lastUpdatedAtMillis)
        assertEquals(neighbor, result.catalog.subscriptions[1])
        assertEquals(false, (result.proxyCatalog.servers[0].server as Custom).overrideInboundAndDns)
        assertEquals(ordinaryLinked, result.proxyCatalog.servers[1])
        assertEquals(customNeighbor, result.proxyCatalog.servers[2])
    }

    @Test
    fun editorSaveDoesNotRecreateAGroupDeletedWhileTheEditorWasOpen() {
        val remaining = record(id = 9, title = "Neighbor", url = "https://neighbor.example/sub")
        val catalog = SubscriptionCatalog(listOf(remaining), nextSubscriptionId = 10)
        val deletedDraft = record(id = 5, title = "Deleted", url = "https://deleted.example/sub")

        val result = SubscriptionCatalogOperations.saveEditor(catalog, ProxyServerCatalog(), deletedDraft)

        assertEquals(null, result.savedSubscription)
        assertEquals(listOf(remaining), result.catalog.subscriptions)
        assertEquals(10, result.catalog.nextSubscriptionId)
    }

    @Test
    fun builtInManualEditorKeepsIdentityTitleEnabledAndBuiltInWhileSavingOptions() {
        val builtIn = record(id = 1, title = "Default manual group", url = "", builtIn = true)
        val catalog = SubscriptionCatalog(listOf(builtIn), nextSubscriptionId = 4)
        val draft = builtIn.copy(
            title = "Changed title",
            url = "https://manual.example/sub",
            userAgent = "Edited agent",
            ageSecretKey = "secret",
            updateViaProxy = true,
            enabled = false,
            builtIn = false,
        )

        val saved = SubscriptionCatalogOperations.saveEditor(catalog, ProxyServerCatalog(), draft)
        val toggled = SubscriptionCatalogOperations.setEnabled(saved.catalog, subscriptionId = 1, enabled = false)
        val result = toggled.subscriptions.single()

        assertEquals(1, saved.savedSubscription?.id)
        assertEquals("Default manual group", result.title)
        assertTrue(result.enabled)
        assertTrue(result.builtIn)
        assertEquals("https://manual.example/sub", result.url)
        assertEquals("Edited agent", result.userAgent)
        assertEquals("secret", result.ageSecretKey)
        assertTrue(result.updateViaProxy)
    }

    @Test
    fun movePreservesFixedAndBuiltinSlotsAndDesktopCanMoveOrdinaryIdOne() {
        val catalog = SubscriptionCatalog(
            subscriptions = listOf(
                record(id = 1, title = "Android manual", url = "", builtIn = true),
                record(id = 2, title = "First", url = "https://first.example/sub"),
                record(id = 3, title = "Built in slot", url = "", builtIn = true),
                record(id = 4, title = "Second", url = "https://second.example/sub"),
                record(id = 5, title = "Third", url = "https://third.example/sub"),
            ),
            nextSubscriptionId = 6,
        )

        val moved = SubscriptionCatalogOperations.move(catalog, groupId = 5, offset = -1, fixedGroupId = 1)
        val movedOrdinaryIdOne = SubscriptionCatalogOperations.move(
            catalog = SubscriptionCatalog(
                subscriptions = listOf(
                    record(id = 1, title = "Desktop ID one", url = "https://one.example/sub"),
                    record(id = 2, title = "Desktop ID two", url = "https://two.example/sub"),
                ),
                nextSubscriptionId = 3,
            ),
            groupId = 1,
            offset = 1,
            fixedGroupId = null,
        )

        assertEquals(listOf(1, 2, 3, 5, 4), moved.subscriptions.map { it.id })
        assertEquals(listOf(2, 1), movedOrdinaryIdOne.subscriptions.map { it.id })
        assertEquals(catalog.subscriptions, SubscriptionCatalogOperations.move(catalog, 1, 1, fixedGroupId = 1).subscriptions)
        assertEquals(catalog.subscriptions, SubscriptionCatalogOperations.move(catalog, 3, 1, fixedGroupId = 1).subscriptions)
    }

    @Test
    fun removeRejectsBuiltinButCleansLinkedRowsAndCompositeReferencesForOrdinaryIdOne() {
        val catalog = SubscriptionCatalog(
            subscriptions = listOf(
                record(id = 1, title = "Desktop ID one", url = "https://one.example/sub", builtIn = false),
                record(id = 2, title = "Remaining", url = "https://two.example/sub"),
            ),
            nextSubscriptionId = 3,
        )
        val proxyCatalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(10, HTTP(server = "linked.example"), sourceSubscriptionId = 1),
                ProxyServerRecord(11, HTTP(server = "remaining.example"), sourceSubscriptionId = 2),
                ProxyServerRecord(
                    20,
                    StrategyGroup(remarks = "Strategy", proxyServerIds = listOf(10, 11), selectedMemberId = 10),
                ),
                ProxyServerRecord(21, ChainProxy(remarks = "Chain", proxyServerIds = listOf(10, 11))),
            ),
            nextServerId = 99,
            selectedServerId = 10,
        )

        val result = SubscriptionCatalogOperations.remove(catalog, proxyCatalog, subscriptionId = 1)

        assertEquals(listOf(2), result.catalog.subscriptions.map { it.id })
        assertEquals(listOf(11, 20, 21), result.proxyCatalog.servers.map { it.id })
        assertEquals(listOf(11), (result.proxyCatalog.servers[1].server as StrategyGroup).proxyServerIds)
        assertEquals(11, (result.proxyCatalog.servers[1].server as StrategyGroup).selectedMemberId)
        assertEquals(listOf(11), (result.proxyCatalog.servers[2].server as ChainProxy).proxyServerIds)
        assertEquals(11, result.proxyCatalog.selectedServerId)
        assertEquals(99, result.proxyCatalog.nextServerId)
        assertTrue(result.shouldStopProxy)

        assertFailsWith<IllegalArgumentException> {
            SubscriptionCatalogOperations.remove(
                catalog = SubscriptionCatalog(
                    subscriptions = listOf(record(id = 1, title = "Built in", url = "", builtIn = true)),
                    nextSubscriptionId = 2,
                ),
                proxyCatalog = proxyCatalog,
                subscriptionId = 1,
            )
        }
    }

    private fun record(
        id: Int,
        title: String,
        url: String,
        userAgent: String = "test-agent",
        updateInterval: String = "6",
        hwid: String = "",
        ageSecretKey: String = "",
        updateViaProxy: Boolean = false,
        autoOverrideRules: Boolean = true,
        enabled: Boolean = true,
        builtIn: Boolean = false,
        metadata: SubscriptionMetadata? = null,
        lastUpdatedAtMillis: Long? = null,
        notifyOnExpiry: Boolean = true,
        customExpiryReminders: List<SubscriptionExpiryReminder>? = null,
    ) = SubscriptionRecord(
        id = id,
        title = title,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        metadata = metadata,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )
}
