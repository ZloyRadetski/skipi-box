// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionExpiryReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistedRuntimePreferencesContractTest {
    @Test
    fun absentRuntimePreferenceSectionsStayAbsent() {
        val settings = PersistedSettings()

        assertNull(settings.localProxy)
        assertNull(settings.subscriptions)
    }

    @Test
    fun runtimePreferenceContractsPreserveAndroidRawStringsAndLists() {
        val localProxy = LocalProxyPreferences(
            port = "01080",
            dynamicPort = true,
            listenAllInterfaces = true,
            authenticationEnabled = false,
            username = " user ",
            password = "",
        )
        val reminders = listOf(
            SubscriptionExpiryReminder(2, ExpiryReminderUnit.Days),
            SubscriptionExpiryReminder(1, ExpiryReminderUnit.Hours),
        )
        val subscriptions = SubscriptionPreferences(
            pingMode = 2,
            pingUrl = "https://probe.example/check",
            pingTimeoutMillis = "005000",
            pingConcurrency = 8,
            userAgents = listOf("SKIPI/1.2/Android", "custom agent", ""),
            deviceHeadersEnabled = false,
            fetchTimeoutSeconds = 10,
            expiryNotificationsEnabled = true,
            expiryReminders = reminders,
        )

        val settings = PersistedSettings(localProxy = localProxy, subscriptions = subscriptions)

        assertEquals(localProxy, settings.localProxy)
        assertEquals(subscriptions, settings.subscriptions)
        assertEquals("01080", settings.localProxy?.port)
        assertEquals("005000", settings.subscriptions?.pingTimeoutMillis)
        assertEquals(listOf("SKIPI/1.2/Android", "custom agent", ""), settings.subscriptions?.userAgents)
        assertEquals(reminders, settings.subscriptions?.expiryReminders)
    }
}
