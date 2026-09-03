package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Files
import features.subscription.SubscriptionMetadata

class DesktopSubscriptionLibraryTest {
    @Test fun persists_and_replaces_by_url() {
        val path = Files.createTempDirectory("skipi-subscriptions").resolve("subscriptions.json")
        val first = DesktopSubscriptionLibraries.addOrReplace(DesktopSubscriptionLibrary(), "https://example.com/sub", "SKIPI")
        val updated = DesktopSubscriptionLibraries.addOrReplace(first, "https://example.com/sub", "Agent", "Provider")
        DesktopSubscriptionLibraries.save(path, updated).getOrThrow()
        val stored = DesktopSubscriptionLibraries.load(path).getOrThrow().subscriptions.single()
        assertEquals("Agent", stored.userAgent); assertEquals("Provider", stored.name)
        assertEquals(0, DesktopSubscriptionLibraries.remove(updated, stored.id).subscriptions.size)
    }

    @Test fun rejects_invalid_urls() {
        assertFailsWith<IllegalArgumentException> { DesktopSubscriptionLibraries.addOrReplace(DesktopSubscriptionLibrary(), "file:///bad", "SKIPI") }
    }

    @Test fun normalizes_manual_url_before_replacing() {
        val first = DesktopSubscriptionLibraries.addOrReplace(
            DesktopSubscriptionLibrary(),
            "  https://example.com/sub  ",
            "SKIPI",
        )
        val updated = DesktopSubscriptionLibraries.addOrReplace(first, "https://example.com/sub", "Updated")

        assertEquals(1, updated.subscriptions.size)
        assertEquals("https://example.com/sub", updated.subscriptions.single().url)
        assertEquals("Updated", updated.subscriptions.single().userAgent)
    }

    @Test fun persists_server_metadata_between_desktop_restarts() {
        val updated = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(),
            url = "https://example.com/sub",
            userAgent = "SKIPI",
            metadata = SubscriptionMetadata(
                profileTitle = "Provider",
                profileDescription = "Premium nodes",
                announce = "Maintenance tonight",
                userInfoReceived = true,
                trafficUploadBytes = 10,
                trafficDownloadBytes = 20,
                trafficTotalBytes = 100,
            ),
        )
        val stored = updated.subscriptions.single()

        assertEquals("Provider", stored.name)
        assertEquals("Premium nodes", stored.metadata.description)
        assertEquals("Maintenance tonight", stored.metadata.announce)
        assertEquals(100, stored.metadata.trafficTotalBytes)
        assertEquals(30, stored.metadata.trafficUploadBytes + stored.metadata.trafficDownloadBytes)

