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

    @Test
    fun addManualGroupAssignsSequentialIdsPreservesOrderAndLeavesExistingEntriesIntact() {
        val existing = StoredSubscription(
            id = 5,
            url = "https://example.com/sub",
            userAgent = "Agent",
            name = "Existing",
            metadata = StoredSubscriptionMetadata(description = "Meta"),
            enabled = false,
        )
        val initialLibrary = SubscriptionProviderLibrary(listOf(existing))

        val library1 = SubscriptionProviderLibraries.addManualGroup(initialLibrary, "  Manual Group 1  ")
        val library2 = SubscriptionProviderLibraries.addManualGroup(library1, "Manual Group 2")
        val library3 = SubscriptionProviderLibraries.addManualGroup(library2, "Manual Group 2")

        assertEquals(4, library3.subscriptions.size)
        assertEquals(listOf(5, 6, 7, 8), library3.subscriptions.map { it.id })
        assertEquals(existing, library3.subscriptions[0])

        val firstGroup = library3.subscriptions[1]
        assertEquals(6, firstGroup.id)
        assertEquals("", firstGroup.url)
        assertEquals("Manual Group 1", firstGroup.name)
        assertTrue(firstGroup.enabled)
        assertEquals("", firstGroup.userAgent)
        assertEquals(StoredSubscriptionMetadata(), firstGroup.metadata)
        assertEquals("", firstGroup.updateInterval)
        assertEquals("", firstGroup.ageSecretKey)
        assertFalse(firstGroup.updateViaProxy)
        assertTrue(firstGroup.autoOverrideRules)
        assertTrue(firstGroup.notifyOnExpiry)

        val secondGroup = library3.subscriptions[2]
        assertEquals(7, secondGroup.id)
        assertEquals("", secondGroup.url)
        assertEquals("Manual Group 2", secondGroup.name)

        val thirdGroup = library3.subscriptions[3]
        assertEquals(8, thirdGroup.id)
        assertEquals("", thirdGroup.url)
        assertEquals("Manual Group 2", thirdGroup.name)
    }

    @Test
    fun addManualGroupGeneratesIdOneForEmptyLibrary() {
        val result = SubscriptionProviderLibraries.addManualGroup(SubscriptionProviderLibrary(), "Custom Group")
        val created = result.subscriptions.single()
        assertEquals(1, created.id)
        assertEquals("Custom Group", created.name)
        assertEquals("", created.url)
        assertTrue(created.enabled)
    }

    @Test
    fun addManualGroupFindsFreePositiveIdWhenMaxIntIsPresent() {
        val sub1 = StoredSubscription(id = 1, url = "https://example.com/one")
        val subMax = StoredSubscription(id = Int.MAX_VALUE, url = "https://example.com/max")
        val library = SubscriptionProviderLibrary(listOf(sub1, subMax))

        val result = SubscriptionProviderLibraries.addManualGroup(library, "Overflow Safe Group")
        val newGroup = result.subscriptions.last()

        assertEquals(2, newGroup.id)
        assertEquals("Overflow Safe Group", newGroup.name)
        assertEquals("", newGroup.url)
        assertTrue(newGroup.id > 0)
        assertEquals(3, result.subscriptions.size)
    }

    @Test
    fun addManualGroupRejectsBlankOrEmptyName() {
        val library = SubscriptionProviderLibrary()
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.addManualGroup(library, "")
        }
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.addManualGroup(library, "   ")
        }
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.addManualGroup(library, "\t\n  \r")
        }
    }

    @Test
    fun updateProviderAllowsEmptyUrlAndMultipleGroupsWithEmptyUrl() {
        val sub1 = StoredSubscription(id = 1, url = "https://example.com/one", name = "Sub 1")
        val sub2 = StoredSubscription(id = 2, url = "https://example.com/two", name = "Sub 2")
        val library = SubscriptionProviderLibrary(listOf(sub1, sub2))

        val updated1 = SubscriptionProviderLibraries.updateProvider(
            library,
            sub1.id,
            sub1.toProviderEdit().copy(url = "   ", name = "Converted to Manual 1"),
        )
        val updated2 = SubscriptionProviderLibraries.updateProvider(
            updated1,
            sub2.id,
            sub2.toProviderEdit().copy(url = "", name = "Converted to Manual 2"),
        )

        assertEquals("", updated2.subscriptions[0].url)
        assertEquals("Converted to Manual 1", updated2.subscriptions[0].name)
        assertEquals("", updated2.subscriptions[1].url)
        assertEquals("Converted to Manual 2", updated2.subscriptions[1].name)
    }

    @Test
    fun updateProviderRejectsInvalidNonEmptyUrlAndDuplicateNonEmptyUrl() {
        val first = StoredSubscription(id = 1, url = "https://example.com/one")
        val second = StoredSubscription(id = 2, url = "https://example.com/two")
        val library = SubscriptionProviderLibrary(listOf(first, second))

        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.updateProvider(
                library,
                first.id,
                first.toProviderEdit().copy(url = "not_a_valid_url"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.updateProvider(
                library,
                first.id,
                first.toProviderEdit().copy(url = "https://example.com/two"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.updateProvider(
                library,
                first.id,
                first.toProviderEdit().copy(url = "https://example.com/bad\nurl"),
            )
        }
    }

    @Test
    fun addOrReplaceStillRejectsEmptyOrBlankUrl() {
        val library = SubscriptionProviderLibrary()
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.addOrReplace(library, "", nowMillis = 100L)
        }
        assertFailsWith<IllegalArgumentException> {
            SubscriptionProviderLibraries.addOrReplace(library, "   ", nowMillis = 100L)
        }
    }

    @Test
    fun updateProviderRetainsMetadataWhenEditingToEmptyUrl() {
        val originalMetadata = StoredSubscriptionMetadata(
            description = "Some description",
            trafficTotalBytes = 1024L,
            lastUpdatedAtMillis = 500L,
        )
        val sub = StoredSubscription(
            id = 10,
            url = "https://example.com/sub",
            name = "Sub",
            metadata = originalMetadata,
        )
        val library = SubscriptionProviderLibrary(listOf(sub))
        val updated = SubscriptionProviderLibraries.updateProvider(
            library,
            sub.id,
            sub.toProviderEdit().copy(url = "", name = "Now Manual"),
        ).subscriptions.single()

        assertEquals(10, updated.id)
        assertEquals("", updated.url)
        assertEquals("Now Manual", updated.name)
        assertEquals(originalMetadata, updated.metadata)
    }
}
