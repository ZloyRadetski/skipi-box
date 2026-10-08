// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.SubscriptionGroupState
import app.skipi.ui.subscription.SubscriptionGroupUiState
import kotlin.test.assertEquals
import org.junit.Test

class SubscriptionGroupUiStateAdapterTest {
    @Test
    fun `Android state projects its display name and all stored editor fields`() {
        val group = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Persisted default name",
            url = "https://provider.example/sub",
            userAgent = DefaultSubscriptionUserAgent,
            updateInterval = "24",
            hwid = "persisted-hwid",
            ageSecretKey = "age-key",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = false,
            builtIn = true,
            lastUpdatedAtMillis = 987L,
            profileTitle = "Provider title",
            announce = "Announcement",
            supportUrl = "https://support.example",
            supportEmail = "support@example.test",
            profileWebPageUrl = "https://provider.example",
            announceUrl = "https://provider.example/news",
            trafficUploadBytes = 101L,
            trafficDownloadBytes = 202L,
            trafficTotalBytes = 303L,
            trafficExpireAtSeconds = 404L,
            notifyOnExpiry = false,
            customExpiryReminders = listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks)),
        )

        val ui = group.toSubscriptionGroupUiState("Manual servers")

        assertEquals("Manual servers", ui.name)
        assertEquals(group.id, ui.id)
        assertEquals(group.url, ui.url)
        assertEquals(group.userAgent, ui.userAgent)
        assertEquals(group.updateInterval, ui.updateInterval)
        assertEquals(group.hwid, ui.hwid)
        assertEquals(group.ageSecretKey, ui.ageSecretKey)
        assertEquals(group.updateViaProxy, ui.updateViaProxy)
        assertEquals(group.autoOverrideRules, ui.autoOverrideRules)
        assertEquals(group.enabled, ui.enabled)
        assertEquals(group.builtIn, ui.builtIn)
        assertEquals(group.lastUpdatedAtMillis, ui.lastUpdatedAtMillis)
        assertEquals(group.profileTitle, ui.profileTitle)
        assertEquals(group.announce, ui.announce)
        assertEquals(group.supportUrl, ui.supportUrl)
        assertEquals(group.supportEmail, ui.supportEmail)
        assertEquals(group.profileWebPageUrl, ui.profileWebPageUrl)
        assertEquals(group.announceUrl, ui.announceUrl)
        assertEquals(group.trafficUploadBytes, ui.trafficUploadBytes)
        assertEquals(group.trafficDownloadBytes, ui.trafficDownloadBytes)
        assertEquals(group.trafficTotalBytes, ui.trafficTotalBytes)
        assertEquals(group.trafficExpireAtSeconds, ui.trafficExpireAtSeconds)
        assertEquals(group.notifyOnExpiry, ui.notifyOnExpiry)
        assertEquals(group.customExpiryReminders, ui.customExpiryReminders)
    }

    @Test
    fun `Android reverse adapter retains fetched values from original state`() {
        val original = SubscriptionGroupState(
            id = 5,
            name = "Provider group",
            url = "https://old.example/sub",
            userAgent = "custom agent",
            updateInterval = "12",
            enabled = false,
            lastUpdatedAtMillis = 777L,
            profileTitle = "Latest profile title",
            announce = "Latest announcement",
            trafficUploadBytes = 10L,
            notifyOnExpiry = true,
        )
        val staleUi = original.toSubscriptionGroupUiState("Manual servers").copy(
            name = "Edited group",
            url = "https://new.example/sub",
            userAgent = "edited agent",
            profileTitle = "Stale profile title",
            announce = "Stale announcement",
            trafficUploadBytes = 1L,
            lastUpdatedAtMillis = 1L,
        )

        val saved = staleUi.toSubscriptionGroupState(original)

        assertEquals("Edited group", saved.name)
        assertEquals("https://new.example/sub", saved.url)
        assertEquals("edited agent", saved.userAgent)
        assertEquals(777L, saved.lastUpdatedAtMillis)
        assertEquals("Latest profile title", saved.profileTitle)
        assertEquals("Latest announcement", saved.announce)
        assertEquals(10L, saved.trafficUploadBytes)
    }
}
