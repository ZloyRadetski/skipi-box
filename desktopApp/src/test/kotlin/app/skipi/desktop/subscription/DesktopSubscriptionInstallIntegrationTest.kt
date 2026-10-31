// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionInstallOperations
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopSubscriptionInstallIntegrationTest {
    @Test
    fun shared_install_coordinator_returns_saved_duplicate_title_after_persisting_before_refresh() = runBlocking {
        val path = Files.createTempFile("skipi-install-flow", ".json")
        val install = checkNotNull(DesktopSubscriptionInstallUri.parseOrNull("http://example.test/sub#Incoming"))
        val initial = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                DesktopStoredSubscription(
                    id = 17,
                    url = install.url,
                    userAgent = "Stored UA",
                    name = "Saved title",
                    enabled = false,
                    updateInterval = "18",
                    hwid = "saved-device",
                    ageSecretKey = "saved-age-key",
                    updateViaProxy = true,
                    autoOverrideRules = false,
                    notifyOnExpiry = false,
                ),
            ),
            nextSubscriptionId = 30,
        )
        var library = initial

        try {
            DesktopSubscriptionLibraries.save(path, initial).getOrThrow()
            val repository = DesktopSubscriptionRepository(
                initialLibrary = initial,
                readLibrary = { library },
                saveLibrary = { DesktopSubscriptionLibraries.save(path, it) },
                publishLibrary = { library = it },
            )
            var refreshCalls = 0
            var recordSeenByRefresh: app.skipi.app.model.SubscriptionRecord? = null

            val result = SubscriptionInstallOperations.installAndRefresh(
                repository = repository,
                title = install.name,
                url = install.url,
                userAgent = install.userAgent,
            ) { subscription ->
                refreshCalls += 1
                recordSeenByRefresh = subscription
                assertEquals(subscription, repository.catalog.value.subscriptions.single())
                assertEquals(initial, DesktopSubscriptionLibraries.load(path).getOrThrow())
                "updated 1 group with 2 servers"
            }

            assertEquals(1, refreshCalls)
            assertFalse(recordSeenByRefresh!!.enabled)
            assertEquals("Stored UA", recordSeenByRefresh!!.userAgent)
            assertEquals("saved-age-key", recordSeenByRefresh!!.ageSecretKey)
            assertEquals(true, recordSeenByRefresh!!.updateViaProxy)
            assertEquals("Saved title", result.preparation.existingTitle)
            assertEquals("Saved title", result.preparation.subscription.title)
            assertEquals("updated 1 group with 2 servers", result.refreshResult)
            assertEquals(initial, library)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
        }
    }
}
