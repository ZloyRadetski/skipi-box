// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.repository.SubscriptionRepository
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionInstallAndRefreshTest {
    @Test
    fun new_install_is_persisted_before_one_refresh_and_keeps_the_row_if_refresh_fails() = runTest {
        val existing = subscription(id = 4, title = "Existing", url = "https://old.example/sub")
        val repository = InMemorySubscriptionRepository(
            SubscriptionCatalog(subscriptions = listOf(existing), nextSubscriptionId = 9),
        )
        val refreshFailure = IllegalStateException("network unavailable")
        var refreshCalls = 0
        var recordSeenByRefresh: SubscriptionRecord? = null

        val result = SubscriptionInstallOperations.installAndRefresh(
            repository = repository,
            title = "Imported",
            url = "https://new.example/sub",
            userAgent = "Imported UA",
        ) { record ->
            refreshCalls += 1
            recordSeenByRefresh = record
            assertEquals(record, repository.catalog.value.subscriptions.last())
            assertEquals(9, record.id)
            Result.failure<Unit>(refreshFailure)
        }

        val installed = repository.catalog.value.subscriptions.last()
        assertEquals(1, repository.catalogWrites)
        assertEquals(1, refreshCalls)
        assertEquals(installed, recordSeenByRefresh)
        assertEquals("Imported", installed.title)
        assertEquals("https://new.example/sub", installed.url)
        assertEquals("Imported UA", installed.userAgent)
        assertEquals("", installed.updateInterval)
        assertEquals("", installed.hwid)
        assertEquals("", installed.ageSecretKey)
        assertEquals(false, installed.updateViaProxy)
        assertEquals(true, installed.autoOverrideRules)
        assertEquals(true, installed.enabled)
        assertEquals(false, installed.builtIn)
        assertEquals(SubscriptionMetadata(), installed.metadata)
        assertEquals(null, installed.lastUpdatedAtMillis)
        assertEquals(true, installed.notifyOnExpiry)
        assertEquals(null, installed.customExpiryReminders)
        assertEquals(10, repository.catalog.value.nextSubscriptionId)
        assertEquals(refreshFailure, result.refreshResult.exceptionOrNull())
        assertEquals("https://old.example/sub", repository.catalog.value.subscriptions.first().url)
    }

    @Test
    fun exact_url_duplicate_is_returned_in_full_and_still_gets_one_install_refresh_when_disabled() = runTest {
        val existing = subscription(
            id = 15,
            title = "Saved title",
            url = "https://example.test/sub",
        ).copy(
            userAgent = "Saved UA",
            updateInterval = "12",
            hwid = "saved-hwid",
            ageSecretKey = "saved-age-key",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = false,
            builtIn = true,
            metadata = SubscriptionMetadata(profileTitle = "Saved profile"),
            lastUpdatedAtMillis = 505L,
            notifyOnExpiry = false,
        )
        val initialCatalog = SubscriptionCatalog(listOf(existing), nextSubscriptionId = 21)
        val repository = InMemorySubscriptionRepository(initialCatalog)
        var refreshCalls = 0
        var refreshedRecord: SubscriptionRecord? = null

        val result = SubscriptionInstallOperations.installAndRefresh(
            repository = repository,
            title = "Incoming title",
            url = existing.url,
            userAgent = "Incoming UA",
        ) { record ->
            refreshCalls += 1
            refreshedRecord = record
            "loaded once"
        }

        assertEquals(initialCatalog, repository.catalog.value)
        assertEquals(1, repository.catalogWrites)
        assertEquals(1, refreshCalls)
        assertEquals(existing, refreshedRecord)
        assertEquals(existing, result.preparation.subscription)
        assertEquals("Saved title", result.preparation.existingTitle)
        assertEquals("loaded once", result.refreshResult)
        assertEquals("Saved title", result.preparation.subscription.title)
    }
}

private class InMemorySubscriptionRepository(initial: SubscriptionCatalog) : SubscriptionRepository {
    private val mutableCatalog = MutableStateFlow(initial)
    private val mutableSubscriptions = MutableStateFlow(initial.subscriptions)
    override val subscriptions: StateFlow<List<SubscriptionRecord>> = mutableSubscriptions
    override val catalog: StateFlow<SubscriptionCatalog> = mutableCatalog
    var catalogWrites: Int = 0
        private set

    override suspend fun updateCatalog(transform: (SubscriptionCatalog) -> SubscriptionCatalog) {
        catalogWrites += 1
        val updated = transform(mutableCatalog.value)
        mutableCatalog.value = updated
        mutableSubscriptions.value = updated.subscriptions
    }

    override suspend fun upsert(subscription: SubscriptionRecord) {
        updateCatalog { current -> SubscriptionCatalogOperations.upsert(current, subscription) }
    }

    override suspend fun remove(subscriptionId: Int) {
        updateCatalog { current ->
            current.copy(subscriptions = current.subscriptions.filterNot { it.id == subscriptionId })
        }
    }

    override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> =
        Result.failure(UnsupportedOperationException("The test refresh callback owns this operation"))
}

private fun subscription(
    id: Int,
    title: String,
    url: String,
) = SubscriptionRecord(
    id = id,
    title = title,
    url = url,
    userAgent = "",
    updateInterval = "",
    hwid = "",
    ageSecretKey = "",
    updateViaProxy = false,
    autoOverrideRules = true,
    enabled = true,
    builtIn = false,
    metadata = null,
    lastUpdatedAtMillis = null,
    notifyOnExpiry = true,
    customExpiryReminders = null,
)
