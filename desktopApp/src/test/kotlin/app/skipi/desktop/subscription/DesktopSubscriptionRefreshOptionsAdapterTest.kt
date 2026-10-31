// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.DefaultSubscriptionUserAgent

import app.skipi.app.model.SubscriptionRecord
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSubscriptionRefreshOptionsAdapterTest {
    @Test
    fun refreshOptionsUseAndroidProxyEligibilityAndTheRunningEndpointSnapshot() {
        val settings = DesktopAppSettings(
            localProxyPort = 10_808,
            localProxyListenAddress = "0.0.0.0",
            enableLocalProxyAuth = true,
            localProxyUsername = " proxy-user ",
            localProxyPassword = " proxy-secret ",
            subscriptionFetchTimeoutSeconds = 47,
            sendDeviceHeaders = true,
            installationUuid = "install-uuid",
        )

        val options = settings.toSubscriptionRefreshFetchOptions(
            subscription = subscription(updateViaProxy = true, ageSecretKey = " age-key "),
            proxyRunning = true,
            resolvedLocalProxyPort = 20_480,
        )

        assertEquals(Duration.ofSeconds(47), options.timeout)
        assertEquals(
            DesktopSubscriptionSocksProxy(
                host = "127.0.0.1",
                port = 20_480,
                username = "proxy-user",
                password = " proxy-secret ",
            ),
            options.proxy,
        )
        assertEquals(DesktopDeviceIdentity.deviceHeaders("install-uuid"), options.deviceHeaders)
        assertEquals(" age-key ", options.ageSecretKey)
        assertEquals(DefaultSubscriptionUserAgent, options.fallbackUserAgent)
    }

    @Test
    fun refreshOptionsSkipProxyUnlessSubscriptionRequestsItAndCoreIsRunning() {
        val settings = DesktopAppSettings(localProxyPort = 10_808)

        val notRequested = settings.toSubscriptionRefreshFetchOptions(
            subscription = subscription(updateViaProxy = false),
            proxyRunning = true,
            resolvedLocalProxyPort = 20_480,
        )
        val coreStopped = settings.toSubscriptionRefreshFetchOptions(
            subscription = subscription(updateViaProxy = true),
            proxyRunning = false,
            resolvedLocalProxyPort = 20_480,
        )

        assertNull(notRequested.proxy)
        assertNull(coreStopped.proxy)
    }

    @Test
    fun disabledAuthAndDeviceHeadersDoNotLeakCredentialsOrIdentity() {
        val settings = DesktopAppSettings(
            enableLocalProxyAuth = false,
            localProxyUsername = "stale-user",
            localProxyPassword = "stale-password",
            sendDeviceHeaders = false,
        )

        val options = settings.toSubscriptionRefreshFetchOptions(
            subscription = subscription(updateViaProxy = true),
            proxyRunning = true,
        )

        assertEquals(
            DesktopSubscriptionSocksProxy(
                host = "127.0.0.1",
                port = settings.localProxyPort,
            ),
            options.proxy,
        )
        assertEquals(emptyMap(), options.deviceHeaders)
        assertEquals(DefaultSubscriptionUserAgent, options.fallbackUserAgent)
    }

    private fun subscription(
        updateViaProxy: Boolean,
        ageSecretKey: String = "",
    ) = SubscriptionRecord(
        id = 4,
        title = "Subscription",
        url = "https://sub.example/list",
        userAgent = "",
        updateInterval = "1d",
        hwid = "install-uuid",
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = false,
        builtIn = false,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )
}
