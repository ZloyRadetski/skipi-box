// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionProviderLibraryTest {
    @Test
    fun addOrReplaceKeepsStableIdAndProviderOptionsWhileMergingFetchedMetadata() {
        val existing = StoredSubscription(
            id = 11,
            url = "https://example.com/sub",
            userAgent = "Old agent",
            name = "Old name",
            metadata = StoredSubscriptionMetadata(description = "Previous", trafficTotalBytes = 400, lastUpdatedAtMillis = 10),
            enabled = false,
            updateInterval = "6",
            ageSecretKey = "AGE-SECRET",
            updateViaProxy = true,
        )

        val result = SubscriptionProviderLibraries.addOrReplace(
            library = SubscriptionProviderLibrary(listOf(existing)),
            url = " https://example.com/sub ",
            userAgent = "New agent",
            metadata = SubscriptionMetadata(profileDescription = "Current"),
            nowMillis = 99,
        ).subscriptions.single()

        assertEquals(11, result.id)
        assertEquals("New agent", result.userAgent)
        assertEquals("Current", result.metadata.description)
        assertEquals(400, result.metadata.trafficTotalBytes)
        assertEquals(99, result.metadata.lastUpdatedAtMillis)
        assertFalse(result.enabled)
        assertEquals("6", result.updateInterval)
        assertEquals("AGE-SECRET", result.ageSecretKey)
        assertTrue(result.updateViaProxy)
    }

    @Test
    fun providerEditRetainsFetchedMetadataAndRejectsUnsafeOrDuplicateValues() {
        val first = StoredSubscription(
            id = 1,
            url = "https://example.com/one",
            metadata = StoredSubscriptionMetadata(description = "Fetched"),
        )
        val second = StoredSubscription(id = 2, url = "https://example.com/two")
        val library = SubscriptionProviderLibrary(listOf(first, second))
        val updated = SubscriptionProviderLibraries.updateProvider(
            library,
            first.id,
            first.toProviderEdit().copy(url = " https://example.com/new ", userAgent = " Agent ", updateInterval = "0.25"),
        ).subscriptions.first()

        assertEquals(first.id, updated.id)
        assertEquals(first.metadata, updated.metadata)
        assertEquals("https://example.com/new", updated.url)
        assertEquals("Agent", updated.userAgent)
        assertEquals("0.25", updated.updateInterval)
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.updateProvider(library, first.id, first.toProviderEdit().copy(url = second.url))
        }
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.updateProvider(library, first.id, first.toProviderEdit().copy(userAgent = "bad\nagent"))
        }
        assertEquals("https://example.com/one", library.subscriptions.first().url)
    }

    @Test
    fun removeDeletesOnlyRequestedProvider() {
        val library = SubscriptionProviderLibrary(
            listOf(StoredSubscription(1, "https://example.com/one"), StoredSubscription(2, "https://example.com/two")),
        )

        assertEquals(listOf(2), SubscriptionProviderLibraries.remove(library, 1).subscriptions.map { it.id })
    }
}
