// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.LocalProxyPreferences
import app.skipi.app.model.SubscriptionPreferences
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionExpiryReminder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSettingsRepositoryParityTest {
    @Test
    fun homeSnapshotAndUpdateKeepRawAndroidValues() = runBlocking {
        val fixture = Fixture(
            settings = DesktopAppSettings(
                proxyServerListSort = 4,
                proxyServerListColumns = 2,
                enableAllProxyGroup = true,
                showTunnelMemory = false,
            ),
        )

        assertEquals(4, fixture.repository.state.value.home.proxyServerListSort)
        assertEquals(2, fixture.repository.state.value.home.proxyServerListLayout)
        assertTrue(fixture.repository.state.value.home.enableAllProxyGroup)
        assertFalse(fixture.repository.state.value.home.showTunnelMemoryOnHome)

        fixture.repository.update { current ->
            current.copy(
                home = current.home.copy(
                    proxyServerListSort = 2,
                    proxyServerListLayout = 3,
                    enableAllProxyGroup = false,
                    showTunnelMemoryOnHome = true,
                ),
            )
        }

        assertEquals(2, fixture.settings.proxyServerListSort)
        assertEquals(3, fixture.settings.proxyServerListColumns)
        assertFalse(fixture.settings.enableAllProxyGroup)
        assertTrue(fixture.settings.showTunnelMemory)
    }

    @Test
    fun nullHomeValuesPreserveDesktopSettingsWhenUpdatingAnotherPreference() = runBlocking {
        val fixture = Fixture(
            settings = DesktopAppSettings(
                proxyServerListSort = 4,
                proxyServerListColumns = 2,
                enableAllProxyGroup = true,
                showTunnelMemory = false,
            ),
        )

        fixture.repository.update { current ->
            current.copy(
                home = current.home.copy(
                    proxyServerListSort = null,
                    proxyServerListLayout = null,
                    enableAllProxyGroup = null,
                    showTunnelMemoryOnHome = null,
                ),
                appearance = current.appearance.copy(customColorsEnabled = true),
            )
        }

        assertEquals(4, fixture.settings.proxyServerListSort)
        assertEquals(2, fixture.settings.proxyServerListColumns)
        assertTrue(fixture.settings.enableAllProxyGroup)
        assertFalse(fixture.settings.showTunnelMemory)
        assertTrue(fixture.settings.customColorsEnabled)
    }

    @Test
    fun applicationPreferencesPersistWithoutReplacingDesktopOnlyValues() = runBlocking {
        val fixture = Fixture(
            settings = DesktopAppSettings(
                localProxyPort = 10808,
                subscriptionFetchTimeoutSeconds = 45,
                subscriptionUserAgent = "SKIPI/1.2/Desktop",
            ),
        )

        fixture.repository.update { current ->
            current.copy(
                application = ApplicationPreferences(
                    languageTag = "fa",
                    hapticsEnabled = false,
                    onboardingCompleted = true,
                ),
            )
        }

        assertEquals("fa", fixture.settings.languageTag)
        assertFalse(fixture.settings.hapticsEnabled)
        assertTrue(fixture.settings.onboardingCompleted)
        assertEquals("fa", fixture.repository.state.value.application.languageTag)
        assertEquals(10808, fixture.settings.localProxyPort)
        assertEquals(45, fixture.settings.subscriptionFetchTimeoutSeconds)
        assertEquals("SKIPI/1.2/Desktop", fixture.settings.subscriptionUserAgent)
    }

    @Test
    fun localProxyAndSubscriptionPreferencesPersistIntoDesktopRuntimeSettings() = runBlocking {
        val fixture = Fixture(
            settings = DesktopAppSettings(
                localProxyPort = 10808,
                localProxyListenAddress = "127.0.0.1",
                subscriptionFetchTimeoutSeconds = 30,
                subscriptionUserAgent = "SKIPI Desktop",
                sendDeviceHeaders = true,
            ),
        )
        val proxy = LocalProxyPreferences(
            port = "10880",
            dynamicPort = true,
            listenAllInterfaces = true,
            authenticationEnabled = true,
            username = "desktop-user",
            password = "desktop-secret",
        )
        val reminders = listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Days))
        val subscriptions = SubscriptionPreferences(
            pingMode = 1,
            pingUrl = "https://probe.example/check",
            pingTimeoutMillis = "9000",
            pingConcurrency = 4,
            userAgents = listOf("SKIPI/1.2/Desktop", "Custom agent"),
            deviceHeadersEnabled = false,
            fetchTimeoutSeconds = 45,
            expiryNotificationsEnabled = true,
            expiryReminders = reminders,
        )

        fixture.repository.update { current ->
            current.copy(localProxy = proxy, subscriptions = subscriptions)
        }

        assertEquals(10880, fixture.settings.localProxyPort)
        assertEquals("0.0.0.0", fixture.settings.localProxyListenAddress)
        assertTrue(fixture.settings.enableDynamicLocalProxyPort)
        assertTrue(fixture.settings.enableLocalProxyAuth)
        assertEquals("desktop-user", fixture.settings.localProxyUsername)
        assertEquals("desktop-secret", fixture.settings.localProxyPassword)
        assertEquals(45, fixture.settings.subscriptionFetchTimeoutSeconds)
        assertFalse(fixture.settings.sendDeviceHeaders)
        assertEquals("https://probe.example/check", fixture.settings.subscriptionPingUrl)
        assertEquals("9000", fixture.settings.subscriptionPingTimeoutMillis)
        assertEquals(4, fixture.settings.subscriptionPingConcurrency)
        assertEquals(listOf("SKIPI/1.2/Desktop", "Custom agent"), fixture.settings.subscriptionUserAgents)
        assertTrue(fixture.settings.enableSubscriptionExpiryNotifications)
        assertEquals(reminders, fixture.settings.subscriptionExpiryReminders)
        assertEquals(proxy, fixture.repository.state.value.localProxy)
        assertEquals(subscriptions, fixture.repository.state.value.subscriptions)
    }

    @Test
    fun nullRuntimeSectionsPreserveDesktopValuesWhenAnUnrelatedPreferenceChanges() = runBlocking {
        val fixture = Fixture(
            settings = DesktopAppSettings(
                localProxyPort = 10880,
                localProxyListenAddress = "0.0.0.0",
                enableDynamicLocalProxyPort = true,
                enableLocalProxyAuth = true,
                localProxyUsername = "desktop-user",
                localProxyPassword = "desktop-secret",
                subscriptionFetchTimeoutSeconds = 45,
                sendDeviceHeaders = false,
                subscriptionPingUrl = "https://probe.example/check",
                subscriptionPingTimeoutMillis = "9000",
                subscriptionExpiryReminders = listOf(SubscriptionExpiryReminder(2, ExpiryReminderUnit.Days)),
            ),
        )

        fixture.repository.update { current ->
            current.copy(
                localProxy = null,
                subscriptions = null,
                appearance = current.appearance.copy(customColorsEnabled = true),
            )
        }

        assertEquals(10880, fixture.settings.localProxyPort)
        assertEquals("0.0.0.0", fixture.settings.localProxyListenAddress)
        assertTrue(fixture.settings.enableDynamicLocalProxyPort)
        assertTrue(fixture.settings.enableLocalProxyAuth)
        assertEquals("desktop-user", fixture.settings.localProxyUsername)
        assertEquals("desktop-secret", fixture.settings.localProxyPassword)
        assertEquals(45, fixture.settings.subscriptionFetchTimeoutSeconds)
        assertFalse(fixture.settings.sendDeviceHeaders)
        assertEquals("https://probe.example/check", fixture.settings.subscriptionPingUrl)
        assertEquals("9000", fixture.settings.subscriptionPingTimeoutMillis)
        assertEquals(2, fixture.settings.subscriptionExpiryReminders.single().value)
        assertTrue(fixture.settings.customColorsEnabled)
    }

    private class Fixture(settings: DesktopAppSettings = DesktopAppSettings()) {
        var settings: DesktopAppSettings = settings
        private var servers = DesktopServerLibrary()
        private var configs = DesktopConfigLibrary()

        val repository = DesktopSettingsRepository(
            readSettings = { settings },
            saveSettings = { settings = it; Result.success(Unit) },
            publishSettings = { settings = it },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )
    }
}
