// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.AppState
import app.skipi.app.model.LocalProxyPreferences
import app.skipi.app.model.SubscriptionPreferences
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionExpiryReminder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AndroidRuntimePreferencesContractTest {
    @Test
    fun androidDefaultsAreExposedWithoutNormalization() {
        val original = AppState()
        val persisted = original.toPersistedSettings()

        assertEquals("10808", persisted.localProxy?.port)
        assertFalse(persisted.localProxy?.dynamicPort ?: true)
        assertFalse(persisted.localProxy?.listenAllInterfaces ?: true)
        assertTrue(persisted.localProxy?.authenticationEnabled ?: false)
        assertEquals(original.localProxyUsername, persisted.localProxy?.username)
        assertEquals(original.localProxyPassword, persisted.localProxy?.password)
        assertEquals(1, persisted.subscriptions?.pingMode)
        assertEquals(original.subscriptionPingUrl, persisted.subscriptions?.pingUrl)
        assertEquals("5000", persisted.subscriptions?.pingTimeoutMillis)
        assertEquals(8, persisted.subscriptions?.pingConcurrency)
        assertEquals(original.subscriptionUserAgents, persisted.subscriptions?.userAgents)
        assertTrue(persisted.subscriptions?.deviceHeadersEnabled ?: false)
        assertEquals(10, persisted.subscriptions?.fetchTimeoutSeconds)
        assertTrue(persisted.subscriptions?.expiryNotificationsEnabled ?: false)
        assertEquals(original.subscriptionExpiryReminders, persisted.subscriptions?.expiryReminders)
    }

    @Test
    fun runtimeSettingsRoundTripAllStoredAndroidValues() {
        val original = AppState(
            localProxyPort = "01080",
            enableDynamicLocalProxyPort = true,
            localProxyListenAllInterfaces = true,
            enableLocalProxyAuth = false,
            localProxyUsername = " user ",
            localProxyPassword = "secret",
            subscriptionPingMode = 2,
            subscriptionPingUrl = "https://probe.example/check",
            subscriptionPingTimeoutMillis = "005000",
            subscriptionPingConcurrency = 3,
            subscriptionUserAgents = listOf("", "custom agent"),
            enableSubscriptionDeviceHeaders = false,
            subscriptionFetchTimeoutSeconds = 45,
            enableSubscriptionExpiryNotifications = false,
            subscriptionExpiryReminders = listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Days)),
        )

        val persisted = original.toPersistedSettings()
        val restored = AppState().applyPersistedSettings(persisted)

        assertEquals(
            LocalProxyPreferences(
                port = "01080",
                dynamicPort = true,
                listenAllInterfaces = true,
                authenticationEnabled = false,
                username = " user ",
                password = "secret",
            ),
            persisted.localProxy,
        )
        assertEquals(
            SubscriptionPreferences(
                pingMode = 2,
                pingUrl = "https://probe.example/check",
                pingTimeoutMillis = "005000",
                pingConcurrency = 3,
                userAgents = listOf("", "custom agent"),
                deviceHeadersEnabled = false,
                fetchTimeoutSeconds = 45,
                expiryNotificationsEnabled = false,
                expiryReminders = listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Days)),
            ),
            persisted.subscriptions,
        )
        assertEquals(original.localProxyPort, restored.localProxyPort)
        assertEquals(original.enableDynamicLocalProxyPort, restored.enableDynamicLocalProxyPort)
        assertEquals(original.localProxyListenAllInterfaces, restored.localProxyListenAllInterfaces)
        assertEquals(original.enableLocalProxyAuth, restored.enableLocalProxyAuth)
        assertEquals(original.localProxyUsername, restored.localProxyUsername)
        assertEquals(original.localProxyPassword, restored.localProxyPassword)
        assertEquals(original.subscriptionPingMode, restored.subscriptionPingMode)
        assertEquals(original.subscriptionPingUrl, restored.subscriptionPingUrl)
        assertEquals(original.subscriptionPingTimeoutMillis, restored.subscriptionPingTimeoutMillis)
        assertEquals(original.subscriptionPingConcurrency, restored.subscriptionPingConcurrency)
        assertEquals(original.subscriptionUserAgents, restored.subscriptionUserAgents)
        assertEquals(original.enableSubscriptionDeviceHeaders, restored.enableSubscriptionDeviceHeaders)
        assertEquals(original.subscriptionFetchTimeoutSeconds, restored.subscriptionFetchTimeoutSeconds)
        assertEquals(original.enableSubscriptionExpiryNotifications, restored.enableSubscriptionExpiryNotifications)
        assertEquals(original.subscriptionExpiryReminders, restored.subscriptionExpiryReminders)
    }

    @Test
    fun absentRuntimeSectionsLeaveAndroidValuesUnchanged() {
        val original = AppState(
            localProxyPort = "11080",
            enableDynamicLocalProxyPort = true,
            localProxyListenAllInterfaces = true,
            enableLocalProxyAuth = false,
            subscriptionPingTimeoutMillis = "9000",
            subscriptionFetchTimeoutSeconds = 90,
        )
        val persisted = original.toPersistedSettings().copy(localProxy = null, subscriptions = null)

        val restored = original.applyPersistedSettings(persisted)

        assertEquals(original.localProxyPort, restored.localProxyPort)
        assertEquals(original.enableDynamicLocalProxyPort, restored.enableDynamicLocalProxyPort)
        assertEquals(original.localProxyListenAllInterfaces, restored.localProxyListenAllInterfaces)
        assertEquals(original.enableLocalProxyAuth, restored.enableLocalProxyAuth)
        assertEquals(original.subscriptionPingTimeoutMillis, restored.subscriptionPingTimeoutMillis)
        assertEquals(original.subscriptionFetchTimeoutSeconds, restored.subscriptionFetchTimeoutSeconds)
    }
}
