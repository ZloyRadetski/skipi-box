// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionExpiryReminder
import features.subscription.SubscriptionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionInstallOperationsTest {
    @Test
    fun exact_url_reuses_the_entire_record_and_leaves_catalog_counter_unchanged() {
        val existing = subscription(
            id = 15,
            title = "Saved name",
            url = "https://example.test/profile",
            userAgent = "Saved UA",
            updateInterval = "12",
            hwid = "saved-hwid",
            ageSecretKey = "saved-secret",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = false,
            builtIn = true,
            metadata = SubscriptionMetadata(
                profileTitle = "Saved profile title",
                announce = "Saved announcement",
                supportUrl = "https://support.example.test/",
                supportEmail = "support@example.test",
                profileWebPageUrl = "https://profile.example.test/",
                announceUrl = "https://announce.example.test/",
                userInfoReceived = true,
                trafficUploadBytes = 101L,
                trafficDownloadBytes = 202L,
                trafficTotalBytes = 303L,
                trafficExpireAtSeconds = 404L,
                profileUpdateIntervalHours = "8",
            ),
            lastUpdatedAtMillis = 505L,
            notifyOnExpiry = false,
            customExpiryReminders = listOf(SubscriptionExpiryReminder(5, ExpiryReminderUnit.Days)),
        )
        val neighbor = subscription(
            id = 8,
            title = "Neighbor",
            url = "https://neighbor.example.test/profile",
        )
        val catalog = SubscriptionCatalog(
            subscriptions = listOf(existing, neighbor),
            nextSubscriptionId = 21,
        )

        val prepared = SubscriptionInstallOperations.prepare(
            catalog = catalog,
            title = "Incoming name",
            url = existing.url,
            userAgent = "Incoming UA",
        )

        assertEquals(catalog, prepared.catalog)
        assertEquals(existing, prepared.subscription)
        assertEquals("Saved name", prepared.existingTitle)
    }

    @Test
    fun a_distinct_url_appends_a_new_record_with_android_defaults_and_next_counter_id() {
        val existing = subscription(
            id = 15,
            title = "Existing",
            url = "https://example.test/profile",
        )
        val catalog = SubscriptionCatalog(
            subscriptions = listOf(existing),
            nextSubscriptionId = 21,
        )

        val prepared = SubscriptionInstallOperations.prepare(
            catalog = catalog,
            title = "Incoming name",
            url = "https://example.test/profile/",
            userAgent = "Imported UA",
        )

        val created = prepared.subscription
        assertEquals(21, created.id)
        assertEquals("Incoming name", created.title)
        assertEquals("https://example.test/profile/", created.url)
        assertEquals("Imported UA", created.userAgent)
        assertEquals("", created.updateInterval)
        assertEquals("", created.hwid)
        assertEquals("", created.ageSecretKey)
        assertFalse(created.updateViaProxy)
        assertTrue(created.autoOverrideRules)
        assertTrue(created.enabled)
        assertFalse(created.builtIn)
        // Android's current SubscriptionGroupState-to-record adapter emits an empty metadata value.
        assertEquals(SubscriptionMetadata(), created.metadata)
        assertNull(created.lastUpdatedAtMillis)
        assertTrue(created.notifyOnExpiry)
        assertNull(created.customExpiryReminders)
        assertEquals(listOf(existing, created), prepared.catalog.subscriptions)
        assertEquals(22, prepared.catalog.nextSubscriptionId)
        assertNull(prepared.existingTitle)
    }
}

private fun subscription(
    id: Int,
    title: String,
    url: String,
    userAgent: String = "",
    updateInterval: String = "",
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
