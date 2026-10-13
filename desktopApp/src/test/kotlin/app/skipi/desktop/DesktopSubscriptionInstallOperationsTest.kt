// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSubscriptionInstallOperationsTest {
    @Test
    fun raw_http_install_uri_prepares_shared_record_without_replacing_desktop_blank_user_agent() {
        val install = checkNotNull(
            DesktopSubscriptionInstallUri.parseOrNull("http://example.test/sub#Portable%20list"),
        )
        val preparation = install.prepareIn(SubscriptionCatalog(nextSubscriptionId = 7))

        assertEquals("Portable list", preparation.subscription.title)
        assertEquals("http://example.test/sub#Portable%20list", preparation.subscription.url)
        assertEquals("", preparation.subscription.userAgent)
        assertEquals(7, preparation.subscription.id)
        assertNull(preparation.existingTitle)
    }

    @Test
    fun client_scheme_keeps_desktop_user_agent_and_exact_url_duplicate_semantics() {
        val install = checkNotNull(
            DesktopSubscriptionInstallUri.parseOrNull(
                "v2rayng://install-config?url=https%3A%2F%2Fexample.test%2Fsub&name=Incoming",
            ),
        )
        val existing = SubscriptionRecord(
            id = 4,
            title = "Saved title",
            url = install.url,
            userAgent = "Saved UA",
            updateInterval = "12",
            hwid = "saved-hwid",
            ageSecretKey = "saved-secret",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = false,
            builtIn = false,
            metadata = null,
            lastUpdatedAtMillis = 15L,
            notifyOnExpiry = false,
            customExpiryReminders = null,
        )
        val catalog = SubscriptionCatalog(listOf(existing), nextSubscriptionId = 8)

        val preparation = install.prepareIn(catalog)

        assertEquals(DefaultDesktopSubscriptionUserAgent, install.userAgent)
        assertEquals(catalog, preparation.catalog)
        assertEquals(existing, preparation.subscription)
        assertEquals("Saved title", preparation.existingTitle)
    }
}
