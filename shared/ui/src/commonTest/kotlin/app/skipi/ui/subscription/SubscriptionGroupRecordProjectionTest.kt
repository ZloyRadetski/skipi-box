// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

import app.skipi.app.model.SubscriptionRecord
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionEmbeddedConfig
import features.subscription.SubscriptionExpiryReminder
import features.subscription.SubscriptionMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionGroupRecordProjectionTest {
    @Test
    fun `default display name applies only to the Android built-in default group`() {
        val androidDefault = subscription(id = 1, title = "", builtIn = true)
        val desktopRecordWithIdOne = subscription(id = 1, title = "Desktop source", builtIn = false)
        val otherBuiltIn = subscription(id = 7, title = "Other built-in", builtIn = true)

        assertEquals("Default group", androidDefault.toSubscriptionGroupUiState("Default group").name)
        assertEquals("Desktop source", desktopRecordWithIdOne.toSubscriptionGroupUiState("Default group").name)
        assertEquals("Other built-in", otherBuiltIn.toSubscriptionGroupUiState("Default group").name)
    }

    @Test
    fun `record projection carries editor options metadata display and reminders`() {
        val source = subscription(
            id = 8,
            title = "Feed",
            builtIn = false,
            metadata = SubscriptionMetadata(
                profileTitle = "Provider title",
                profileDescription = "Description",
                announce = "Announcement",
                supportUrl = "https://support.example",
                supportEmail = "support@example.test",
                profileWebPageUrl = "https://provider.example",
                announceUrl = "https://provider.example/news",
                userInfoReceived = true,
                trafficUploadBytes = 101L,
                trafficDownloadBytes = 202L,
                trafficTotalBytes = 303L,
                trafficExpireAtSeconds = 404L,
                profileUpdateIntervalHours = "12",
                embeddedConfig = SubscriptionEmbeddedConfig(
                    payload = "https://provider.example/config",
                    activate = false,
                    isUrl = true,
                ),
            ),
        )

        val ui = source.toSubscriptionGroupUiState("Default group")

        assertEquals(8, ui.id)
        assertEquals("Feed", ui.name)
        assertEquals("https://provider.example/sub", ui.url)
        assertEquals("SKIPI/1/Desktop", ui.userAgent)
        assertEquals("24", ui.updateInterval)
        assertEquals("persisted-hwid", ui.hwid)
        assertEquals("age-key", ui.ageSecretKey)
        assertEquals(true, ui.updateViaProxy)
        assertEquals(false, ui.autoOverrideRules)
        assertEquals(false, ui.enabled)
        assertEquals(false, ui.builtIn)
        assertEquals(5678L, ui.lastUpdatedAtMillis)
        assertEquals("Provider title", ui.profileTitle)
        assertEquals("Announcement", ui.announce)
        assertEquals("https://support.example", ui.supportUrl)
        assertEquals("support@example.test", ui.supportEmail)
        assertEquals("https://provider.example", ui.profileWebPageUrl)
        assertEquals("https://provider.example/news", ui.announceUrl)
        assertEquals(101L, ui.trafficUploadBytes)
        assertEquals(202L, ui.trafficDownloadBytes)
        assertEquals(303L, ui.trafficTotalBytes)
        assertEquals(404L, ui.trafficExpireAtSeconds)
        assertEquals(false, ui.notifyOnExpiry)
        assertEquals(listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks)), ui.customExpiryReminders)
    }

    @Test
    fun `reverse projection edits group fields but retains existing fetched metadata and timestamp`() {
        val existing = subscription(
            id = 1,
            title = "Persisted built-in title",
            builtIn = true,
            metadata = SubscriptionMetadata(
                profileTitle = "Latest provider title",
                announce = "Latest announcement",
                profileDescription = "Keep this field even though the editor does not show it",
            ),
        )
        val editedUi = existing.toSubscriptionGroupUiState("Default group").copy(
            name = "Visible default name",
            url = "https://new.example/sub",
            userAgent = "custom agent",
            updateInterval = "48",
            hwid = "new-hwid",
            ageSecretKey = "new-age-key",
            updateViaProxy = false,
            autoOverrideRules = true,
            enabled = true,
            lastUpdatedAtMillis = 1L,
            profileTitle = "Stale dialog title",
            announce = "Stale dialog announcement",
            trafficUploadBytes = 1L,
        )

        val savedDraft = editedUi.toSubscriptionRecord(existing)

        assertEquals(existing.id, savedDraft.id)
        assertEquals(existing.title, savedDraft.title)
        assertEquals("https://new.example/sub", savedDraft.url)
        assertEquals("custom agent", savedDraft.userAgent)
        assertEquals("48", savedDraft.updateInterval)
        assertEquals("new-hwid", savedDraft.hwid)
        assertEquals("new-age-key", savedDraft.ageSecretKey)
        assertEquals(false, savedDraft.updateViaProxy)
        assertEquals(true, savedDraft.autoOverrideRules)
        assertEquals(true, savedDraft.enabled)
        assertEquals(true, savedDraft.builtIn)
        assertEquals(existing.metadata, savedDraft.metadata)
        assertEquals(existing.lastUpdatedAtMillis, savedDraft.lastUpdatedAtMillis)
        assertEquals(existing.notifyOnExpiry, savedDraft.notifyOnExpiry)
        assertEquals(existing.customExpiryReminders, savedDraft.customExpiryReminders)
    }

    @Test
    fun `new draft has no fetched metadata or update timestamp`() {
        val draft = SubscriptionGroupUiState(
            id = 99,
            name = "Manual entry",
            url = "",
            userAgent = "SKIPI Desktop",
            updateInterval = "",
            enabled = true,
            notifyOnExpiry = false,
            customExpiryReminders = emptyList(),
        ).toSubscriptionRecord()

        assertEquals(99, draft.id)
        assertEquals("Manual entry", draft.title)
        assertEquals(null, draft.metadata)
        assertEquals(null, draft.lastUpdatedAtMillis)
        assertEquals(false, draft.notifyOnExpiry)
        assertEquals(emptyList(), draft.customExpiryReminders)
    }

    private fun subscription(
        id: Int,
        title: String,
        builtIn: Boolean,
        metadata: SubscriptionMetadata? = null,
    ) = SubscriptionRecord(
        id = id,
        title = title,
        url = "https://provider.example/sub",
        userAgent = "SKIPI/1/Desktop",
        updateInterval = "24",
        hwid = "persisted-hwid",
        ageSecretKey = "age-key",
        updateViaProxy = true,
        autoOverrideRules = false,
        enabled = false,
        builtIn = builtIn,
        metadata = metadata,
        lastUpdatedAtMillis = 5678L,
        notifyOnExpiry = false,
        customExpiryReminders = listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks)),
    )
}