        val preserved = DesktopSubscriptionLibraries.addOrReplace(updated, stored.url, "Updated")
        assertEquals(stored.metadata, preserved.subscriptions.single().metadata)
    }

    @Test fun keeps_traffic_metadata_when_a_successful_response_has_no_userinfo_header() {
        val withQuota = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(),
            url = "https://example.com/sub",
            userAgent = "SKIPI",
            metadata = SubscriptionMetadata(
                profileTitle = "Provider",
                userInfoReceived = true,
                trafficUploadBytes = 10,
                trafficDownloadBytes = 20,
                trafficTotalBytes = 100,
            ),
        )

        val refreshed = DesktopSubscriptionLibraries.addOrReplace(
            library = withQuota,
            url = "https://example.com/sub",
            userAgent = "SKIPI",
            metadata = SubscriptionMetadata(profileDescription = "Updated description"),
        ).subscriptions.single()

        assertEquals(10, refreshed.metadata.trafficUploadBytes)
        assertEquals(20, refreshed.metadata.trafficDownloadBytes)
        assertEquals(100, refreshed.metadata.trafficTotalBytes)
        assertEquals("Updated description", refreshed.metadata.description)
    }

    @Test fun updates_provider_fields_without_losing_id_or_fetched_metadata() {
        val before = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(),
            url = "https://example.com/old",
            userAgent = "Initial agent",
            metadata = SubscriptionMetadata(
                profileTitle = "Provider",
                profileDescription = "Retained description",
                userInfoReceived = true,
                trafficTotalBytes = 1_000,
            ),
        ).subscriptions.single()
        val library = DesktopSubscriptionLibrary(listOf(before))

        val updated = DesktopSubscriptionLibraries.updateProvider(
            library = library,
            subscriptionId = before.id,
            edit = before.toProviderEdit().copy(
                name = "Renamed provider",
                url = "https://example.com/new",
                userAgent = "Desktop editor",
                enabled = false,
                updateInterval = "6",
            ),
        ).subscriptions.single()

        assertEquals(before.id, updated.id)
        assertEquals(before.metadata, updated.metadata)
        assertEquals("Renamed provider", updated.name)
        assertEquals("https://example.com/new", updated.url)
        assertEquals("Desktop editor", updated.userAgent)
        assertFalse(updated.enabled)
        assertEquals("6", updated.updateInterval)

        val refreshed = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(listOf(updated)),
            url = updated.url,
            userAgent = "Refresh agent",
        ).subscriptions.single()
        assertFalse(refreshed.enabled)
        assertEquals("6", refreshed.updateInterval)
    }

    @Test fun rejects_unsafe_or_ambiguous_provider_updates_without_mutating_library() {
        val first = DesktopSubscriptionLibraries.addOrReplace(
            DesktopSubscriptionLibrary(),
            "https://example.com/first",
            "SKIPI",
        )
        val library = DesktopSubscriptionLibraries.addOrReplace(
            first,
            "https://example.com/second",
            "SKIPI",
        )
        val target = library.subscriptions.first()

        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                target.id,
                target.toProviderEdit().copy(url = "https://example.com/second"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                target.id,
                target.toProviderEdit().copy(updateInterval = "0.2"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                target.id,
                target.toProviderEdit().copy(userAgent = "safe\nnot-safe"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                999,
                target.toProviderEdit(),
            )
        }
        assertEquals(2, library.subscriptions.size)
        assertEquals("https://example.com/first", library.subscriptions.first().url)
    }

    @Test fun loads_legacy_json_with_safe_provider_defaults_and_persists_new_fields() {
        val path = Files.createTempDirectory("skipi-subscriptions").resolve("subscriptions.json")
        Files.writeString(
            path,
            """
            {
              "subscriptions": [
                {
                  "id": 7,
                  "url": "https://example.com/legacy",
                  "userAgent": "Legacy agent",
                  "name": "Legacy provider",
                  "metadata": { "description": "Still here" },
                  "futureDesktopField": true
                }
              ]
            }
            """.trimIndent(),
        )

        val legacy = DesktopSubscriptionLibraries.load(path).getOrThrow().subscriptions.single()
        assertTrue(legacy.enabled)
        assertEquals("", legacy.updateInterval)
        assertEquals("", legacy.ageSecretKey)
        assertFalse(legacy.updateViaProxy)
        assertTrue(legacy.autoOverrideRules)
        assertTrue(legacy.notifyOnExpiry)
        assertNull(legacy.customExpiryReminders)
        assertEquals("Still here", legacy.metadata.description)

        val edited = DesktopSubscriptionLibraries.updateProvider(
            library = DesktopSubscriptionLibrary(listOf(legacy)),
            subscriptionId = legacy.id,
            edit = legacy.toProviderEdit().copy(enabled = false, updateInterval = "0.25"),
        )
        DesktopSubscriptionLibraries.save(path, edited).getOrThrow()
        val restored = DesktopSubscriptionLibraries.load(path).getOrThrow().subscriptions.single()
        assertFalse(restored.enabled)
        assertEquals("0.25", restored.updateInterval)
        assertEquals("", restored.ageSecretKey)
        assertFalse(restored.updateViaProxy)
        assertTrue(restored.autoOverrideRules)
        assertTrue(restored.notifyOnExpiry)
        assertNull(restored.customExpiryReminders)
        assertEquals("Still here", restored.metadata.description)
    }

    @Test fun persists_android_provider_options_through_edit_and_refresh() {
        val original = DesktopStoredSubscription(
            id = 10,
            url = "https://example.com/sub",
            name = "Provider",
            metadata = DesktopStoredSubscriptionMetadata(description = "Fetched metadata"),
            ageSecretKey = "AGE-SECRET-KEY-1EXAMPLE",
            updateViaProxy = true,
            autoOverrideRules = false,
            notifyOnExpiry = false,
            customExpiryReminders = listOf(
                DesktopSubscriptionExpiryReminder(3, DesktopSubscriptionExpiryReminderUnit.Days),
                DesktopSubscriptionExpiryReminder(0, DesktopSubscriptionExpiryReminderUnit.AtExpiration),
            ),
        )
        val edited = DesktopSubscriptionLibraries.updateProvider(
            library = DesktopSubscriptionLibrary(listOf(original)),
            subscriptionId = original.id,
            edit = original.toProviderEdit().copy(
                ageSecretKey = "  AGE-SECRET-KEY-2  ",
                updateViaProxy = false,
                autoOverrideRules = true,
                notifyOnExpiry = true,
                customExpiryReminders = listOf(
                    DesktopSubscriptionExpiryReminder(12, DesktopSubscriptionExpiryReminderUnit.Hours),
                ),
            ),
        ).subscriptions.single()

        assertEquals(original.id, edited.id)
        assertEquals(original.metadata, edited.metadata)
        assertEquals("AGE-SECRET-KEY-2", edited.ageSecretKey)
        assertFalse(edited.updateViaProxy)
        assertTrue(edited.autoOverrideRules)
        assertTrue(edited.notifyOnExpiry)
        assertEquals(
            listOf(DesktopSubscriptionExpiryReminder(12, DesktopSubscriptionExpiryReminderUnit.Hours)),
            edited.customExpiryReminders,
        )

        val refreshed = DesktopSubscriptionLibraries.addOrReplace(
            library = DesktopSubscriptionLibrary(listOf(edited)),
            url = edited.url,
            userAgent = "Refresh agent",
        ).subscriptions.single()
        assertEquals(edited.ageSecretKey, refreshed.ageSecretKey)
        assertEquals(edited.updateViaProxy, refreshed.updateViaProxy)
        assertEquals(edited.autoOverrideRules, refreshed.autoOverrideRules)
        assertEquals(edited.notifyOnExpiry, refreshed.notifyOnExpiry)
        assertEquals(edited.customExpiryReminders, refreshed.customExpiryReminders)

        val path = Files.createTempDirectory("skipi-subscriptions").resolve("subscriptions.json")
        DesktopSubscriptionLibraries.save(path, DesktopSubscriptionLibrary(listOf(refreshed))).getOrThrow()
        assertEquals(refreshed, DesktopSubscriptionLibraries.load(path).getOrThrow().subscriptions.single())
    }

    @Test fun rejects_invalid_advanced_provider_options_without_mutating_library() {
        val subscription = DesktopStoredSubscription(id = 9, url = "https://example.com/sub")
        val library = DesktopSubscriptionLibrary(listOf(subscription))

        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                subscription.id,
                subscription.toProviderEdit().copy(ageSecretKey = "bad\nkey"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                subscription.id,
                subscription.toProviderEdit().copy(
                    customExpiryReminders = listOf(
                        DesktopSubscriptionExpiryReminder(0, DesktopSubscriptionExpiryReminderUnit.Days),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionLibraries.updateProvider(
                library,
                subscription.id,
                subscription.toProviderEdit().copy(
                    customExpiryReminders = listOf(
                        DesktopSubscriptionExpiryReminder(1, DesktopSubscriptionExpiryReminderUnit.AtExpiration),
                    ),
                ),
            )
        }
        assertEquals(subscription, library.subscriptions.single())
    }
}
